"""Vertex AI calls: classification, extraction, translation.

The prompts are carried over almost verbatim from the FastAPI build, because
they were tuned against real documents and their strictness is the product's
safety story. What changed is how the output is constrained: the old code
asked for JSON in prose and then scraped it back out with a regex, tolerating
whatever came back. Here the schema is passed to the model as `response_schema`
so the response is JSON by construction, and a parse failure is a real error
rather than something silently sanitised into an empty brief.

Authentication is Application Default Credentials -- the function's own
runtime service account, granted roles/aiplatform.user. There is no API key
and no service-account JSON anywhere in this repo or in Secret Manager,
because the functions and Vertex live in the same project.
"""

from __future__ import annotations

import json
import logging
from typing import Any

from google import genai
from google.genai import types

from anteroom.config import (
    CLASSIFIER_MODEL,
    EXTRACTION_MODEL,
    PROJECT_ID,
    TRANSLATION_MODEL,
    VERTEX_LOCATION,
)

logger = logging.getLogger("anteroom.vertex")

_client: genai.Client | None = None


def client() -> genai.Client:
    """Lazily built and reused across invocations on a warm instance."""
    global _client
    if _client is None:
        _client = genai.Client(
            vertexai=True, project=PROJECT_ID, location=VERTEX_LOCATION
        )
    return _client


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------
# These mirror `BriefContent` in core/.../model/Brief.kt exactly. A field here
# that the Kotlin model lacks would be dropped on the next client write; a
# field the Kotlin model requires and this omits would fail to decode and make
# the brief disappear. Keep the two in step.
_NULLABLE_STRING = {"type": "STRING", "nullable": True}

BRIEF_CONTENT_SCHEMA: dict[str, Any] = {
    "type": "OBJECT",
    "properties": {
        "patient": {
            "type": "OBJECT",
            "properties": {
                "name": _NULLABLE_STRING,
                "dob": _NULLABLE_STRING,
                "sex": _NULLABLE_STRING,
                "id_number": _NULLABLE_STRING,
            },
        },
        "referral_reason": _NULLABLE_STRING,
        "medications": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "name": {"type": "STRING"},
                    "dose": _NULLABLE_STRING,
                    "frequency": _NULLABLE_STRING,
                },
                "required": ["name"],
            },
        },
        "allergies": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "substance": {"type": "STRING"},
                    "reaction": _NULLABLE_STRING,
                },
                "required": ["substance"],
            },
        },
        "flagged_items": {"type": "ARRAY", "items": {"type": "STRING"}},
    },
    "required": [
        "patient",
        "referral_reason",
        "medications",
        "allergies",
        "flagged_items",
    ],
}

CLASSIFICATION_SCHEMA: dict[str, Any] = {
    "type": "OBJECT",
    "properties": {
        "doc_type": {
            "type": "STRING",
            "enum": ["referral", "med_list", "lab_result", "other"],
        },
        "confidence": {"type": "STRING", "enum": ["low", "medium", "high"]},
    },
    "required": ["doc_type", "confidence"],
}


# ---------------------------------------------------------------------------
# Prompts (carried over from backend/briefgen.py, docclassifier.py,
# brieftranslator.py on the `main` branch)
# ---------------------------------------------------------------------------
EXTRACTION_SYSTEM_PROMPT = (
    "You are a strict medical-document data extractor for a pre-visit brief. "
    "You will receive one or more photos of medical documents (referral letters, "
    "medication lists, lab results, etc.) belonging to ONE patient.\n\n"
    "STRICT RULES - DO NOT VIOLATE:\n"
    "1. Never diagnose, interpret, explain, translate, or infer anything. Extract only what is literally printed on the pages.\n"
    "2. Never invent, guess or complete a missing value. If a field is not clearly present, use null (or [] for lists).\n"
    "3. Copy patient names, medication names, dosages, frequencies, dates and numbers VERBATIM from the images. Do not normalise units or translate.\n"
    "4. If any part of a high-risk field (dosage, frequency, allergy) is UNREADABLE, cut off, blurry or ambiguous - DO NOT guess. Add a short human-readable note to `flagged_items` instead (e.g. 'Warfarin dose unreadable on page 2'). Then omit that field from `medications`/`allergies`.\n"
    "5. Only include a medication once - if the same drug appears multiple times, keep the clearest entry.\n"
    "6. `referral_reason` is a single sentence taken verbatim from a referral letter's stated reason. If the pages do not contain a referral letter, set it to null.\n"
    "7. `flagged_items` is for anything a clinician must check against the original page. Use it whenever you are less than certain about a dosage or an allergy."
)

