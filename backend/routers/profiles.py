"""Family-profile CRUD routes."""

from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import List

from fastapi import APIRouter, Depends, HTTPException

from db import db
from deps import get_current_user
from models import (
    CreateProfileRequest,
    ProfileOut,
    UpdateProfileRequest,
)
from services.profiles import (
    RELATIONSHIPS,
    ensure_self_profile,
    profile_to_out,
    validate_dob,
)


router = APIRouter(tags=["profiles"])


@router.get("/profiles", response_model=List[ProfileOut])
async def list_profiles(user: dict = Depends(get_current_user)):
    await ensure_self_profile(user)
    cursor = db.profiles.find(
        {"user_id": user["user_id"], "deleted_at": None}, {"_id": 0}
    ).sort([("is_self", -1), ("created_at", 1)])
    docs = await cursor.to_list(50)
    return [profile_to_out(d) for d in docs]


@router.post("/profiles", response_model=ProfileOut)
async def create_profile(
    payload: CreateProfileRequest, user: dict = Depends(get_current_user)
):
    rel = payload.relationship.lower().strip()
    if rel not in RELATIONSHIPS or rel == "self":
        raise HTTPException(
            status_code=400,
            detail="relationship must be one of partner|child|parent|other",
        )
    sex = (payload.sex or None)
    if sex is not None and sex not in ("male", "female", "other"):
        raise HTTPException(status_code=400, detail="Invalid sex")
    dob = validate_dob(payload.dob)
    now = datetime.now(timezone.utc)
    doc = {
        "profile_id": f"prof_{uuid.uuid4().hex[:12]}",
        "user_id": user["user_id"],
        "name": payload.name.strip(),
        "relationship": rel,
        "dob": dob,
        "sex": sex,
        "is_self": False,
        "created_at": now,
        "updated_at": now,
        "deleted_at": None,
    }
    await db.profiles.insert_one(doc)
    return profile_to_out(doc)


@router.patch("/profiles/{profile_id}", response_model=ProfileOut)
async def update_profile_endpoint(
    profile_id: str,
    payload: UpdateProfileRequest,
    user: dict = Depends(get_current_user),
):
    doc = await db.profiles.find_one(
        {"profile_id": profile_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Profile not found")
    updates: dict = {"updated_at": datetime.now(timezone.utc)}
    if payload.name is not None:
        n = payload.name.strip()
        if not n:
            raise HTTPException(status_code=400, detail="Name cannot be empty")
        updates["name"] = n
    if payload.relationship is not None:
        rel = payload.relationship.lower().strip()
        if rel not in RELATIONSHIPS:
            raise HTTPException(status_code=400, detail="Invalid relationship")
        if doc.get("is_self") and rel != "self":
            raise HTTPException(
                status_code=400, detail="Cannot change relationship on Self profile"
            )
        if rel == "self" and not doc.get("is_self"):
            raise HTTPException(status_code=400, detail="Only one Self profile allowed")
        updates["relationship"] = rel
    if payload.dob is not None:
        updates["dob"] = validate_dob(payload.dob)
    if payload.sex is not None:
        if payload.sex not in ("male", "female", "other", ""):
            raise HTTPException(status_code=400, detail="Invalid sex")
        updates["sex"] = payload.sex or None
    await db.profiles.update_one({"profile_id": profile_id}, {"$set": updates})
    doc.update(updates)
    return profile_to_out(doc)


@router.delete("/profiles/{profile_id}")
async def delete_profile_endpoint(
    profile_id: str, user: dict = Depends(get_current_user)
):
    doc = await db.profiles.find_one(
        {"profile_id": profile_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Profile not found")
    if doc.get("is_self"):
        raise HTTPException(status_code=400, detail="The Self profile cannot be removed")
    now = datetime.now(timezone.utc)
    await db.profiles.update_one(
        {"profile_id": profile_id}, {"$set": {"deleted_at": now, "updated_at": now}}
    )
    # Soft-archive that profile's briefs too.
    await db.briefs.update_many(
        {
            "profile_id": profile_id,
            "user_id": user["user_id"],
            "deleted_at": None,
        },
        {"$set": {"deleted_at": now}},
    )
    return {"ok": True}
