"""Anteroom Cloud Functions entrypoint.

Firebase only reads this module, so every deployed function must be imported
or defined here. Keep it thin: logic lives in `anteroom/`.

`initialize_app()` must run before importing anything that touches
`firestore.client()` at module scope, hence the import ordering. The storage
bucket is passed explicitly because this project uses the newer
`.firebasestorage.app` domain while the Admin SDK still defaults to the legacy
`<project>.appspot.com`, which does not exist here.

Function names are camelCase on purpose. firebase-functions deploys a Python
function under its own identifier, and the Kotlin client resolves
`httpsCallable("generateBrief")` -- a snake_case name here would be a
NOT_FOUND at the call site with nothing to explain it.
"""

from __future__ import annotations

import base64
import hmac
import json
import logging

from firebase_admin import initialize_app
from firebase_functions import https_fn, options
from firebase_functions.params import SecretParam

from anteroom.config import (
    ENTITLEMENT_ID,
    MAX_PAGES,
    REGION,
    STORAGE_BUCKET,
    SUPPORTED_LANGUAGES,
)

initialize_app(options={"storageBucket": STORAGE_BUCKET})

from anteroom import store, vertex  # noqa: E402  (must follow initialize_app)
from anteroom.pdf import render_brief_pdf  # noqa: E402

logger = logging.getLogger("anteroom.main")

# Shared secret with the RevenueCat dashboard's webhook Authorization header.
# Kept in Secret Manager rather than the repo or an env var so it is not in
# git history and can be rotated without a code change.
REVENUECAT_WEBHOOK_SECRET = SecretParam("REVENUECAT_WEBHOOK_SECRET")


def _language(value, default: str = "en") -> str:
    lang = str(value or default).lower().strip()
    return lang if lang in SUPPORTED_LANGUAGES else default


# ---------------------------------------------------------------------------
# classifyDocType
# ---------------------------------------------------------------------------
@https_fn.on_call(
    region=REGION,
    memory=options.MemoryOption.MB_512,
    timeout_sec=90,
    max_instances=10,
)
def classifyDocType(req: https_fn.CallableRequest) -> dict:
    """Triage the first page so the draft screen can pre-select a doc type.

    Only the first page is classified. A brief is one patient's paperwork, the
    label describes the packet rather than each sheet, and classifying six
    pages to fill one enum would be five wasted model calls.
    """
    uid = store.require_uid(req)
    brief_id = (req.data or {}).get("brief_id")
    ref, brief = store.load_owned_brief(uid, brief_id)

    store.require_ai_enabled()
    pages = store.load_page_images(brief)
    store.charge_quota(uid)

    try:
        result = vertex.classify_page(pages[0][0], pages[0][1])
    except Exception as exc:
        logger.exception("classifyDocType failed for %s", brief_id)
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INTERNAL,
            "Couldn't work out what kind of document this is.",
        ) from exc

    update = {
        "detected_doc_type": result["doc_type"],
        "detected_confidence": result["confidence"],
        "updated_at": store.now_iso(),
    }
    # Never overwrite a choice the user made by hand.
    if not brief.get("doc_type_manual_override"):
        update["doc_type"] = result["doc_type"]
    ref.update(update)

    return {"doc_type": result["doc_type"], "confidence": result["confidence"]}


# ---------------------------------------------------------------------------
# generateBrief
# ---------------------------------------------------------------------------
@https_fn.on_call(
    region=REGION,
    memory=options.MemoryOption.GB_1,
    timeout_sec=540,
    max_instances=10,
)
def generateBrief(req: https_fn.CallableRequest) -> dict:
    """Extract structured content from a brief's pages.

    `source_language` records what language the documents are in and which
    labels the PDF uses. It deliberately does not change what the model
    outputs: the extraction prompt forbids translating, because copying
    dosages verbatim is the safety guarantee. Getting a Spanish brief is a
    separate, explicit translate step.
    """
    uid = store.require_uid(req)
    data = req.data or {}
    ref, brief = store.load_owned_brief(uid, data.get("brief_id"))
    source_language = _language(data.get("source_language"))

    store.require_ai_enabled()
    pages = store.load_page_images(brief)
    store.charge_quota(uid)

    # Claim the brief before the long call, so a client that reconnects
    # mid-extraction sees work in progress instead of a draft it might
    # resubmit.
    ref.update({"status": "generating", "last_error": None, "updated_at": store.now_iso()})

    try:
        content = vertex.extract_brief(pages)
    except Exception as exc:
        logger.exception("generateBrief failed for %s", brief.get("brief_id"))
        ref.update(
            {
                "status": "failed",
                "last_error": "Extraction didn't finish. Your photos are safe - try again.",
                "updated_at": store.now_iso(),
            }
        )
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INTERNAL,
            "Couldn't read these pages. Your photos are safe - try again.",
        ) from exc

    generated_at = store.now_iso()
    ref.update(
        {
            "content": content,
            "status": "complete",
            "generated_at": generated_at,
            "source_language": source_language,
            "available_languages": [source_language],
            "last_error": None,
            "updated_at": generated_at,
        }
    )

    return {
        "brief_id": brief.get("brief_id"),
        "pages": len(pages),
        "medications": len(content["medications"]),
        "flagged_items": len(content["flagged_items"]),
    }


