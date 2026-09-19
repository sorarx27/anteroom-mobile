"""Firestore and Storage access, plus the guards that run before any spend.

Everything here uses the Admin SDK, which bypasses security rules entirely.
That is the whole point: `firestore.rules` forbids clients from writing
`content`, `status`, `generated_at` and the share token, and these functions
are the only thing that may. It also means every ownership check that the
rules would have done has to be done explicitly here -- see `load_owned_brief`.
"""

from __future__ import annotations

import datetime as _dt
import io
import logging
from typing import Any

from firebase_admin import firestore, storage
from firebase_functions import https_fn

from anteroom.config import (
    DAILY_MODEL_CALLS_PER_USER,
    ENTITLEMENT_DOC,
    IMAGE_JPEG_QUALITY,
    IMAGE_LONG_EDGE,
    MAX_PAGES,
    RUNTIME_CONFIG_DOC,
)

logger = logging.getLogger("anteroom.store")


def db():
    return firestore.client()


def bucket():
    return storage.bucket()


# ---------------------------------------------------------------------------
# Timestamps
# ---------------------------------------------------------------------------
def now_iso() -> str:
    """Fixed-width ISO-8601 UTC, matching `nowIso()` on the Kotlin side.

    Two things are load-bearing. It is a **string**: the Kotlin model types
    these fields as String, and a real Firestore Timestamp fails to decode,
    which used to make the brief silently vanish from the dashboard. And the
    width is **fixed** -- milliseconds always present -- because `orderBy` on a
    string is lexicographic and `...:00.5Z` would otherwise sort before
    `...:00.500Z`.
    """
    now = _dt.datetime.now(_dt.timezone.utc)
    return f"{now:%Y-%m-%dT%H:%M:%S}.{now.microsecond // 1000:03d}Z"


def _today() -> str:
    return _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%d")


# ---------------------------------------------------------------------------
# Guards
# ---------------------------------------------------------------------------
def require_uid(req: https_fn.CallableRequest) -> str:
    if req.auth is None or not req.auth.uid:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.UNAUTHENTICATED,
            "You need to be signed in to do that.",
        )
    return req.auth.uid


def require_ai_enabled() -> None:
    """Global kill switch.

    Read before every model call rather than cached, so flipping
    `config/runtime.ai_enabled` to false halts spend within seconds and without
    a deploy. A missing document means enabled, so the healthy state needs no
    setup and a Firestore blip cannot silently disable the product.
    """
    try:
        snap = db().document(RUNTIME_CONFIG_DOC).get()
    except Exception:
        logger.exception("Kill-switch read failed; failing open")
        return
    if snap.exists and snap.to_dict().get("ai_enabled") is False:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.UNAVAILABLE,
            "Document processing is paused right now. Try again later.",
        )


def charge_quota(uid: str, calls: int = 1) -> None:
    """Count model calls against a per-user daily budget, before spending.

    Uses a transaction so two devices on one account cannot both read 39 and
    both proceed. The counter lives at `users/{uid}/usage/{YYYY-MM-DD}`, which
    rules expose read-only to its owner so the UI can show what is left.
    """
    ref = db().collection("users").document(uid).collection("usage").document(_today())
    transaction = db().transaction()

    @firestore.transactional
    def _charge(txn) -> None:
        snap = ref.get(transaction=txn)
        used = (snap.to_dict() or {}).get("model_calls", 0) if snap.exists else 0
        if used + calls > DAILY_MODEL_CALLS_PER_USER:
            raise https_fn.HttpsError(
                https_fn.FunctionsErrorCode.RESOURCE_EXHAUSTED,
                "You've reached today's processing limit. It resets at midnight UTC.",
            )
        txn.set(
            ref,
            {
                "model_calls": used + calls,
                "day": _today(),
                "updated_at": now_iso(),
            },
            merge=True,
        )

    _charge(transaction)


def is_pro(uid: str) -> bool:
    """Whether the user holds the anteroom_pro entitlement.

    Read from `users/{uid}/billing/entitlement`, which only the RevenueCat
    webhook writes. The client's own `isSubscribed` is a UI hint and is never
    trusted here -- that flag was the entire paywall for the PDF and the
    translation, and it lives in a process the user controls.
    """
    try:
        ref = db().document(f"users/{uid}/{ENTITLEMENT_DOC}")
        snap = ref.get()
    except Exception:
        logger.exception("Entitlement read failed for %s; treating as free", uid)
        return False
    if not snap.exists:
        return False
    data = snap.to_dict() or {}
    if not data.get("active"):
        return False
    expires = data.get("expires_at")
    if isinstance(expires, str) and expires:
        # Lifetime purchases have no expiry. A past expiry that the webhook
        # has not yet superseded should not keep the door open.
        return expires > now_iso()
    return True


def require_pro(uid: str, feature: str) -> None:
    if not is_pro(uid):
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.PERMISSION_DENIED,
            f"{feature} is an Anteroom Pro feature.",
        )


