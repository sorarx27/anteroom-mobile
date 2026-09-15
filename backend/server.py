"""Anteroom FastAPI entrypoint.

This module wires the modular routers together, ensures MongoDB indexes on
startup, and runs a one-off backfill for legacy briefs missing profile_id.

Route logic lives in `routers/*` and helpers live in `services/*` and
`deps.py`. Keep this file thin — new endpoints should go in a router.
"""

from __future__ import annotations

import logging

from fastapi import APIRouter, FastAPI
from fastapi.concurrency import run_in_threadpool
from starlette.middleware.cors import CORSMiddleware

from db import client, db
from routers.auth import router as auth_router
from routers.briefs import router as briefs_router
from routers.profiles import router as profiles_router
from routers.public import router as public_router
from services.profiles import ensure_self_profile
from storage import init_storage


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger("anteroom")


app = FastAPI(title="Anteroom API")


# All routes are exposed under /api so the Kubernetes ingress rules forward
# them to this process. The individual routers stay prefix-free so they can
# be tested / mounted independently.
api_router = APIRouter(prefix="/api")


@api_router.get("/")
async def root():
    return {"service": "anteroom", "status": "ok"}


api_router.include_router(auth_router)
api_router.include_router(profiles_router)
api_router.include_router(briefs_router)
api_router.include_router(public_router)

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
        await ensure_self_profile(u)
    async for b in db.briefs.find({"profile_id": {"$exists": False}}, {"_id": 0}):
        owner = await db.users.find_one({"user_id": b["user_id"]}, {"_id": 0})
        if not owner:
            continue
        self_prof = await ensure_self_profile(owner)
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