# ---------------------------------------------------------------------------
# translateBrief
# ---------------------------------------------------------------------------
@https_fn.on_call(
    region=REGION,
    memory=options.MemoryOption.MB_512,
    timeout_sec=180,
    max_instances=10,
)
def translateBrief(req: https_fn.CallableRequest) -> dict:
    """Translate an already-extracted brief. Pro only, enforced here.

    The client also gates this behind `isSubscribed`, but that flag lives in a
    process the user controls. This check is the one that counts.
    """
    uid = store.require_uid(req)
    data = req.data or {}
    ref, brief = store.load_owned_brief(uid, data.get("brief_id"))
    target = _language(data.get("target_language"), default="es")

    store.require_pro(uid, "Translation")

    content = brief.get("content")
    if not content:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "Generate the brief before translating it.",
        )

    source_language = _language(brief.get("source_language"))
    if target == source_language:
        return {"language": target, "cached": True}

    existing = brief.get("content_translations") or {}
    if target in existing:
        return {"language": target, "cached": True}

    store.require_ai_enabled()
    store.charge_quota(uid)

    try:
        translated = vertex.translate_content(content, target)
    except Exception as exc:
        logger.exception("translateBrief failed for %s", brief.get("brief_id"))
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.INTERNAL, "Couldn't translate this brief."
        ) from exc

    available = list(brief.get("available_languages") or [source_language])
    if target not in available:
        available.append(target)

    ref.update(
        {
            f"content_translations.{target}": translated,
            "available_languages": available,
            "updated_at": store.now_iso(),
        }
    )
    return {"language": target, "cached": False}


# ---------------------------------------------------------------------------
# renderBriefPdf
# ---------------------------------------------------------------------------
@https_fn.on_call(
    region=REGION,
    memory=options.MemoryOption.MB_512,
    timeout_sec=120,
    max_instances=10,
)
def renderBriefPdf(req: https_fn.CallableRequest) -> dict:
    """Render the brief as a PDF, watermarked unless the caller is Pro.

    There is no `watermarked` argument, by design. The old build decided
    watermarking on the client, which meant the clean export -- one of the two
    things Pro sells -- was a boolean anyone could flip. The server derives it
    from the entitlement and the caller cannot express a preference.

    Returns the bytes inline rather than a Storage URL: a one-page A4 brief is
    a few tens of kilobytes, far inside the callable response limit, and it
    avoids minting a signed URL to a document full of medical data.
    """
    uid = store.require_uid(req)
    data = req.data or {}
    _ref, brief = store.load_owned_brief(uid, data.get("brief_id"))

    content = brief.get("content")
    if not content:
        raise https_fn.HttpsError(
            https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
            "Generate the brief before exporting it.",
        )

    source_language = _language(brief.get("source_language"))
    language = _language(data.get("language"), default=source_language)
    if language != source_language:
        translated = (brief.get("content_translations") or {}).get(language)
        if not translated:
            raise https_fn.HttpsError(
                https_fn.FunctionsErrorCode.FAILED_PRECONDITION,
                "Translate the brief into that language first.",
            )
        content = translated

    watermarked = not store.is_pro(uid)
    pdf = render_brief_pdf(
        content,
        generated_at=(brief.get("generated_at") or "")[:10],
        watermarked=watermarked,
        brief_id=str(brief.get("brief_id") or "")[:12],
        language=language,
    )

    return {
        "filename": f"anteroom-brief-{str(brief.get('brief_id') or 'export')[:8]}.pdf",
        "watermarked": watermarked,
        "language": language,
        "pdf_base64": base64.b64encode(pdf).decode("ascii"),
    }


# ---------------------------------------------------------------------------
# RevenueCat webhook
# ---------------------------------------------------------------------------
# Events that mean the entitlement is live right now. CANCELLATION is
# deliberately absent from both lists: it means auto-renew was switched off,
# not that access ended, and the user keeps Pro until `expires_at` passes.
_GRANTING = {
    "INITIAL_PURCHASE",
    "RENEWAL",
    "UNCANCELLATION",
    "NON_RENEWING_PURCHASE",
    "PRODUCT_CHANGE",
    "SUBSCRIPTION_EXTENDED",
    "TEMPORARY_ENTITLEMENT_GRANT",
}
_REVOKING = {"EXPIRATION", "SUBSCRIPTION_PAUSED"}

