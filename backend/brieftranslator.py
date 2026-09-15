"""Translate an already-extracted brief content dict into another language.

Uses Gemini 3.1 Pro (same model as extraction) for consistency. STRICT rules:
- Do NOT invent, diagnose, or complete missing fields.
- Preserve drug names (generic/brand) exactly as extracted — never translate a
  medication name (they are proper nouns worldwide). Same for dosage numbers,
  units (mg, ml, IU), frequencies of numeric form ("1x/day"), dates and IDs.
- Only translate free-text human phrases: `referral_reason`, `flagged_items`,
  the reaction descriptions in `allergies`, and the free-form parts of the
  patient block that are clearly words (e.g. "Male" → "Hombre").
- Names of people (patient name) are NEVER translated.
- Return a JSON object with the SAME schema as the input.
"""

from __future__ import annotations

import json
import logging
import os
import re
import uuid
from typing import Any, Optional

from dotenv import load_dotenv
from emergentintegrations.llm.chat import LlmChat, UserMessage

load_dotenv()

logger = logging.getLogger("anteroom.brieftranslator")


LANGUAGE_NAMES = {
    "en": "English",
    "es": "Spanish (Castilian, es-ES)",
}


def _system_prompt(target_lang_code: str) -> str:
    target_name = LANGUAGE_NAMES.get(target_lang_code, target_lang_code)
    return (
        f"You translate a medical pre-visit brief JSON into {target_name} for a "
        "clinician reading it in that language.\n\n"
        "STRICT RULES — DO NOT VIOLATE:\n"
        "1. Never diagnose, interpret, or infer. Never add fields not in the input.\n"
        "2. NEVER translate a person's name — keep patient.name character-for-character.\n"
        "3. NEVER translate medication names, brand names, IDs, dates, numeric doses,\n"
        "   or units like mg/ml/µg/IU. Copy them verbatim.\n"
        "4. DO translate: referral_reason, flagged_items text, allergies[].reaction\n"
        "   text, and clearly-worded values in patient.sex (e.g. Male↔Hombre, F↔F).\n"
        "5. Preserve every field the input has — same keys, same array order, same\n"
        "   `null` for empty fields. Do NOT add new keys.\n"
        "6. Output MUST be a single valid JSON object matching the input schema. No\n"
        "   prose, no code fences, no leading/trailing text.\n"
    )


def _extract_json(text: str) -> Optional[dict]:
    if not text:
        return None
    fence = re.search(r"```(?:json)?\s*(\{.*\})\s*```", text, flags=re.DOTALL)
    candidate = fence.group(1) if fence else None
    if not candidate:
        m = re.search(r"\{.*\}", text, flags=re.DOTALL)
        candidate = m.group(0) if m else None
    if not candidate:
        return None
    try:
        return json.loads(candidate)
    except Exception:
        return None


def _sanitize_like_input(source: dict, translated: Any) -> dict:
    """Ensure the LLM did not delete keys or add new ones — fall back to source."""
    if not isinstance(translated, dict):
        return source

    def s(v: Any) -> Optional[str]:
        return v.strip() if isinstance(v, str) and v.strip() else None

    out: dict = {}

    # patient
    src_pat = source.get("patient") or {}
    t_pat = translated.get("patient") or {}
    if not isinstance(t_pat, dict):
        t_pat = {}
    out_pat: dict = {}
    for k in ("name", "dob", "sex", "id_number"):
        if k in src_pat:
            # Names/IDs/dates should never be translated — always use source.
            if k in ("name", "id_number", "dob"):
                out_pat[k] = src_pat[k]
            else:
                out_pat[k] = s(t_pat.get(k)) or src_pat.get(k)
    if out_pat:
        out["patient"] = out_pat

    # referral_reason
    if "referral_reason" in source:
        tr = s(translated.get("referral_reason"))
        out["referral_reason"] = tr if tr else source.get("referral_reason")

    # medications — keep name/dose/frequency from source (do not translate)
    if "medications" in source:
        out["medications"] = source["medications"]

    # allergies — keep substance, translate reaction only
    if "allergies" in source:
        src_all = source["allergies"] or []
        t_all = translated.get("allergies") or []
        if not isinstance(t_all, list):
            t_all = []
        out_all = []
        for i, a in enumerate(src_all):
            if not isinstance(a, dict):
                continue
            item = {"substance": a.get("substance")}
            src_rx = a.get("reaction")
            t_rx = None
            if i < len(t_all) and isinstance(t_all[i], dict):
                t_rx = s(t_all[i].get("reaction"))
            item["reaction"] = t_rx if t_rx is not None else src_rx
            # drop null reaction to match extractor shape
            if item["reaction"] is None:
                item.pop("reaction", None)
            out_all.append(item)
        out["allergies"] = out_all

    # flagged_items — translate strings, fall back per-item to source
    if "flagged_items" in source:
        src_fl = source["flagged_items"] or []
        t_fl = translated.get("flagged_items") or []
        if not isinstance(t_fl, list):
            t_fl = []
        out_fl = []
        for i, f in enumerate(src_fl):
            src_str = f if isinstance(f, str) else str(f)
            t_str = None
            if i < len(t_fl) and isinstance(t_fl[i], str):
                t_str = t_fl[i].strip() or None
            out_fl.append(t_str if t_str else src_str)
        out["flagged_items"] = out_fl

    return out


async def translate_brief_content(
    content: dict,
    target_language: str,
) -> dict:
    """Return `content` translated into `target_language` (e.g. 'es' or 'en')."""
    if target_language not in LANGUAGE_NAMES:
        raise ValueError(f"Unsupported target_language: {target_language}")

    key = os.environ.get("EMERGENT_LLM_KEY")
    if not key:
        raise RuntimeError("EMERGENT_LLM_KEY not set")
    if not isinstance(content, dict):
        raise ValueError("content must be a dict")

    chat = LlmChat(
        api_key=key,
        session_id=f"brief-tr-{uuid.uuid4().hex[:12]}",
        system_message=_system_prompt(target_language),
    ).with_model("gemini", "gemini-3.1-pro-preview")

    message = UserMessage(
        text=(
            "Translate the fields described in the system prompt. Return ONE JSON "
            "object with the exact same shape as the input.\n\nINPUT JSON:\n"
            + json.dumps(content, ensure_ascii=False)
        ),
    )
    raw = await chat.send_message(message)
    text = raw if isinstance(raw, str) else getattr(raw, "content", str(raw))
    parsed = _extract_json(text or "")
    if parsed is None:
        logger.warning(
            "Brief translation returned unparseable output: %r", (text or "")[:400]
        )
        return content  # fall back
    return _sanitize_like_input(content, parsed)
