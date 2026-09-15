"""Briefs CRUD + photo upload + doc-type detection + generation + translation + PDF."""

from __future__ import annotations

import asyncio
import logging
import uuid
from datetime import datetime, timedelta, timezone
from typing import List, Optional

from fastapi import APIRouter, Depends, File, HTTPException, Query, Request, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import Response

from briefgen import generate_brief_content
from briefpdf import render_brief_pdf
from brieftranslator import LANGUAGE_NAMES, translate_brief_content
from db import db
from deps import get_current_user, resolve_user_from_bearer_or_token
from docclassifier import classify_image
from models import (
    BriefOut,
    BriefPhotoOut,
    CreateBriefRequest,
    TranslateBriefRequest,
    UpdateBriefRequest,
)
from services.profiles import ensure_self_profile, get_profile_or_404
from storage import APP_NAME, StorageError, get_object, put_object


logger = logging.getLogger("anteroom.briefs")

router = APIRouter(tags=["briefs"])


DOC_TYPES = {"referral", "med_list", "lab_result", "other"}
MAX_PHOTO_BYTES = 8 * 1024 * 1024  # 8 MB
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/jpg", "image/png", "image/webp"}


def _brief_to_out(doc: dict) -> BriefOut:
    photos = [
        BriefPhotoOut(
            photo_id=p["photo_id"],
            filename=p["filename"],
            content_type=p["content_type"],
            size=p["size"],
            url=f"/api/briefs/{doc['brief_id']}/photos/{p['photo_id']}/file",
        )
        for p in doc.get("photos", [])
        if not p.get("deleted_at")
    ]
    translations = doc.get("content_translations") or {}
    source_lang = doc.get("source_language")
    langs: list[str] = []
    if source_lang:
        langs.append(source_lang)
    for k in translations.keys():
        if k not in langs:
            langs.append(k)
    return BriefOut(
        brief_id=doc["brief_id"],
        user_id=doc["user_id"],
        profile_id=doc.get("profile_id", ""),
        doc_type=doc.get("doc_type", "other"),
        doc_type_manual_override=bool(doc.get("doc_type_manual_override", False)),
        detected_doc_type=doc.get("detected_doc_type"),
        detected_confidence=doc.get("detected_confidence"),
        status=doc.get("status", "draft"),
        photos=photos,
        content=doc.get("content"),
        content_translations=translations or None,
        source_language=source_lang,
        available_languages=langs,
        generated_at=doc.get("generated_at"),
        share_url_path=(
            f"/api/public/briefs/{doc['share_token']}"
            if doc.get("share_token")
            else None
        ),
        created_at=doc["created_at"],
        updated_at=doc["updated_at"],
    )


def _public_share_url(request: Request, share_token: str) -> str:
    base = str(request.base_url).rstrip("/")
    return f"{base}/api/public/briefs/{share_token}"


# ---------------------------------------------------------------------------
# CRUD
# ---------------------------------------------------------------------------
@router.post("/briefs", response_model=BriefOut)
async def create_brief(
    payload: CreateBriefRequest, user: dict = Depends(get_current_user)
):
    doc_type = (payload.doc_type or "other").lower()
    if doc_type not in DOC_TYPES:
        raise HTTPException(
            status_code=400, detail=f"doc_type must be one of {sorted(DOC_TYPES)}"
        )
    # Resolve profile: explicit id, else the self profile.
    if payload.profile_id:
        profile = await get_profile_or_404(user, payload.profile_id)
    else:
        profile = await ensure_self_profile(user)
    now = datetime.now(timezone.utc)
    brief = {
        "brief_id": f"brief_{uuid.uuid4().hex[:12]}",
        "user_id": user["user_id"],
        "profile_id": profile["profile_id"],
        "doc_type": doc_type,
        "doc_type_manual_override": False,
        "detected_doc_type": None,
        "detected_confidence": None,
        "status": "draft",
        "photos": [],
        "created_at": now,
        "updated_at": now,
        "deleted_at": None,
    }
    await db.briefs.insert_one(brief)
    return _brief_to_out(brief)


@router.get("/briefs", response_model=List[BriefOut])
async def list_briefs(
    user: dict = Depends(get_current_user),
    profile_id: Optional[str] = Query(default=None),
):
    query: dict = {"user_id": user["user_id"], "deleted_at": None}
    if profile_id:
        # Validate ownership
        await get_profile_or_404(user, profile_id)
        query["profile_id"] = profile_id
    cursor = db.briefs.find(query, {"_id": 0}).sort("updated_at", -1)
    docs = await cursor.to_list(200)
    return [_brief_to_out(d) for d in docs]


