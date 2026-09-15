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
from typing import Optional, List
import bcrypt
import httpx

from storage import init_storage, put_object, get_object, StorageError, APP_NAME


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
    return to_user_out(user)


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
    doc_type: str
    status: str  # 'draft' | 'complete'
    photos: List[BriefPhotoOut]
    created_at: datetime
    updated_at: datetime


class CreateBriefRequest(BaseModel):
    doc_type: Optional[str] = "other"


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
    return BriefOut(
        brief_id=doc["brief_id"],
        user_id=doc["user_id"],
        doc_type=doc.get("doc_type", "other"),
        status=doc.get("status", "draft"),
        photos=photos,
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
    now = datetime.now(timezone.utc)
    brief = {
        "brief_id": f"brief_{uuid.uuid4().hex[:12]}",
        "user_id": user["user_id"],
        "doc_type": doc_type,
        "status": "draft",
        "photos": [],
        "created_at": now,
        "updated_at": now,
        "deleted_at": None,
    }
    await db.briefs.insert_one(brief)
    return _brief_to_out(brief)


@api_router.get("/briefs", response_model=List[BriefOut])
async def list_briefs(user: dict = Depends(get_current_user)):
    cursor = db.briefs.find(
        {"user_id": user["user_id"], "deleted_at": None},
        {"_id": 0},
    ).sort("updated_at", -1)
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
    try:
        await run_in_threadpool(init_storage)
        logger.info("Object storage initialized.")
    except Exception as e:
        logger.warning("Storage init deferred: %s", e)
    logger.info("Anteroom API ready, indexes ensured.")


@app.on_event("shutdown")
async def on_shutdown():
    client.close()