# Two event types are deliberately in neither set.
#
# CANCELLATION means auto-renew was switched off, not that access ended: the
# user keeps Pro until `expires_at` passes, and revoking here would take the
# feature away from someone who has paid for the rest of the month. A refund
# arrives as EXPIRATION with an expiration_reason, which _REVOKING catches.
#
# TRANSFER carries `transferred_from` / `transferred_to` rather than a single
# `app_user_id`, so there is no one uid to write. It falls through to the
# missing-uid branch and is skipped rather than guessed at. Worth revisiting
# if account transfers ever become a real flow.


@https_fn.on_request(
    region=REGION,
    memory=options.MemoryOption.MB_256,
    timeout_sec=30,
    max_instances=5,
    secrets=[REVENUECAT_WEBHOOK_SECRET],
)
def revenuecatWebhook(req: https_fn.Request) -> https_fn.Response:
    """Write `users/{uid}/billing/entitlement` from RevenueCat events.

    This is the only writer of that document -- security rules deny every
    client -- which is what makes a server-side Pro check meaningful. The
    RevenueCat SDK still drives the in-app UI; this exists so the *server* can
    answer "is this user Pro" without asking the client.

    `app_user_id` is the Firebase uid because AnteroomApp passes it to
    `revenueCatService.initialize(key, user.user_id)`.
    """
    expected = REVENUECAT_WEBHOOK_SECRET.value
    presented = req.headers.get("Authorization") or ""
    # compare_digest rather than `!=`: this is a secret comparison, and the
    # early-exit of a normal string compare leaks its prefix through timing.
    if not expected or not hmac.compare_digest(presented, expected):
        # 401 with no detail: an attacker learns nothing about the header shape.
        return https_fn.Response("unauthorized", status=401)

    try:
        payload = req.get_json(silent=True) or {}
        event = payload.get("event") or {}
    except Exception:
        return https_fn.Response("bad request", status=400)

    event_type = str(event.get("type") or "").upper()
    uid = event.get("app_user_id")

    if event_type == "TEST":
        logger.info("RevenueCat TEST event received")
        return https_fn.Response(json.dumps({"ok": True, "test": True}), status=200)

    if not uid or not isinstance(uid, str):
        return https_fn.Response(json.dumps({"ok": True, "skipped": "no app_user_id"}), status=200)

    entitlements = event.get("entitlement_ids") or []
    if isinstance(entitlements, str):
        entitlements = [entitlements]
    if ENTITLEMENT_ID not in entitlements and event.get("entitlement_id") != ENTITLEMENT_ID:
        return https_fn.Response(
            json.dumps({"ok": True, "skipped": "other entitlement"}), status=200
        )

    expires_ms = event.get("expiration_at_ms")
    expires_at = None
    if isinstance(expires_ms, (int, float)) and expires_ms > 0:
        import datetime as dt

        moment = dt.datetime.fromtimestamp(expires_ms / 1000, dt.timezone.utc)
        expires_at = f"{moment:%Y-%m-%dT%H:%M:%S}.{moment.microsecond // 1000:03d}Z"

    if event_type in _REVOKING:
        active = False
    elif event_type in _GRANTING:
        active = expires_at is None or expires_at > store.now_iso()
    else:
        # An event type we do not model yet. Recording it without changing
        # `active` is safer than guessing in either direction.
        active = None

    document = {
        "entitlement_id": ENTITLEMENT_ID,
        "last_event_type": event_type,
        "product_id": event.get("product_id"),
        "store": event.get("store"),
        "expires_at": expires_at,
        "updated_at": store.now_iso(),
    }
    if active is not None:
        document["active"] = active

    store.db().document(f"users/{uid}/billing/entitlement").set(document, merge=True)
    logger.info("RevenueCat %s for %s -> active=%s", event_type, uid, active)
    return https_fn.Response(json.dumps({"ok": True}), status=200, mimetype="application/json")


# ---------------------------------------------------------------------------
# ping
# ---------------------------------------------------------------------------
@https_fn.on_call(
    region=REGION,
    memory=options.MemoryOption.MB_256,
    timeout_sec=30,
    max_instances=2,
)
def ping(req: https_fn.CallableRequest) -> dict:
    """Deployment canary. Proves runtime, region and auth context in isolation."""
    return {
        "ok": True,
        "region": REGION,
        "max_pages": MAX_PAGES,
        "authenticated": req.auth is not None,
        "uid": req.auth.uid if req.auth else None,
        "pro": store.is_pro(req.auth.uid) if req.auth else False,
    }
