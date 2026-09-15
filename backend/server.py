from fastapi import FastAPI, APIRouter, Depends, HTTPException, Request
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
from typing import Optional
import bcrypt
import httpx


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
    logger.info("Anteroom API ready, indexes ensured.")


@app.on_event("shutdown")
async def on_shutdown():
    client.close()