CLASSIFIER_SYSTEM_PROMPT = (
    "You are a medical-document triage classifier. Given a photograph of a "
    "single medical document page, classify it into ONE of these categories:\n"
    "- referral: a doctor's referral letter (from GP to specialist, hospital admission letter, etc.).\n"
    "- med_list: a list or table of prescribed medications (drug name, dose, frequency).\n"
    "- lab_result: laboratory or diagnostic report (blood work, urinalysis, imaging report, cardiogram, etc.).\n"
    "- other: anything else, a business card, a photo that isn't a medical document, an unclear page, or when you cannot tell.\n\n"
    "Also rate your confidence: low, medium, or high. If confidence is low, prefer 'other'."
)

LANGUAGE_NAMES = {"en": "English", "es": "Spanish (Castilian, es-ES)"}


def _translation_system_prompt(target: str) -> str:
    name = LANGUAGE_NAMES.get(target, target)
    return (
        f"You translate a medical pre-visit brief JSON into {name} for a "
        "clinician reading it in that language.\n\n"
        "STRICT RULES - DO NOT VIOLATE:\n"
        "1. Never diagnose, interpret, or infer. Never add fields not in the input.\n"
        "2. NEVER translate a person's name - keep patient.name character-for-character.\n"
        "3. NEVER translate medication names, brand names, IDs, dates, numeric doses,\n"
        "   or units like mg/ml/ug/IU. Copy them verbatim.\n"
        "4. DO translate: referral_reason, flagged_items text, allergies[].reaction\n"
        "   text, and clearly-worded values in patient.sex (e.g. Male<->Hombre).\n"
        "5. Preserve every field the input has - same keys, same array order, same\n"
        "   `null` for empty fields. Do NOT add new keys."
    )


# ---------------------------------------------------------------------------
# Calls
# ---------------------------------------------------------------------------
def _parse(response) -> dict:
    text = (getattr(response, "text", None) or "").strip()
    if not text:
        raise ValueError("model returned no text")
    return json.loads(text)


def classify_page(image: bytes, mime_type: str = "image/jpeg") -> dict:
    """Classify a single page. Returns {'doc_type', 'confidence'}."""
    response = client().models.generate_content(
        model=CLASSIFIER_MODEL,
        contents=[
            types.Part.from_bytes(data=image, mime_type=mime_type),
            "Classify the attached medical document page.",
        ],
        config=types.GenerateContentConfig(
            system_instruction=CLASSIFIER_SYSTEM_PROMPT,
            temperature=0,
            response_mime_type="application/json",
            response_schema=CLASSIFICATION_SCHEMA,
            # Triage into four buckets does not benefit from reasoning tokens,
            # and they are the dominant cost on Flash.
            thinking_config=types.ThinkingConfig(thinking_budget=0),
        ),
    )
    parsed = _parse(response)
    doc_type = str(parsed.get("doc_type", "other")).lower().strip()
    confidence = str(parsed.get("confidence", "low")).lower().strip()
    if doc_type not in {"referral", "med_list", "lab_result", "other"}:
        doc_type = "other"
    if confidence not in {"low", "medium", "high"}:
        confidence = "low"
    return {"doc_type": doc_type, "confidence": confidence}


def extract_brief(pages: list[tuple[bytes, str]]) -> dict:
    """Extract structured content from every page of one brief."""
    if not pages:
        raise ValueError("no pages to extract")

    parts: list[Any] = [
        types.Part.from_bytes(data=data, mime_type=mime) for data, mime in pages
    ]
    parts.append(
        "Extract the pre-visit brief data from the attached "
        f"{len(pages)} page(s). They all belong to ONE patient."
    )

    response = client().models.generate_content(
        model=EXTRACTION_MODEL,
        contents=parts,
        config=types.GenerateContentConfig(
            system_instruction=EXTRACTION_SYSTEM_PROMPT,
            # Zero temperature because the requirement is transcription, not
            # fluency: the same page must give the same dosages every time.
            temperature=0,
            response_mime_type="application/json",
            response_schema=BRIEF_CONTENT_SCHEMA,
        ),
    )
    return sanitize_content(_parse(response))