@router.get("/briefs/{brief_id}", response_model=BriefOut)
async def get_brief(brief_id: str, user: dict = Depends(get_current_user)):
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")
    return _brief_to_out(doc)


@router.patch("/briefs/{brief_id}", response_model=BriefOut)
async def update_brief(
    brief_id: str,
    payload: UpdateBriefRequest,
    user: dict = Depends(get_current_user),
):
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")

    updates: dict = {"updated_at": datetime.now(timezone.utc)}
    if payload.doc_type is not None:
        dt = payload.doc_type.lower()
        if dt not in DOC_TYPES:
            raise HTTPException(status_code=400, detail="Invalid doc_type")
        updates["doc_type"] = dt
        # A manual PATCH of doc_type flags the brief as user-overridden so
        # background auto-detect stops touching it.
        updates["doc_type_manual_override"] = True
    if payload.status is not None:
        if payload.status not in ("draft", "complete"):
            raise HTTPException(status_code=400, detail="Invalid status")
        updates["status"] = payload.status
    if payload.photo_order is not None:
        by_id = {p["photo_id"]: p for p in doc.get("photos", [])}
        missing = [pid for pid in payload.photo_order if pid not in by_id]
        if missing:
            raise HTTPException(status_code=400, detail=f"Unknown photo_ids: {missing}")
        reordered = [by_id[pid] for pid in payload.photo_order]
        # Preserve any photos not in the order list at the end (defensive)
        trailing = [p for pid, p in by_id.items() if pid not in payload.photo_order]
        updates["photos"] = reordered + trailing

    await db.briefs.update_one({"brief_id": brief_id}, {"$set": updates})
    doc.update(updates)
    return _brief_to_out(doc)


@router.delete("/briefs/{brief_id}")
async def delete_brief(brief_id: str, user: dict = Depends(get_current_user)):
    res = await db.briefs.update_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"$set": {"deleted_at": datetime.now(timezone.utc)}},
    )
    if res.matched_count == 0:
        raise HTTPException(status_code=404, detail="Brief not found")
    return {"ok": True}


# ---------------------------------------------------------------------------
# Photos
# ---------------------------------------------------------------------------
@router.post("/briefs/{brief_id}/photos", response_model=BriefOut)
async def upload_brief_photo(
    brief_id: str,
    file: UploadFile = File(...),
    user: dict = Depends(get_current_user),
):
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")

    content_type = (file.content_type or "").lower()
    if content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(status_code=400, detail="Only JPEG/PNG/WEBP images are allowed")

    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty file")
    if len(data) > MAX_PHOTO_BYTES:
        raise HTTPException(status_code=413, detail="Photo too large (max 8 MB)")

    ext = {
        "image/jpeg": "jpg",
        "image/jpg": "jpg",
        "image/png": "png",
        "image/webp": "webp",
    }[content_type]
    photo_id = f"photo_{uuid.uuid4().hex[:12]}"
    storage_path = f"{APP_NAME}/uploads/{user['user_id']}/{photo_id}.{ext}"

    try:
        await run_in_threadpool(put_object, storage_path, data, content_type)
    except StorageError as e:
        if str(e) == "out_of_credits":
            raise HTTPException(status_code=402, detail="Storage credits exhausted")
        raise HTTPException(status_code=502, detail=str(e))
    except Exception as e:
        logger.exception("Storage upload failed: %s", e)
        raise HTTPException(status_code=502, detail="Upload failed")

    photo = {
        "photo_id": photo_id,
        "filename": file.filename or f"{photo_id}.{ext}",
        "content_type": content_type,
        "size": len(data),
        "storage_path": storage_path,
        "created_at": datetime.now(timezone.utc),
        "deleted_at": None,
    }
    await db.briefs.update_one(
        {"brief_id": brief_id},
        {
            "$push": {"photos": photo},
            "$set": {"updated_at": datetime.now(timezone.utc)},
        },
    )
    doc["photos"] = list(doc.get("photos", [])) + [photo]
    doc["updated_at"] = datetime.now(timezone.utc)

    # Only run auto-detect for the FIRST photo (or when nothing has been
    # detected yet) and only if the user has NOT manually overridden the
    # doc-type. Later photos on the same brief keep the existing type.
    has_prior_photos = any(
        p for p in doc["photos"][:-1] if not p.get("deleted_at")
    )
    should_auto_detect = (
        not doc.get("doc_type_manual_override", False)
        and (not has_prior_photos or not doc.get("detected_doc_type"))
    )
    if should_auto_detect:
        asyncio.create_task(
            _auto_classify_brief(brief_id, data, content_type)
        )

    return _brief_to_out(doc)


