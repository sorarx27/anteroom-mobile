"""Doc-type classification using Gemini 3.5 Flash via emergentintegrations."""

from __future__ import annotations

import base64
import json
import logging
import os
import re
import uuid
from typing import Literal, Optional, TypedDict

from dotenv import load_dotenv
from emergentintegrations.llm.chat import (
    ImageContent,
    LlmChat,
    UserMessage,
)

load_dotenv()

logger = logging.getLogger("anteroom.docclassifier")

DocType = Literal["referral", "med_list", "lab_result", "other"]
Confidence = Literal["low", "medium", "high"]

VALID_DOC_TYPES = {"referral", "med_list", "lab_result", "other"}
VALID_CONFIDENCE = {"low", "medium", "high"}

SYSTEM_PROMPT = (
    "You are a medical-document triage classifier. Given a photograph of a "
    "single medical document page, classify it into ONE of these categories:\n"
    "- referral: a doctor's referral letter (from GP to specialist, hospital admission letter, etc.).\n"
    "- med_list: a list or table of prescribed medications (drug name, dose, frequency).\n"
    "- lab_result: laboratory or diagnostic report (blood work, urinalysis, imaging report, cardiogram, etc.).\n"
    "- other: anything else, a business card, a photo that isn't a medical document, an unclear page, or when you cannot tell.\n\n"
    "Also rate your confidence: low, medium, or high. If confidence is low, prefer 'other'.\n\n"
    "Respond ONLY with a single-line JSON object of the form:\n"
    '{"doc_type": "referral|med_list|lab_result|other", "confidence": "low|medium|high"}\n'
    "No prose, no code fences."
)


class Classification(TypedDict):
    doc_type: DocType
    confidence: Confidence


def _extract_json(text: str) -> Optional[dict]:
    """Pull the first JSON object out of a possibly noisy model response."""
    if not text:
        return None
    m = re.search(r"\{[^{}]*\}", text, flags=re.DOTALL)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except Exception:
        return None


async def classify_image(
    image_bytes: bytes, content_type: str = "image/jpeg"
) -> Classification:
    """Classify a document image. Falls back to 'other'/'low' on any failure."""
    key = os.environ.get("EMERGENT_LLM_KEY")
    if not key:
        logger.warning("EMERGENT_LLM_KEY missing; returning default classification")
        return {"doc_type": "other", "confidence": "low"}

    try:
        b64 = base64.b64encode(image_bytes).decode("ascii")
        chat = LlmChat(
            api_key=key,
            session_id=f"doc-classify-{uuid.uuid4().hex[:12]}",
            system_message=SYSTEM_PROMPT,
        ).with_model("gemini", "gemini-3.5-flash")

        message = UserMessage(
            text="Classify the attached medical document page. Return JSON only.",
            file_contents=[ImageContent(image_base64=b64)],
        )
        raw = await chat.send_message(message)
        text = raw if isinstance(raw, str) else getattr(raw, "content", str(raw))
        parsed = _extract_json(text or "")
        if not parsed:
            logger.warning("Classifier returned unparseable output: %r", text[:200])
            return {"doc_type": "other", "confidence": "low"}

        doc_type = str(parsed.get("doc_type", "")).lower().strip()
        confidence = str(parsed.get("confidence", "")).lower().strip()
        if doc_type not in VALID_DOC_TYPES:
            doc_type = "other"
        if confidence not in VALID_CONFIDENCE:
            confidence = "low"
        return {"doc_type": doc_type, "confidence": confidence}  # type: ignore[return-value]
    except Exception as e:
        logger.exception("Doc classification failed: %s", e)
        return {"doc_type": "other", "confidence": "low"}