# ---------------------------------------------------------------------------
# Briefs
# ---------------------------------------------------------------------------
def load_owned_brief(uid: str, brief_id: str) -> tuple[Any, dict]:
    """Fetch a brief and prove the caller owns it.

    The Admin SDK ignores security rules, so without this check any signed-in
    user could pass any brief_id and have the server read another patient's
    pages out of Storage and hand back their extracted content.
    """
    if not isinstance(brief_id, str) or not brief_id.strip():
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INVALID_ARGUMENT, "brief_id is required."
        )
    ref = db().collection("briefs").document(brief_id)
    snap = ref.get()
    if not snap.exists:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.NOT_FOUND, "That brief no longer exists."
        )
    data = snap.to_dict() or {}
    if data.get("user_id") != uid:
        # Deliberately the same message as a missing brief: telling a caller
        # that a brief exists but belongs to someone else is itself a leak.
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.NOT_FOUND, "That brief no longer exists."
        )
    return ref, data


# ---------------------------------------------------------------------------
# Page images
# ---------------------------------------------------------------------------
def load_page_images(brief: dict) -> list[tuple[bytes, str]]:
    """Download a brief's pages and downscale them for the model.

    Reads `storage_path`, never the download URL: the path is the canonical
    reference, works with the Admin SDK without a token, and cannot be pointed
    at somebody else's object by a client that tampered with the document.
    """
    from PIL import Image  # local import: ~100ms, and only this path needs it

    photos = brief.get("photos") or []
    if not photos:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "Add at least one photo before generating a brief.",
        )

    out: list[tuple[bytes, str]] = []
    b = bucket()
    for photo in photos[:MAX_PAGES]:
        path = (photo or {}).get("storage_path") or ""
        if not path:
            continue
        blob = b.blob(path)
        try:
            raw = blob.download_as_bytes()
        except Exception:
            logger.exception("Page download failed: %s", path)
            continue
        out.append((_downscale_jpeg(Image, raw), "image/jpeg"))

    if not out:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "None of this brief's photos could be read. Try re-adding them.",
        )
    return out


def _downscale_jpeg(Image, raw: bytes) -> bytes:
    """Re-encode to a long-edge-capped RGB JPEG.

    The client already does this, so in the normal path it is close to a no-op.
    It is repeated here because the server must not depend on a client having
    behaved: a crafted upload could otherwise send a 50 MP image straight into
    a per-tile-billed model.
    """
    try:
        img = Image.open(io.BytesIO(raw))
        img.load()
    except Exception:
        logger.exception("Unreadable image; passing bytes through")
        return raw

    # EXIF orientation matters: a sideways page costs the model accuracy on
    # exactly the small print we care about.
    try:
        from PIL import ImageOps

        img = ImageOps.exif_transpose(img)
    except Exception:
        pass

    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")

    long_edge = max(img.size)
    if long_edge > IMAGE_LONG_EDGE:
        scale = IMAGE_LONG_EDGE / float(long_edge)
        img = img.resize(
            (max(1, int(img.width * scale)), max(1, int(img.height * scale))),
            Image.LANCZOS,
        )

    buf = io.BytesIO()
    img.convert("RGB").save(buf, format="JPEG", quality=IMAGE_JPEG_QUALITY, optimize=True)
    return buf.getvalue()


# ---------------------------------------------------------------------------
# Account deletion
# ---------------------------------------------------------------------------
def purge_user(uid: str) -> dict[str, int]:
    """Delete everything belonging to `uid`, except the Auth record itself.

    App Store Review Guideline 5.1.1(v) requires an app that creates accounts
    to delete them from inside the app. A client cannot do this job properly:
    it can delete its own Firestore documents, but Storage objects under
    another prefix, the entitlement document and the usage counters are all
    denied to it by rules -- by design, since those are exactly the documents a
    client must not be able to forge.

    The Auth record is deliberately *not* deleted here; the caller does that
    last. If this raises halfway through, the account still exists and the user
    can retry. Deleting the credential first would leave orphaned medical
    images behind a uid nobody can authenticate as -- unreachable by the
    person they belong to, and still on disk.
    """
    counts = {"briefs": 0, "files": 0, "documents": 0}
    client = db()

    for snap in client.collection("briefs").where("user_id", "==", uid).stream():
        snap.reference.delete()
        counts["briefs"] += 1

    # `users/{uid}` holds profiles, billing and usage as subcollections.
    # `collections()` is listed rather than hard-coded so a subcollection added
    # later cannot quietly survive a deletion request.
    user_ref = client.collection("users").document(uid)
    for collection in user_ref.collections():
        for snap in collection.stream():
            snap.reference.delete()
            counts["documents"] += 1
    user_ref.delete()
    counts["documents"] += 1

    # Page images. The prefix is the uid, so this cannot reach another account
    # even if a path were somehow malformed.
    for blob in bucket().list_blobs(prefix=f"users/{uid}/"):
        blob.delete()
        counts["files"] += 1

    logger.info("Purged account %s: %s", uid, counts)
    return counts
