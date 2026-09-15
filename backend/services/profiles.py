"""Family-profile helpers reused by auth (self-profile bootstrap) and profile routes."""

from __future__ import annotations

import re
import uuid
from datetime import datetime, timezone
from typing import Optional

from fastapi import HTTPException

from db import db
from models import ProfileOut


RELATIONSHIPS = {"self", "partner", "child", "parent", "other"}


def profile_to_out(doc: dict) -> ProfileOut:
    return ProfileOut(
        profile_id=doc["profile_id"],
        user_id=doc["user_id"],
        name=doc["name"],
        relationship=doc["relationship"],
        dob=doc.get("dob"),
        sex=doc.get("sex"),
        is_self=bool(doc.get("is_self", False)),
        created_at=doc["created_at"],
        updated_at=doc["updated_at"],
    )


async def ensure_self_profile(user: dict) -> dict:
    """Return (creating if needed) the user's self profile."""
    existing = await db.profiles.find_one(
        {"user_id": user["user_id"], "is_self": True, "deleted_at": None},
        {"_id": 0},
    )
    if existing:
        return existing
    now = datetime.now(timezone.utc)
    doc = {
        "profile_id": f"prof_{uuid.uuid4().hex[:12]}",
        "user_id": user["user_id"],
        "name": (user.get("name") or "You").strip() or "You",
        "relationship": "self",
        "dob": user.get("dob"),
        "sex": None,
        "is_self": True,
        "created_at": now,
        "updated_at": now,
        "deleted_at": None,
    }
    await db.profiles.insert_one(doc)
    return doc


def validate_dob(dob: Optional[str]) -> Optional[str]:
    if dob is None or dob == "":
        return None
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", dob):
        raise HTTPException(status_code=400, detail="dob must be YYYY-MM-DD")
    try:
        datetime.strptime(dob, "%Y-%m-%d")
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid dob")
    return dob


async def get_profile_or_404(user: dict, profile_id: str) -> dict:
    doc = await db.profiles.find_one(
        {"profile_id": profile_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Profile not found")
    return doc
