"""Pydantic models shared across routers."""

from __future__ import annotations

from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, EmailStr, Field


# ---------------------------------------------------------------------------
# Users / auth
# ---------------------------------------------------------------------------
class UserOut(BaseModel):
    user_id: str
    email: EmailStr
    name: Optional[str] = None
    picture: Optional[str] = None
    dob: Optional[str] = None  # YYYY-MM-DD
    language: Optional[str] = None  # 'en' | 'es'
    country: Optional[str] = None
    profile_completed: bool = False
    provider: str = "email"


class AuthResponse(BaseModel):
    session_token: str
    user: UserOut


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=6)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class GoogleSessionRequest(BaseModel):
    session_id: str


class ProfileUpdateRequest(BaseModel):
    name: str = Field(min_length=1)
    dob: str  # YYYY-MM-DD
    language: str  # 'en' | 'es'
    country: str


# ---------------------------------------------------------------------------
# Family profiles
# ---------------------------------------------------------------------------
class ProfileOut(BaseModel):
    profile_id: str
    user_id: str
    name: str
    relationship: str  # 'self' | 'partner' | 'child' | 'parent' | 'other'
    dob: Optional[str] = None
    sex: Optional[str] = None
    is_self: bool
    created_at: datetime
    updated_at: datetime


class CreateProfileRequest(BaseModel):
    name: str = Field(min_length=1)
    relationship: str  # partner|child|parent|other  (self is auto-created)
    dob: Optional[str] = None
    sex: Optional[str] = None


class UpdateProfileRequest(BaseModel):
    name: Optional[str] = None
    relationship: Optional[str] = None
    dob: Optional[str] = None
    sex: Optional[str] = None


# ---------------------------------------------------------------------------
# Briefs
# ---------------------------------------------------------------------------
class BriefPhotoOut(BaseModel):
    photo_id: str
    filename: str
    content_type: str
    size: int
    url: str  # backend-served, requires auth


class BriefOut(BaseModel):
    brief_id: str
    user_id: str
    profile_id: str
    doc_type: str
    doc_type_manual_override: bool = False
    detected_doc_type: Optional[str] = None
    detected_confidence: Optional[str] = None
    status: str  # 'draft' | 'complete'
    photos: List[BriefPhotoOut]
    content: Optional[dict] = None  # generated brief content
    content_translations: Optional[dict] = None  # {"es": {...}, "en": {...}}
    source_language: Optional[str] = None  # locked at generate-time
    available_languages: List[str] = []  # union of source_language + cached translations
    generated_at: Optional[datetime] = None
    share_url_path: Optional[str] = None  # /public/briefs/{share_token}
    created_at: datetime
    updated_at: datetime


class CreateBriefRequest(BaseModel):
    doc_type: Optional[str] = "other"
    profile_id: Optional[str] = None


class UpdateBriefRequest(BaseModel):
    doc_type: Optional[str] = None
    status: Optional[str] = None
    photo_order: Optional[List[str]] = None  # list of photo_ids


class TranslateBriefRequest(BaseModel):
    target_language: str  # 'en' | 'es'