async def _auto_classify_brief(brief_id: str, image_bytes: bytes, content_type: str) -> None:
    """Background task: classify the image and, if not overridden, update doc_type."""
    try:
        result = await classify_image(image_bytes, content_type)
        now = datetime.now(timezone.utc)
        # Always persist the detected value + confidence for the hint.
        await db.briefs.update_one(
            {"brief_id": brief_id, "deleted_at": None},
            {
                "$set": {
                    "detected_doc_type": result["doc_type"],
                    "detected_confidence": result["confidence"],
                    "updated_at": now,
                }
            },
        )
        # Only write doc_type when the user has NOT overridden it. Conditional
        # match closes the race where a manual PATCH sneaks in during classify.
        effective = (
            result["doc_type"] if result["confidence"] != "low" else "other"
        )
        await db.briefs.update_one(
            {
                "brief_id": brief_id,
                "deleted_at": None,
                "doc_type_manual_override": False,
            },
            {"$set": {"doc_type": effective, "updated_at": now}},
        )
    except Exception as e:
        logger.exception("Auto-classify failed for brief %s: %s", brief_id, e)


@router.post("/briefs/{brief_id}/detect-doc-type", response_model=BriefOut)
async def detect_brief_doc_type(
    brief_id: str, user: dict = Depends(get_current_user)
):
    """Re-run doc-type detection using the brief's first photo.

    Clears any previous manual override — the user is explicitly asking the
    model to try again.
    """
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")
    photos = [p for p in doc.get("photos", []) if not p.get("deleted_at")]
    if not photos:
        raise HTTPException(status_code=400, detail="Attach a photo first")
    first = photos[0]
    try:
        image_bytes, ctype = await run_in_threadpool(get_object, first["storage_path"])
    except Exception as e:
        logger.exception("Fetching photo for detection failed: %s", e)
        raise HTTPException(status_code=502, detail="Could not read photo")

    result = await classify_image(image_bytes, ctype)
    effective = result["doc_type"] if result["confidence"] != "low" else "other"
    updates = {
        "doc_type": effective,
        "detected_doc_type": result["doc_type"],
        "detected_confidence": result["confidence"],
        "doc_type_manual_override": False,
        "updated_at": datetime.now(timezone.utc),
    }
    await db.briefs.update_one({"brief_id": brief_id}, {"$set": updates})
    doc.update(updates)
    return _brief_to_out(doc)


@router.delete("/briefs/{brief_id}/photos/{photo_id}", response_model=BriefOut)
async def delete_brief_photo(
    brief_id: str, photo_id: str, user: dict = Depends(get_current_user)
):
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")
    existing = next(
        (p for p in doc.get("photos", []) if p["photo_id"] == photo_id and not p.get("deleted_at")),
        None,
    )
    if not existing:
        raise HTTPException(status_code=404, detail="Photo not found")
    now = datetime.now(timezone.utc)
    await db.briefs.update_one(
        {"brief_id": brief_id, "photos.photo_id": photo_id},
        {"$set": {"photos.$.deleted_at": now, "updated_at": now}},
    )
    for p in doc.get("photos", []):
        if p["photo_id"] == photo_id:
            p["deleted_at"] = now
    doc["updated_at"] = now
    return _brief_to_out(doc)


@router.get("/briefs/{brief_id}/photos/{photo_id}/file")
async def get_brief_photo_file(
    brief_id: str,
    photo_id: str,
    request: Request,
    token: Optional[str] = Query(default=None),
):
    user = await resolve_user_from_bearer_or_token(request, token)
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")
    photo = next(
        (p for p in doc.get("photos", []) if p["photo_id"] == photo_id and not p.get("deleted_at")),
        None,
    )
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")
    try:
        content, ctype = await run_in_threadpool(get_object, photo["storage_path"])
    except Exception as e:
        logger.exception("Storage download failed: %s", e)
        raise HTTPException(status_code=502, detail="Download failed")
    return Response(content=content, media_type=ctype)


