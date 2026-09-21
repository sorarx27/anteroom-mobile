"""Authentication routes — register / login / Google session / me / logout / profile / delete."""

from __future__ import annotations

import logging
import re
from datetime import datetime, timezone

import bcrypt
import httpx
from fastapi import APIRouter, Depends, HTTPException, Request

from db import EMERGENT_SESSION_DATA_URL, db
from deps import (
    create_session,
    get_current_user,
    new_user_id,
    to_user_out,
)
from models import (
    AuthResponse,
    GoogleSessionRequest,
    LoginRequest,
    ProfileUpdateRequest,
    RegisterRequest,
    UserOut,
)
from services.profiles import ensure_self_profile


logger = logging.getLogger("anteroom.auth")

router = APIRouter(tags=["auth"])


@router.post("/auth/register", response_model=AuthResponse)
async def register(payload: RegisterRequest):
    email = payload.email.lower().strip()
    existing = await db.users.find_one({"email": email}, {"_id": 0})
    if existing:
        raise HTTPException(status_code=409, detail="Email already registered")
    pw_hash = bcrypt.hashpw(payload.password.encode("utf-8"), bcrypt.gensalt()).decode(
        "utf-8"
    )
    user_doc = {
        "user_id": new_user_id(),
        "email": email,
        "password_hash": pw_hash,
        "provider": "email",
        "profile_completed": False,
        "created_at": datetime.now(timezone.utc),
    }
    await db.users.insert_one(user_doc)
    await ensure_self_profile(user_doc)
    token = await create_session(user_doc["user_id"])
    return AuthResponse(session_token=token, user=to_user_out(user_doc))


@router.post("/auth/login", response_model=AuthResponse)
async def login(payload: LoginRequest):
    email = payload.email.lower().strip()
    user = await db.users.find_one({"email": email}, {"_id": 0})
    if not user or not user.get("password_hash"):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    ok = bcrypt.checkpw(
        payload.password.encode("utf-8"), user["password_hash"].encode("utf-8")
    )
    if not ok:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    await ensure_self_profile(user)
    token = await create_session(user["user_id"])
    return AuthResponse(session_token=token, user=to_user_out(user))


@router.post("/auth/session", response_model=AuthResponse)
async def google_session(payload: GoogleSessionRequest):
    """Exchange Emergent one-time session_id for a session_token."""
    session_id = payload.session_id.strip()
    if not session_id:
        raise HTTPException(status_code=400, detail="Missing session_id")
    try:
        async with httpx.AsyncClient(timeout=10.0) as h:
            resp = await h.get(
                EMERGENT_SESSION_DATA_URL,
                headers={"X-Session-ID": session_id},
            )
    except Exception as exc:  # network / timeout
        logger.exception("Emergent session exchange failed: %s", exc)
        raise HTTPException(status_code=401, detail="Authentication failed")
    if resp.status_code != 200:
        raise HTTPException(status_code=401, detail="Invalid session")
    data = resp.json()
    email = (data.get("email") or "").lower().strip()
    if not email:
        raise HTTPException(status_code=401, detail="No email in profile")
    name = data.get("name")
    picture = data.get("picture")

    existing = await db.users.find_one({"email": email}, {"_id": 0})
    if existing:
        user_id = existing["user_id"]
        # Update profile fields from Google if not set
        updates = {}
        if not existing.get("name") and name:
            updates["name"] = name
        if not existing.get("picture") and picture:
            updates["picture"] = picture
        if updates:
            await db.users.update_one({"user_id": user_id}, {"$set": updates})
            existing.update(updates)
        user_doc = existing
    else:
        user_doc = {
            "user_id": new_user_id(),
            "email": email,
            "name": name,
            "picture": picture,
            "provider": "google",
            "profile_completed": False,
            "created_at": datetime.now(timezone.utc),
        }
        await db.users.insert_one(user_doc)

    await ensure_self_profile(user_doc)
    token = await create_session(user_doc["user_id"])
    return AuthResponse(session_token=token, user=to_user_out(user_doc))


@router.get("/auth/me", response_model=UserOut)
async def me(user: dict = Depends(get_current_user)):
    return to_user_out(user)


@router.post("/auth/logout")
async def logout(request: Request, user: dict = Depends(get_current_user)):
    auth = request.headers.get("authorization") or request.headers.get("Authorization")
    token = auth.split(" ", 1)[1].strip()
    await db.user_sessions.delete_one({"session_token": token})
    return {"ok": True}


@router.delete("/auth/account")
async def delete_account(user: dict = Depends(get_current_user)):
    """Permanently delete the account and everything owned by it.

    Required by App Store Review Guideline 5.1.1(v): an app that supports
    account creation must let the user initiate deletion from inside the app.
    """
    user_id = user["user_id"]
    await db.briefs.delete_many({"user_id": user_id})
    await db.profiles.delete_many({"user_id": user_id})
    await db.user_sessions.delete_many({"user_id": user_id})
    result = await db.users.delete_one({"user_id": user_id})
    if result.deleted_count != 1:
        logger.error("account deletion left user %s in place", user_id)
        raise HTTPException(status_code=500, detail="Account deletion failed")
    logger.info("account deleted: %s", user_id)
    return {"ok": True}


@router.put("/auth/profile", response_model=UserOut)
async def update_profile(
    payload: ProfileUpdateRequest, user: dict = Depends(get_current_user)
):
    # Validate DOB format
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", payload.dob):
        raise HTTPException(status_code=400, detail="dob must be YYYY-MM-DD")
    try:
        datetime.strptime(payload.dob, "%Y-%m-%d")
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid dob")
    if payload.language not in ("en", "es"):
        raise HTTPException(status_code=400, detail="language must be 'en' or 'es'")
    updates = {
        "name": payload.name.strip(),
        "dob": payload.dob,
        "language": payload.language,
        "country": payload.country.strip(),
        "profile_completed": True,
    }
    await db.users.update_one({"user_id": user["user_id"]}, {"$set": updates})
    user.update(updates)
    await ensure_self_profile(user)
    return to_user_out(user)
