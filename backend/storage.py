"""Emergent Object Storage helpers for Anteroom.

The FastAPI app calls these — the mobile client NEVER talks to storage directly.
"""

from __future__ import annotations

import os
from typing import Tuple

import requests
from dotenv import load_dotenv

# server.py also calls load_dotenv; calling it here first (idempotent) so this
# module works if imported before server.py's load_dotenv line runs.
load_dotenv()

APP_NAME = "anteroom"

_storage_key: str | None = None


def _base_url() -> str:
    base = (os.environ.get("INTEGRATION_PROXY_URL") or "").strip()
    if not base:
        base = "https://integrations.emergentagent.com"
    return base.rstrip("/") + "/objstore/api/v1/storage"


class StorageError(RuntimeError):
    pass


def init_storage() -> str:
    """Call once at startup. Idempotent. Returns a reusable storage_key."""
    global _storage_key
    if _storage_key:
        return _storage_key
    key = os.environ.get("EMERGENT_LLM_KEY")
    if not key:
        raise StorageError("EMERGENT_LLM_KEY is not set")
    resp = requests.post(
        f"{_base_url()}/init",
        json={"emergent_key": key},
        timeout=30,
    )
    resp.raise_for_status()
    _storage_key = resp.json()["storage_key"]
    return _storage_key


def _reset_and_reinit() -> str:
    global _storage_key
    _storage_key = None
    return init_storage()


def put_object(path: str, data: bytes, content_type: str) -> dict:
    """Upload. Overwrites silently if `path` already exists."""
    key = init_storage()
    resp = requests.put(
        f"{_base_url()}/objects/{path}",
        headers={"X-Storage-Key": key, "Content-Type": content_type},
        data=data,
        timeout=120,
    )
    if resp.status_code == 503:
        key = _reset_and_reinit()
        resp = requests.put(
            f"{_base_url()}/objects/{path}",
            headers={"X-Storage-Key": key, "Content-Type": content_type},
            data=data,
            timeout=120,
        )
    if resp.status_code == 402:
        raise StorageError("out_of_credits")
    resp.raise_for_status()
    return resp.json()


def get_object(path: str) -> Tuple[bytes, str]:
    """Download. Returns (bytes, content-type)."""
    key = init_storage()
    resp = requests.get(
        f"{_base_url()}/objects/{path}",
        headers={"X-Storage-Key": key},
        timeout=60,
    )
    if resp.status_code == 503:
        key = _reset_and_reinit()
        resp = requests.get(
            f"{_base_url()}/objects/{path}",
            headers={"X-Storage-Key": key},
            timeout=60,
        )
    resp.raise_for_status()
    return resp.content, resp.headers.get("Content-Type", "application/octet-stream")
