"""Brief content extraction from photos using Gemini 3.1 Pro Preview.

STRICT rules for the LLM:
- Do NOT diagnose or infer anything not literally visible on the pages.
- If a field is not clearly present on the images, return null (or [] for lists).
- Copy names, dosages, dates and numbers verbatim from the documents.
- Flag anything that is unreadable / cut off / high-risk-but-illegible in
  `flagged_items` rather than guessing.
"""

from __future__ import annotations

import base64
import json
import logging
import os
import re
import uuid
from typing import Any, Optional, TypedDict

from dotenv import load_dotenv
from emergentintegrations.llm.chat import (
    ImageContent,
    LlmChat,
    UserMessage,
)

load_dotenv()

logger = logging.getLogger("anteroom.briefgen")


class Medication(TypedDict, total=False):
    name: str
    dose: Optional[str]
    frequency: Optional[str]


class Allergy(TypedDict, total=False):
    substance: str
    reaction: Optional[str]


class PatientInfo(TypedDict, total=False):
    name: Optional[str]
    dob: Optional[str]
    sex: Optional[str]
    id_number: Optional[str]


class BriefContent(TypedDict, total=False):
    patient: PatientInfo
    referral_reason: Optional[str]
    medications: list[Medication]
    allergies: list[Allergy]
    flagged_items: list[str]


SYSTEM_PROMPT = (
    "You are a strict medical-document data extractor for a pre-visit brief. "
    "You will receive one or more photos of medical documents (referral letters, "
    "medication lists, lab results, etc.) belonging to ONE patient.\n\n"
    "STRICT RULES — DO NOT VIOLATE:\n"
    "1. Never diagnose, interpret, explain, translate, or infer anything. Extract only what is literally printed on the pages.\n"
    "2. Never invent, guess or complete a missing value. If a field is not clearly present, use null (or [] for lists).\n"
    "3. Copy patient names, medication names, dosages, frequencies, dates and numbers VERBATIM from the images. Do not normalise units or translate.\n"
    "4. If any part of a high-risk field (dosage, frequency, allergy) is UNREADABLE, cut off, blurry or ambiguous — DO NOT guess. Add a short human-readable note to `flagged_items` instead (e.g. 'Warfarin dose unreadable on page 2'). Then omit that field from `medications`/`allergies`.\n"
    "5. Only include a medication once — if the same drug appears multiple times, keep the clearest entry.\n"
    "6. `referral_reason` is a single sentence taken verbatim from a referral letter's stated reason. If the pages do not contain a referral letter, set it to null.\n"
    "7. Output MUST be a single valid JSON object matching the schema below. No prose, no code fences, no leading/trailing text.\n\n"
    "SCHEMA:\n"
    "{\n"
    '  "patient": { "name": string|null, "dob": string|null, "sex": string|null, "id_number": string|null },\n'
    '  "referral_reason": string|null,\n'
    '  "medications": [ { "name": string, "dose": string|null, "frequency": string|null } ],\n'
    '  "allergies": [ { "substance": string, "reaction": string|null } ],\n'
    '  "flagged_items": [ string ]\n'
    "}"
)


def _extract_json(text: str) -> Optional[dict]:
    if not text:
        return None
    fence = re.search(r"```(?:json)?\s*(\{.*\})\s*```", text, flags=re.DOTALL)
    candidate = fence.group(1) if fence else None
    if not candidate:
        # Grab the first {...} block, tolerating nested braces via a greedy match.
        m = re.search(r"\{.*\}", text, flags=re.DOTALL)
        candidate = m.group(0) if m else None
    if not candidate:
        return None
    try:
        return json.loads(candidate)
    except Exception:
        return None


def _sanitize(raw: Any) -> BriefContent:
    """Coerce any keys/types we didn't ask for."""
    out: BriefContent = {
        "patient": {},
        "referral_reason": None,
        "medications": [],
        "allergies": [],
        "flagged_items": [],
    }
    if not isinstance(raw, dict):
        return out
    patient = raw.get("patient") or {}
    if isinstance(patient, dict):
        for k in ("name", "dob", "sex", "id_number"):
            v = patient.get(k)
            if isinstance(v, str) and v.strip():
                out["patient"][k] = v.strip()  # type: ignore[literal-required]
    rr = raw.get("referral_reason")
    if isinstance(rr, str) and rr.strip():
        out["referral_reason"] = rr.strip()
    for m in raw.get("medications") or []:
        if not isinstance(m, dict):
            continue
        name = m.get("name")
        if not isinstance(name, str) or not name.strip():
            continue
        item: Medication = {"name": name.strip()}
        for k in ("dose", "frequency"):
            v = m.get(k)
            if isinstance(v, str) and v.strip():
                item[k] = v.strip()  # type: ignore[literal-required]
        out["medications"].append(item)
    for a in raw.get("allergies") or []:
        if not isinstance(a, dict):
            continue
        subs = a.get("substance")
        if not isinstance(subs, str) or not subs.strip():
            continue
        item2: Allergy = {"substance": subs.strip()}
        rx = a.get("reaction")
        if isinstance(rx, str) and rx.strip():
            item2["reaction"] = rx.strip()
        out["allergies"].append(item2)
    for f in raw.get("flagged_items") or []:
        if isinstance(f, str) and f.strip():
            out["flagged_items"].append(f.strip())
    return out


async def generate_brief_content(
    photos: list[tuple[bytes, str]],
) -> BriefContent:
    """Call Gemini 3.1 Pro Preview with all page images; return sanitized content."""
    key = os.environ.get("EMERGENT_LLM_KEY")
    if not key:
        raise RuntimeError("EMERGENT_LLM_KEY not set")
    if not photos:
        raise ValueError("no photos to process")

    file_contents = [
        ImageContent(image_base64=base64.b64encode(b).decode("ascii"))
        for (b, _) in photos
    ]
    chat = LlmChat(
        api_key=key,
        session_id=f"brief-gen-{uuid.uuid4().hex[:12]}",
        system_message=SYSTEM_PROMPT,
    ).with_model("gemini", "gemini-3.1-pro-preview")

    message = UserMessage(
        text=(
            "Extract the pre-visit brief data from the attached photos. "
            "Return ONE JSON object exactly matching the schema in the system "
            "message. Do NOT include anything else."
        ),
        file_contents=file_contents,
    )
    raw = await chat.send_message(message)
    text = raw if isinstance(raw, str) else getattr(raw, "content", str(raw))
    parsed = _extract_json(text or "")
    if parsed is None:
        logger.warning("Brief generation returned unparseable output: %r", (text or "")[:400])
        return _sanitize({})
    return _sanitize(parsed)
