from fastapi import FastAPI, APIRouter, Depends, HTTPException, Request, UploadFile, File, Form, Query
from fastapi.responses import Response
from fastapi.concurrency import run_in_threadpool
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import logging
import uuid
import re
from datetime import datetime, timezone, timedelta
from pathlib import Path
from pydantic import BaseModel, Field, EmailStr
from typing import Optional, List, Any
import bcrypt
import httpx

from storage import init_storage, put_object, get_object, StorageError, APP_NAME
from docclassifier import classify_image
from briefgen import generate_brief_content
from brieftranslator import translate_brief_content, LANGUAGE_NAMES
from briefpdf import render_brief_pdf
import asyncio


ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / ".env")

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
mongo_url = os.environ["MONGO_URL"]
db_name = os.environ["DB_NAME"]
client = AsyncIOMotorClient(mongo_url)
db = client[db_name]

SESSION_TTL_DAYS = 7
EMERGENT_SESSION_DATA_URL = (
    "https://demobackend.emergentagent.com/auth/v1/env/oauth/session-data"
)

app = FastAPI(title="Anteroom API")
api_router = APIRouter(prefix="/api")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger("anteroom")


# ---------------------------------------------------------------------------
# Models
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
# Helpers
# ---------------------------------------------------------------------------
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


async def _create_session(user_id: str) -> str:
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


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------
@api_router.get("/")
async def root():
    return {"service": "anteroom", "status": "ok"}


@api_router.post("/auth/register", response_model=AuthResponse)
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
    await _ensure_self_profile(user_doc)
    token = await _create_session(user_doc["user_id"])
    return AuthResponse(session_token=token, user=to_user_out(user_doc))


@api_router.post("/auth/login", response_model=AuthResponse)
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
    await _ensure_self_profile(user)
    token = await _create_session(user["user_id"])
    return AuthResponse(session_token=token, user=to_user_out(user))


@api_router.post("/auth/session", response_model=AuthResponse)
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

    await _ensure_self_profile(user_doc)
    token = await _create_session(user_doc["user_id"])
    return AuthResponse(session_token=token, user=to_user_out(user_doc))


@api_router.get("/auth/me", response_model=UserOut)
async def me(user: dict = Depends(get_current_user)):
    return to_user_out(user)


@api_router.post("/auth/logout")
async def logout(request: Request, user: dict = Depends(get_current_user)):
    auth = request.headers.get("authorization") or request.headers.get("Authorization")
    token = auth.split(" ", 1)[1].strip()
    await db.user_sessions.delete_one({"session_token": token})
    return {"ok": True}


@api_router.put("/auth/profile", response_model=UserOut)
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
    await _ensure_self_profile(user)
    return to_user_out(user)


# ---------------------------------------------------------------------------
# Family profiles
# ---------------------------------------------------------------------------
RELATIONSHIPS = {"self", "partner", "child", "parent", "other"}


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


def _profile_to_out(doc: dict) -> ProfileOut:
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


async def _ensure_self_profile(user: dict) -> dict:
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


def _validate_dob(dob: Optional[str]) -> Optional[str]:
    if dob is None or dob == "":
        return None
    if not re.match(r"^\d{4}-\d{2}-\d{2}$", dob):
        raise HTTPException(status_code=400, detail="dob must be YYYY-MM-DD")
    try:
        datetime.strptime(dob, "%Y-%m-%d")
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid dob")
    return dob


@api_router.get("/profiles", response_model=List[ProfileOut])
async def list_profiles(user: dict = Depends(get_current_user)):
    await _ensure_self_profile(user)
    cursor = db.profiles.find(
        {"user_id": user["user_id"], "deleted_at": None}, {"_id": 0}
    ).sort([("is_self", -1), ("created_at", 1)])
    docs = await cursor.to_list(50)
    return [_profile_to_out(d) for d in docs]


@api_router.post("/profiles", response_model=ProfileOut)
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
    dob = _validate_dob(payload.dob)
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
    return _profile_to_out(doc)


@api_router.patch("/profiles/{profile_id}", response_model=ProfileOut)
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
        updates["dob"] = _validate_dob(payload.dob)
    if payload.sex is not None:
        if payload.sex not in ("male", "female", "other", ""):
            raise HTTPException(status_code=400, detail="Invalid sex")
        updates["sex"] = payload.sex or None
    await db.profiles.update_one({"profile_id": profile_id}, {"$set": updates})
    doc.update(updates)
    return _profile_to_out(doc)


@api_router.delete("/profiles/{profile_id}")
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