# ---------------------------------------------------------------------------
# Generate / translate / PDF
# ---------------------------------------------------------------------------
@router.post("/briefs/{brief_id}/generate", response_model=BriefOut)
async def generate_brief(brief_id: str, user: dict = Depends(get_current_user)):
    """Extract structured content from all attached photos with Gemini 3.1 Pro."""
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")
    photos = [p for p in doc.get("photos", []) if not p.get("deleted_at")]
    if not photos:
        raise HTTPException(status_code=400, detail="Attach at least one photo first")

    image_bytes: list[tuple[bytes, str]] = []
    for p in photos[:10]:  # cap at 10 pages
        try:
            b, ct = await run_in_threadpool(get_object, p["storage_path"])
        except Exception as e:
            logger.exception("Photo fetch for brief-gen failed: %s", e)
            raise HTTPException(status_code=502, detail="Could not read photos")
        image_bytes.append((b, ct))

    try:
        content = await generate_brief_content(image_bytes)
    except Exception as e:
        logger.exception("Brief generation failed: %s", e)
        raise HTTPException(status_code=502, detail="Brief generation failed")

    now = datetime.now(timezone.utc)
    share_token = doc.get("share_token") or uuid.uuid4().hex
    share_expires_at = now + timedelta(days=7)
    source_lang = (user.get("language") or "en").lower()
    if source_lang not in LANGUAGE_NAMES:
        source_lang = "en"
    await db.briefs.update_one(
        {"brief_id": brief_id},
        {
            "$set": {
                "content": content,
                "status": "complete",
                "generated_at": now,
                "share_token": share_token,
                "share_expires_at": share_expires_at,
                "source_language": source_lang,
                "content_translations": {},
                "updated_at": now,
            }
        },
    )
    doc.update(
        {
            "content": content,
            "status": "complete",
            "generated_at": now,
            "share_token": share_token,
            "share_expires_at": share_expires_at,
            "source_language": source_lang,
            "content_translations": {},
            "updated_at": now,
        }
    )
    return _brief_to_out(doc)


@router.post("/briefs/{brief_id}/translate", response_model=BriefOut)
async def translate_brief(
    brief_id: str,
    payload: TranslateBriefRequest,
    user: dict = Depends(get_current_user),
):
    """Translate an already-generated brief's content into another language.

    The translated payload is cached on the brief so subsequent requests are
    instant. Callers pass `target_language`; if it matches `source_language`
    we simply no-op.
    """
    target = (payload.target_language or "").lower().strip()
    if target not in LANGUAGE_NAMES:
        raise HTTPException(
            status_code=400,
            detail=f"target_language must be one of {sorted(LANGUAGE_NAMES)}",
        )
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")
    if not doc.get("content"):
        raise HTTPException(status_code=400, detail="Brief has not been generated yet")

    source_lang = (doc.get("source_language") or "en").lower()
    # Same language → no work.
    if target == source_lang:
        return _brief_to_out(doc)

    translations = doc.get("content_translations") or {}
    if target in translations:
        return _brief_to_out(doc)

    try:
        translated = await translate_brief_content(doc["content"], target)
    except Exception as e:
        logger.exception("Brief translation failed: %s", e)
        raise HTTPException(status_code=502, detail="Translation failed")

    translations = {**translations, target: translated}
    now = datetime.now(timezone.utc)
    await db.briefs.update_one(
        {"brief_id": brief_id},
        {"$set": {"content_translations": translations, "updated_at": now}},
    )
    doc["content_translations"] = translations
    doc["updated_at"] = now
    return _brief_to_out(doc)


@router.get("/briefs/{brief_id}/pdf")
async def get_brief_pdf(
    brief_id: str,
    request: Request,
    watermark: bool = Query(default=True),
    lang: Optional[str] = Query(default=None),
    token: Optional[str] = Query(default=None),
):
    """Render the brief as a 1-page PDF. `watermark` controls the free-tier ribbon.

    The client (which is the source of truth for Pro entitlement via
    RevenueCat) passes `watermark=false` when the user is subscribed.

    `lang` selects which cached translation to render. Defaults to the
    source language. Unknown / missing translations fall back to source.
    """
    user = await resolve_user_from_bearer_or_token(request, token)
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")
    if not doc.get("content"):
        raise HTTPException(status_code=400, detail="Brief has not been generated yet")

    source_lang = (doc.get("source_language") or "en").lower()
    translations = doc.get("content_translations") or {}
    requested = (lang or "").lower().strip() or source_lang
    if requested != source_lang and requested in translations:
        active_content = translations[requested]
    else:
        active_content = doc["content"]
        requested = source_lang

    share_url = None
    if doc.get("share_token"):
        share_url = _public_share_url(request, doc["share_token"])
    generated_at = doc.get("generated_at")
    generated_at_str = generated_at.strftime("%Y-%m-%d %H:%M UTC") if generated_at else ""
    pdf_bytes = await run_in_threadpool(
        render_brief_pdf,
        active_content,
        doc_type_label=doc.get("doc_type", "other"),
        generated_at=generated_at_str,
        watermarked=watermark,
        share_url=share_url or "",
        brief_id=brief_id,
        language=requested,
    )
    headers = {
        "Content-Disposition": f'inline; filename="anteroom-brief-{brief_id}-{requested}.pdf"'
    }
    return Response(content=pdf_bytes, media_type="application/pdf", headers=headers)
