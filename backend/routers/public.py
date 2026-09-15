"""Public (unauthenticated) share view — mounted under /api so ingress reaches it."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, Query
from fastapi.responses import Response

from db import db
from services.share_html import (
    render_share_html,
    share_html_expired,
    share_html_not_found,
)


router = APIRouter(tags=["public"])


@router.get("/public/briefs/{share_token}")
async def public_brief_view(
    share_token: str, lang: Optional[str] = Query(default=None)
):
    doc = await db.briefs.find_one(
        {"share_token": share_token, "deleted_at": None}, {"_id": 0}
    )
    if not doc or not doc.get("content"):
        return Response(
            content=share_html_not_found(),
            media_type="text/html",
            status_code=404,
        )
    expires_at = doc.get("share_expires_at")
    if expires_at:
        if expires_at.tzinfo is None:
            expires_at = expires_at.replace(tzinfo=timezone.utc)
        if expires_at < datetime.now(timezone.utc):
            return Response(
                content=share_html_expired(),
                media_type="text/html",
                status_code=410,
            )
    html = render_share_html(doc, lang or None)
    return Response(content=html, media_type="text/html")