async def _get_profile_or_404(user: dict, profile_id: str) -> dict:
    doc = await db.profiles.find_one(
        {"profile_id": profile_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Profile not found")
    return doc


# ---------------------------------------------------------------------------
# Briefs (document capture)
# ---------------------------------------------------------------------------
DOC_TYPES = {"referral", "med_list", "lab_result", "other"}
MAX_PHOTO_BYTES = 8 * 1024 * 1024  # 8 MB
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/jpg", "image/png", "image/webp"}


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


@api_router.post("/briefs", response_model=BriefOut)
async def create_brief(
    payload: CreateBriefRequest, user: dict = Depends(get_current_user)
):
    doc_type = (payload.doc_type or "other").lower()
    if doc_type not in DOC_TYPES:
        raise HTTPException(status_code=400, detail=f"doc_type must be one of {sorted(DOC_TYPES)}")
    # Resolve profile: explicit id, else the self profile.
    if payload.profile_id:
        profile = await _get_profile_or_404(user, payload.profile_id)
    else:
        profile = await _ensure_self_profile(user)
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


@api_router.get("/briefs", response_model=List[BriefOut])
async def list_briefs(
    user: dict = Depends(get_current_user),
    profile_id: Optional[str] = Query(default=None),
):
    query: dict = {"user_id": user["user_id"], "deleted_at": None}
    if profile_id:
        # Validate ownership
        await _get_profile_or_404(user, profile_id)
        query["profile_id"] = profile_id
    cursor = db.briefs.find(query, {"_id": 0}).sort("updated_at", -1)
    docs = await cursor.to_list(200)
    return [_brief_to_out(d) for d in docs]


@api_router.get("/briefs/{brief_id}", response_model=BriefOut)
async def get_brief(brief_id: str, user: dict = Depends(get_current_user)):
    doc = await db.briefs.find_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    )
    if not doc:
        raise HTTPException(status_code=404, detail="Brief not found")
    return _brief_to_out(doc)


@api_router.patch("/briefs/{brief_id}", response_model=BriefOut)
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
        # Validate
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


@api_router.delete("/briefs/{brief_id}")
async def delete_brief(brief_id: str, user: dict = Depends(get_current_user)):
    res = await db.briefs.update_one(
        {"brief_id": brief_id, "user_id": user["user_id"], "deleted_at": None},
        {"$set": {"deleted_at": datetime.now(timezone.utc)}},
    )
    if res.matched_count == 0:
        raise HTTPException(status_code=404, detail="Brief not found")
    return {"ok": True}


@api_router.post("/briefs/{brief_id}/photos", response_model=BriefOut)
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


@api_router.post("/briefs/{brief_id}/detect-doc-type", response_model=BriefOut)
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


@api_router.delete("/briefs/{brief_id}/photos/{photo_id}", response_model=BriefOut)
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


async def _resolve_user_from_bearer_or_token(
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
    session = await db.user_sessions.find_one({"session_token": session_token}, {"_id": 0})
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


@api_router.get("/briefs/{brief_id}/photos/{photo_id}/file")
async def get_brief_photo_file(
    brief_id: str,
    photo_id: str,
    request: Request,
    token: Optional[str] = Query(default=None),
):
    user = await _resolve_user_from_bearer_or_token(request, token)
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


@api_router.post("/briefs/{brief_id}/generate", response_model=BriefOut)
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


class TranslateBriefRequest(BaseModel):
    target_language: str  # 'en' | 'es'


@api_router.post("/briefs/{brief_id}/translate", response_model=BriefOut)
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


def _public_share_url(request: Request, share_token: str) -> str:
    base = str(request.base_url).rstrip("/")
    return f"{base}/api/public/briefs/{share_token}"


@api_router.get("/briefs/{brief_id}/pdf")
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
    user = await _resolve_user_from_bearer_or_token(request, token)
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


# ---- Public (unauthenticated) share view — mounted under /api so ingress reaches it ----
@api_router.get("/public/briefs/{share_token}")
async def public_brief_view(share_token: str, lang: Optional[str] = Query(default=None)):
    doc = await db.briefs.find_one(
        {"share_token": share_token, "deleted_at": None}, {"_id": 0}
    )
    if not doc or not doc.get("content"):
        return Response(
            content=_share_html_not_found(),
            media_type="text/html",
            status_code=404,
        )
    expires_at = doc.get("share_expires_at")
    if expires_at:
        if expires_at.tzinfo is None:
            expires_at = expires_at.replace(tzinfo=timezone.utc)
        if expires_at < datetime.now(timezone.utc):
            return Response(
                content=_share_html_expired(),
                media_type="text/html",
                status_code=410,
            )
    html = _share_html(doc, lang or None)
    return Response(content=html, media_type="text/html")


def _esc(v: Any) -> str:
    import html
    return html.escape(str(v)) if v is not None else ""


_SHARE_LABELS = {
    "en": {
        "title": "Pre-visit brief",
        "generated": "Generated",
        "disclaimer": "Anteroom · Extracted directly from patient documents — no diagnosis.",
        "patient": "Patient",
        "name": "Name",
        "dob": "DOB",
        "sex": "Sex",
        "id": "ID",
        "no_patient": "No patient details detected.",
        "referral": "Referral reason",
        "not_detected": "Not detected on the pages.",
        "medications": "Medications",
        "allergies": "Allergies",
        "none": "None detected.",
        "flagged": "Flagged for review",
        "not_found_title": "Brief not found",
        "not_found_body": "This brief link is invalid.",
        "expired_title": "Link expired",
        "expired_body": "This share link has expired. Ask the patient to regenerate it.",
    },
    "es": {
        "title": "Resumen previo a la visita",
        "generated": "Generado",
        "disclaimer": "Anteroom · Extraído directamente de los documentos del paciente — sin diagnóstico.",
        "patient": "Paciente",
        "name": "Nombre",
        "dob": "Fecha nac.",
        "sex": "Sexo",
        "id": "ID",
        "no_patient": "No se detectaron datos del paciente.",
        "referral": "Motivo de derivación",
        "not_detected": "No detectado en las páginas.",
        "medications": "Medicamentos",
        "allergies": "Alergias",
        "none": "No se detectaron.",
        "flagged": "Marcado para revisar",
        "not_found_title": "Resumen no encontrado",
        "not_found_body": "Este enlace no es válido.",
        "expired_title": "Enlace caducado",
        "expired_body": "Este enlace ha caducado. Pide al paciente que lo regenere.",
    },
}


def _share_html_not_found() -> str:
    L = _SHARE_LABELS["en"]
    return _share_wrapper(L["not_found_title"], f"<p>{L['not_found_body']}</p>", L["disclaimer"])


def _share_html_expired() -> str:
    L = _SHARE_LABELS["en"]
    return _share_wrapper(L["expired_title"], f"<p>{L['expired_body']}</p>", L["disclaimer"])


def _share_wrapper(title: str, body: str, disclaimer: str) -> str:
    return f"""<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title} — Anteroom</title>
<style>
  body{{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif;
       margin:0;padding:24px;background:#F4F7F6;color:#111815}}
  main{{max-width:640px;margin:24px auto;background:#fff;border-radius:20px;
       padding:32px;box-shadow:0 1px 3px rgba(17,24,21,0.08)}}
  h1{{color:#365F50;margin:0 0 8px}}
  h2{{color:#111815;font-size:16px;margin:20px 0 8px}}
  .muted{{color:#698075;font-size:12px}}
  .row{{display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid #E6EDE9}}
  .row:last-child{{border-bottom:0}}
  .label{{color:#698075}}
  .footer{{margin-top:32px;color:#698075;font-size:11px;text-align:center}}
  .flag{{background:#FFF3F3;border:1px solid #E6D0D0;color:#9E3838;padding:8px 12px;border-radius:12px;margin:6px 0;font-size:13px}}
  .empty{{color:#698075;font-style:italic}}
  .lang{{display:inline-flex;gap:6px;margin:8px 0 0}}
  .lang a{{display:inline-block;padding:4px 10px;border-radius:999px;background:#E6EDE9;
       color:#365F50;text-decoration:none;font-size:11px;font-weight:700;letter-spacing:0.4px}}
  .lang a.active{{background:#365F50;color:#fff}}
</style>
</head><body><main><h1>{title}</h1>{body}
<div class="footer">{disclaimer}</div>
</main></body></html>"""


def _share_html(doc: dict, lang: Optional[str] = None) -> str:
    source_lang = (doc.get("source_language") or "en").lower()
    translations = doc.get("content_translations") or {}
    requested = (lang or "").lower() if lang else source_lang
    if requested == source_lang:
        content = doc.get("content") or {}
        active_lang = source_lang
    elif requested in translations:
        content = translations[requested]
        active_lang = requested
    else:
        content = doc.get("content") or {}
        active_lang = source_lang

    L = _SHARE_LABELS.get(active_lang, _SHARE_LABELS["en"])
    patient = content.get("patient") or {}
    meds = content.get("medications") or []
    allergies = content.get("allergies") or []
    referral = content.get("referral_reason")
    flagged = content.get("flagged_items") or []
    generated_at = doc.get("generated_at")
    gen_str = generated_at.strftime("%Y-%m-%d %H:%M UTC") if generated_at else ""

    def kv(label: str, value: Any) -> str:
        if value is None or value == "":
            return ""
        return f'<div class="row"><span class="label">{_esc(label)}</span><span>{_esc(value)}</span></div>'

    patient_html = "".join(
        [
            kv(L["name"], patient.get("name")),
            kv(L["dob"], patient.get("dob")),
            kv(L["sex"], patient.get("sex")),
            kv(L["id"], patient.get("id_number")),
        ]
    ) or f'<div class="empty">{L["no_patient"]}</div>'

    meds_html = (
        "".join(
            f'<div class="row"><span>{_esc(m.get("name",""))}</span>'
            f'<span class="muted">{_esc(m.get("dose") or "—")} · {_esc(m.get("frequency") or "—")}</span></div>'
            for m in meds
        )
        or f'<div class="empty">{L["none"]}</div>'
    )

    allergies_html = (
        "".join(
            f'<div class="row"><span>{_esc(a.get("substance",""))}</span>'
            f'<span class="muted">{_esc(a.get("reaction") or "—")}</span></div>'
            for a in allergies
        )
        or f'<div class="empty">{L["none"]}</div>'
    )

    referral_html = (
        f"<p>{_esc(referral)}</p>"
        if referral
        else f'<p class="empty">{L["not_detected"]}</p>'
    )

    flagged_html = ""
    if flagged:
        flagged_html = f'<h2>{L["flagged"]}</h2>' + "".join(
            f'<div class="flag">{_esc(f)}</div>' for f in flagged
        )

    # Build language switcher — always show source; other langs only if cached.
    available = [source_lang] + [l for l in translations.keys() if l != source_lang]
    if len(available) > 1:
        base_path = f"/api/public/briefs/{doc.get('share_token','')}"
        chips = " ".join(
            f'<a class="{"active" if l == active_lang else ""}" href="{base_path}?lang={l}">{_esc(l.upper())}</a>'
            for l in available
        )
        lang_switch = f'<div class="lang">{chips}</div>'
    else:
        lang_switch = ""

    return _share_wrapper(
        L["title"],
        f"""
<p class="muted">{L["generated"]} {_esc(gen_str)} · {L["disclaimer"]}</p>
{lang_switch}
<h2>{L["patient"]}</h2>{patient_html}
<h2>{L["referral"]}</h2>{referral_html}
<h2>{L["medications"]} ({len(meds)})</h2>{meds_html}
<h2>{L["allergies"]} ({len(allergies)})</h2>{allergies_html}
{flagged_html}
""",
        L["disclaimer"],
    )


app.include_router(api_router)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
async def on_startup():
    await db.users.create_index("email", unique=True)
    await db.users.create_index("user_id", unique=True)
    await db.user_sessions.create_index("session_token", unique=True)
    await db.user_sessions.create_index("user_id")
    await db.user_sessions.create_index("expires_at", expireAfterSeconds=0)
    await db.briefs.create_index("brief_id", unique=True)
    await db.briefs.create_index([("user_id", 1), ("updated_at", -1)])
    await db.briefs.create_index([("user_id", 1), ("profile_id", 1)])
    await db.profiles.create_index("profile_id", unique=True)
    await db.profiles.create_index([("user_id", 1), ("is_self", -1)])

    # One-off backfill: give every user a self profile, and every brief that
    # predates family profiles a profile_id (mapped to its owner's self).
    async for u in db.users.find({}, {"_id": 0}):
        await _ensure_self_profile(u)
    async for b in db.briefs.find({"profile_id": {"$exists": False}}, {"_id": 0}):
        owner = await db.users.find_one({"user_id": b["user_id"]}, {"_id": 0})
        if not owner:
            continue
        self_prof = await _ensure_self_profile(owner)
        await db.briefs.update_one(
            {"brief_id": b["brief_id"]},
            {"$set": {"profile_id": self_prof["profile_id"]}},
        )

    try:
        await run_in_threadpool(init_storage)
        logger.info("Object storage initialized.")
    except Exception as e:
        logger.warning("Storage init deferred: %s", e)
    logger.info("Anteroom API ready, indexes ensured.")


@app.on_event("shutdown")
async def on_shutdown():
    client.close()
