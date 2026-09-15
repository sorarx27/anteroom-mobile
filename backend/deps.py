"""Shared request-level helpers and FastAPI dependencies.

- session/user id generators
- session creation helper
- `get_current_user` FastAPI dependency (Bearer token)
- `resolve_user_from_bearer_or_token` (accepts ?token= too, for <img> tags)
- `to_user_out` model mapper
"""

from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import HTTPException, Request

from db import SESSION_TTL_DAYS, db
from models import UserOut


def new_user_id() -> str:
    return f"user_{uuid.uuid4().hex[:12]}"


def new_session_token() -> str:
    return uuid.uuid4().hex + uuid.uuid4().hex


def to_user_out(doc: dict) -> UserOut:
    return UserOut(
        user_id=doc["user_id"],
        email=doc["email"],
        name=doc.get("name"),
        picture=doc.get("picture"),
        dob=doc.get("dob"),
        language=doc.get("language"),
        country=doc.get("country"),
        profile_completed=bool(doc.get("profile_completed", False)),
        provider=doc.get("provider", "email"),
    )


async def create_session(user_id: str) -> str:
    session_token = new_session_token()
    now = datetime.now(timezone.utc)
    await db.user_sessions.insert_one(
        {
            "session_token": session_token,
            "user_id": user_id,
            "created_at": now,
            "expires_at": now + timedelta(days=SESSION_TTL_DAYS),
        }
    )
    return session_token


async def get_current_user(request: Request) -> dict:
    auth = request.headers.get("authorization") or request.headers.get("Authorization")
    if not auth or not auth.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    token = auth.split(" ", 1)[1].strip()
    session = await db.user_sessions.find_one({"session_token": token}, {"_id": 0})
    if not session:
        raise HTTPException(status_code=401, detail="Invalid session")
    expires_at = session["expires_at"]
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at < datetime.now(timezone.utc):
        raise HTTPException(status_code=401, detail="Session expired")
    user = await db.users.find_one({"user_id": session["user_id"]}, {"_id": 0})
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return user


async def resolve_user_from_bearer_or_token(
    request: Request, token: Optional[str]
) -> dict:
    """Auth helper that also accepts a `?token=` query string (for <img> on web)."""
    auth = request.headers.get("authorization") or request.headers.get("Authorization")
    session_token = None
    if auth and auth.lower().startswith("bearer "):
        session_token = auth.split(" ", 1)[1].strip()
    elif token:
        session_token = token.strip()
    if not session_token:
        raise HTTPException(status_code=401, detail="Missing token")
    session = await db.user_sessions.find_one(
        {"session_token": session_token}, {"_id": 0}
    )
    if not session:
        raise HTTPException(status_code=401, detail="Invalid session")
    expires_at = session["expires_at"]
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at < datetime.now(timezone.utc):
        raise HTTPException(status_code=401, detail="Session expired")
    user = await db.users.find_one({"user_id": session["user_id"]}, {"_id": 0})
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return user