def translate_content(content: dict, target: str) -> dict:
    """Translate the free-text fields of an extracted brief."""
    response = client().models.generate_content(
        model=TRANSLATION_MODEL,
        contents=[
            "Translate the fields described in the system prompt. Return one "
            "JSON object with the same shape as the input.\n\nINPUT JSON:\n"
            + json.dumps(content, ensure_ascii=False)
        ],
        config=types.GenerateContentConfig(
            system_instruction=_translation_system_prompt(target),
            temperature=0,
            response_mime_type="application/json",
            response_schema=BRIEF_CONTENT_SCHEMA,
        ),
    )
    return merge_translation(content, _parse(response))


# ---------------------------------------------------------------------------
# Shaping
# ---------------------------------------------------------------------------
def _clean(value: Any) -> str | None:
    return value.strip() if isinstance(value, str) and value.strip() else None


def sanitize_content(raw: Any) -> dict:
    """Coerce a model response into exactly the Kotlin `BriefContent` shape.

    `response_schema` already constrains the structure, so this is a second
    line rather than the first: it drops empty strings that would render as
    blank rows, and guarantees the keys the Kotlin decoder needs are present.
    """
    out: dict[str, Any] = {
        "patient": {},
        "referral_reason": None,
        "medications": [],
        "allergies": [],
        "flagged_items": [],
    }
    if not isinstance(raw, dict):
        return out

    patient = raw.get("patient")
    if isinstance(patient, dict):
        for key in ("name", "dob", "sex", "id_number"):
            value = _clean(patient.get(key))
            if value:
                out["patient"][key] = value

    out["referral_reason"] = _clean(raw.get("referral_reason"))

    for med in raw.get("medications") or []:
        if not isinstance(med, dict):
            continue
        name = _clean(med.get("name"))
        if not name:
            continue
        item: dict[str, Any] = {"name": name}
        for key in ("dose", "frequency"):
            value = _clean(med.get(key))
            if value:
                item[key] = value
        out["medications"].append(item)

    for allergy in raw.get("allergies") or []:
        if not isinstance(allergy, dict):
            continue
        substance = _clean(allergy.get("substance"))
        if not substance:
            continue
        item = {"substance": substance}
        reaction = _clean(allergy.get("reaction"))
        if reaction:
            item["reaction"] = reaction
        out["allergies"].append(item)

    for flag in raw.get("flagged_items") or []:
        value = _clean(flag)
        if value:
            out["flagged_items"].append(value)

    return out


def merge_translation(source: dict, translated: Any) -> dict:
    """Take translated prose from the model, everything clinical from source.

    This is not defensive tidying, it is the safety boundary. A translation
    pass must not be able to alter a drug name, a dose, a patient's name or an
    ID -- so those are copied from the already-extracted content and the model's
    version of them is discarded, whatever it said.
    """
    out = sanitize_content(source)
    if not isinstance(translated, dict):
        return out

    t_patient = translated.get("patient")
    if isinstance(t_patient, dict) and "sex" in out["patient"]:
        # Only `sex` is a word; name, dob and id_number stay as extracted.
        out["patient"]["sex"] = _clean(t_patient.get("sex")) or out["patient"]["sex"]

    referral = _clean(translated.get("referral_reason"))
    if referral and out["referral_reason"]:
        out["referral_reason"] = referral

    t_allergies = translated.get("allergies")
    if isinstance(t_allergies, list):
        for index, allergy in enumerate(out["allergies"]):
            if index >= len(t_allergies) or not isinstance(t_allergies[index], dict):
                continue
            reaction = _clean(t_allergies[index].get("reaction"))
            if reaction and allergy.get("reaction"):
                allergy["reaction"] = reaction

    t_flags = translated.get("flagged_items")
    if isinstance(t_flags, list):
        out["flagged_items"] = [
            _clean(t_flags[i]) or flag if i < len(t_flags) else flag
            for i, flag in enumerate(out["flagged_items"])
        ]

    return out
