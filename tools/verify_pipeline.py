#!/usr/bin/env python3
"""End-to-end exercise of the Anteroom pipeline against the live backend.

    python3 tools/verify_pipeline.py

Signs in as the test user, uploads two pages -- a clean referral and a
prescription whose Warfarin dose has been deliberately smudged illegible --
then drives every deployed callable exactly the way the Kotlin client does.

The point of the smudged page is that "nothing invented" has to be
demonstrated, not asserted: a model that guesses a plausible dose would pass a
test that only checks the readable rows. This asserts the unreadable dose is
*absent* from medications and *present* in flagged_items.

It also drives the RevenueCat webhook directly to grant and revoke the
entitlement, so the Pro gates on translation and the clean PDF are tested
from the server's point of view rather than the client's.
"""

from __future__ import annotations

import base64
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API_KEY = os.environ.get("ANTEROOM_WEB_API_KEY", "AIzaSyBhNhp_3oaFJXXHi7HEGE97cfVbxf6TYgw")
PROJECT = "anteroom-d2e72"
REGION = "europe-west1"
BUCKET = "anteroom-d2e72.firebasestorage.app"
FN = f"https://{REGION}-{PROJECT}.cloudfunctions.net"
FS = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents"
HERE = os.path.dirname(os.path.abspath(__file__))

TEST_EMAIL = os.environ.get("ANTEROOM_TEST_EMAIL", "test1@anteroom.dev")
TEST_PASSWORD = os.environ.get("ANTEROOM_TEST_PASSWORD", "password123")
WEBHOOK_SECRET = os.environ.get("REVENUECAT_WEBHOOK_SECRET", "")

results: list[tuple[str, bool, str]] = []


def check(name: str, ok: bool, detail: str = "") -> bool:
    results.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + (f" :: {detail}" if detail else ""))
    return ok


def _req(url, data=None, token=None, method=None, ctype="application/json", raw=None, extra=None):
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    req = urllib.request.Request(url, data=body, method=method or ("POST" if body else "GET"))
    if body is not None:
        req.add_header("Content-Type", ctype)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (extra or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            text = r.read().decode()
            try:
                return r.status, json.loads(text or "{}")
            except Exception:
                return r.status, text
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        try:
            return e.code, json.loads(text)
        except Exception:
            return e.code, text


def callable_fn(name, payload, token):
    """Invoke a callable the way the Firebase SDK does."""
    status, body = _req(f"{FN}/{name}", {"data": payload}, token)
    if status == 200 and isinstance(body, dict):
        return status, body.get("result"), None
    err = ""
    if isinstance(body, dict):
        err = (body.get("error") or {}).get("status") or (body.get("error") or {}).get("message") or str(body)
    else:
        err = str(body)[:200]
    return status, None, err


def sv(v):
    if isinstance(v, bool):  return {"booleanValue": v}
    if isinstance(v, int):   return {"integerValue": str(v)}
    if isinstance(v, str):   return {"stringValue": v}
    if v is None:            return {"nullValue": None}
    if isinstance(v, list):  return {"arrayValue": {"values": [sv(x) for x in v]}}
    if isinstance(v, dict):  return {"mapValue": {"fields": {k: sv(x) for k, x in v.items()}}}
    raise ValueError(v)


def fields(d):
    return {"fields": {k: sv(v) for k, v in d.items()}}


def main() -> int:
    status, body = _req(
        f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}",
        {"email": TEST_EMAIL, "password": TEST_PASSWORD, "returnSecureToken": True},
    )
    if status != 200:
        print("Cannot sign in:", body)
        return 1
    token, uid = body["idToken"], body["localId"]
    print(f"signed in as {uid}\n")

    # Start from a known-free account so the Pro gates mean something.
    _req(f"{FS}/users/{uid}/billing/entitlement", method="DELETE", token=token)

    now = time.strftime("%Y-%m-%dT%H:%M:%S.000Z", time.gmtime())
    brief_id = f"e2e_{int(time.time())}"
    draft = {
        "brief_id": brief_id, "user_id": uid, "profile_id": "self",
        "doc_type": "other", "doc_type_manual_override": False,
        "detected_doc_type": None, "detected_confidence": None,
        "status": "draft", "photos": [],
        "content": None, "content_translations": None,
        "source_language": None, "available_languages": [],
        "generated_at": None, "share_url_path": None,
        "share_token": None, "share_expires_at": None, "last_error": None,
        "created_at": now, "updated_at": now,
    }
    status, body = _req(f"{FS}/briefs/{brief_id}", draft and fields(draft), token, method="PATCH")
    if not check("brief created", status == 200, "" if status == 200 else str(body)[:160]):
        return 1

    photos = []
    for idx, (name, path) in enumerate(
        [("photo_e2epage1.jpg", "sample_page.jpg"), ("photo_e2epage2.jpg", "sample_page_illegible.jpg")]
    ):
        blob = open(os.path.join(HERE, path), "rb").read()
        obj = urllib.parse.quote(f"users/{uid}/briefs/{brief_id}/pages/{name}", safe="")
        st, resp = _req(
            f"https://firebasestorage.googleapis.com/v0/b/{BUCKET}/o?uploadType=media&name={obj}",
            token=token, raw=blob, ctype="image/jpeg",
        )
        if not check(f"page {idx+1} uploaded", st == 200, "" if st == 200 else str(resp)[:160]):
            return 1
        photos.append({
            "photo_id": f"e2epage{idx+1}", "filename": path, "content_type": "image/jpeg",
            "size": len(blob), "storage_path": f"users/{uid}/briefs/{brief_id}/pages/{name}",
            "url": "",
        })

    st, resp = _req(
        f"{FS}/briefs/{brief_id}?updateMask.fieldPaths=photos&updateMask.fieldPaths=updated_at",
        fields({"photos": photos, "updated_at": now}), token, method="PATCH",
    )
    check("pages attached to brief", st == 200, "" if st == 200 else str(resp)[:160])

    # ---- classifyDocType ----
    t = time.time()
    st, result, err = callable_fn("classifyDocType", {"brief_id": brief_id}, token)
    check("classifyDocType succeeds", st == 200, err or f"{time.time()-t:.1f}s")
    if result:
        check("classified as referral", result.get("doc_type") == "referral", json.dumps(result))

    # ---- generateBrief ----
    t = time.time()
    st, result, err = callable_fn("generateBrief", {"brief_id": brief_id, "source_language": "en"}, token)
    if not check("generateBrief succeeds", st == 200, err or f"{time.time()-t:.1f}s"):
        return 1
    check("both pages processed", result.get("pages") == 2, json.dumps(result))

    st, doc = _req(f"{FS}/briefs/{brief_id}", token=token)
    f = doc["fields"]
    check("status is complete", f["status"]["stringValue"] == "complete")
    check("generated_at is an ISO string", "stringValue" in f.get("generated_at", {}),
          list(f.get("generated_at", {})))

    def unwrap(v):
        k, x = next(iter(v.items()))
        if k == "mapValue":   return {kk: unwrap(vv) for kk, vv in (x.get("fields") or {}).items()}
        if k == "arrayValue": return [unwrap(i) for i in (x.get("values") or [])]
        if k == "nullValue":  return None
        if k == "integerValue": return int(x)
        if k == "booleanValue": return bool(x)
        return x

    content = unwrap(f["content"])
    meds = content.get("medications") or []
    flags = content.get("flagged_items") or []
    names = {m.get("name", "").lower() for m in meds}
    print("\n  medications: " + ", ".join(
        f"{m.get('name')} {m.get('dose') or '?'} {m.get('frequency') or ''}".strip() for m in meds))
    print("  flagged: " + (" | ".join(flags) if flags else "(none)") + "\n")

    check("medications extracted", len(meds) >= 5, f"{len(meds)} found")
    for drug in ("bisoprolol", "atorvastatin", "metformin", "levothyroxine"):
        check(f"{drug} extracted", drug in names)
    bis = next((m for m in meds if m.get("name", "").lower() == "bisoprolol"), {})
    check("bisoprolol dose is verbatim", bis.get("dose") == "2.5 mg", str(bis.get("dose")))
    check("allergy extracted", any("penicillin" in (a.get("substance") or "").lower()
                                   for a in content.get("allergies") or []))

    # The core safety claim.
    warf = next((m for m in meds if "warfarin" in (m.get("name", "").lower())), None)
    check("illegible warfarin dose was NOT invented",
          warf is None or not warf.get("dose"), f"got dose={warf and warf.get('dose')!r}")
    check("at least one flagged item", len(flags) >= 1, f"{len(flags)} flags")
    check("a flag mentions the unreadable dose",
          any("warfarin" in x.lower() or "dose" in x.lower() or "illegible" in x.lower()
              or "unreadable" in x.lower() for x in flags),
          " | ".join(flags)[:200])

    # ---- Pro gates, as a free user ----
    st, result, err = callable_fn("translateBrief", {"brief_id": brief_id, "target_language": "es"}, token)
    check("translateBrief denied for free user", st != 200 and "PERMISSION_DENIED" in (err or ""), err or "allowed!")

    st, result, err = callable_fn("renderBriefPdf", {"brief_id": brief_id}, token)
    check("renderBriefPdf works for free user", st == 200, err or "")
    if result:
        check("free PDF is watermarked", result.get("watermarked") is True, str(result.get("watermarked")))
        pdf = base64.b64decode(result["pdf_base64"])
        check("free PDF is a real PDF", pdf[:4] == b"%PDF", str(pdf[:8]))
        open("/tmp/anteroom_e2e_free.pdf", "wb").write(pdf)

    # ---- grant Pro through the webhook ----
    if not WEBHOOK_SECRET:
        print("\n  (REVENUECAT_WEBHOOK_SECRET not set - skipping webhook and Pro-path checks)")
    else:
        st, body = _req(f"{FN}/revenuecatWebhook", {"event": {"type": "TEST"}},
                        extra={"Authorization": "wrong"})
        check("webhook rejects a bad secret", st == 401, str(st))

        st, body = _req(
            f"{FN}/revenuecatWebhook",
            {"event": {"type": "INITIAL_PURCHASE", "app_user_id": uid,
                       "entitlement_ids": ["anteroom_pro"], "product_id": "anteroom_pro_monthly",
                       "store": "APP_STORE", "expiration_at_ms": int((time.time() + 86400) * 1000)}},
            extra={"Authorization": WEBHOOK_SECRET},
        )
        check("webhook grants entitlement", st == 200, str(body)[:120])

        st, ent = _req(f"{FS}/users/{uid}/billing/entitlement", token=token)
        check("entitlement readable by owner", st == 200 and ent["fields"]["active"]["booleanValue"] is True,
              str(ent)[:120] if st != 200 else "")

        st, result, err = callable_fn("translateBrief", {"brief_id": brief_id, "target_language": "es"}, token)
        check("translateBrief allowed for Pro", st == 200, err or "")

        st, doc = _req(f"{FS}/briefs/{brief_id}", token=token)
        tr = unwrap(doc["fields"]["content_translations"]).get("es", {})
        tr_meds = tr.get("medications") or []
        tr_names = {m.get("name", "").lower() for m in tr_meds}
        check("translation preserved drug names", "bisoprolol" in tr_names, ", ".join(sorted(tr_names)))
        tr_bis = next((m for m in tr_meds if m.get("name", "").lower() == "bisoprolol"), {})
        check("translation preserved the dose", tr_bis.get("dose") == "2.5 mg", str(tr_bis.get("dose")))
        # Frequency is localised by a lookup table, not the model -- see
        # localize_frequency. Drug name and dose above must be untouched.
        tr_freqs = {m.get("name", "").lower(): m.get("frequency") for m in tr_meds}
        check("frequency localised to Spanish",
              tr_freqs.get("bisoprolol") == "1 vez al día (c/24h)", str(tr_freqs.get("bisoprolol")))
        check("twice-daily localised", tr_freqs.get("metformin") == "cada 12h",
              str(tr_freqs.get("metformin")))
        check("at-night localised", tr_freqs.get("atorvastatin") == "por la noche",
              str(tr_freqs.get("atorvastatin")))
        check("as-directed localised", tr_freqs.get("warfarin") in ("según pauta", None),
              str(tr_freqs.get("warfarin")))
        check("English source frequency untouched in source content",
              next((m.get("frequency") for m in meds if m.get("name","").lower()=="bisoprolol"), None)
              == "once daily")

        check("translation rendered the flags into Spanish",
              bool(tr.get("flagged_items")) and tr.get("flagged_items") != flags,
              " | ".join(tr.get("flagged_items") or [])[:160])

        st, result, err = callable_fn("renderBriefPdf", {"brief_id": brief_id, "language": "es"}, token)
        check("Pro PDF renders in Spanish", st == 200, err or "")
        if result:
            check("Pro PDF is NOT watermarked", result.get("watermarked") is False, str(result.get("watermarked")))
            open("/tmp/anteroom_e2e_pro_es.pdf", "wb").write(base64.b64decode(result["pdf_base64"]))

        # ---- revoke ----
        st, body = _req(
            f"{FN}/revenuecatWebhook",
            {"event": {"type": "EXPIRATION", "app_user_id": uid, "entitlement_ids": ["anteroom_pro"]}},
            extra={"Authorization": WEBHOOK_SECRET},
        )
        check("webhook revokes entitlement", st == 200, str(body)[:120])
        st, result, err = callable_fn("renderBriefPdf", {"brief_id": brief_id}, token)
        check("PDF is watermarked again after expiry",
              st == 200 and result.get("watermarked") is True, err or str(result and result.get("watermarked")))

    # ---- cross-user isolation on the callables ----
    stamp = str(int(time.time()))
    st, other = _req(
        f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={API_KEY}",
        {"email": f"pipecheck+{stamp}@anteroom.dev", "password": "password123", "returnSecureToken": True},
    )
    if st == 200:
        st, result, err = callable_fn("generateBrief", {"brief_id": brief_id, "source_language": "en"},
                                      other["idToken"])
        check("another user cannot generate on this brief", st != 200 and "NOT_FOUND" in (err or ""), err or "allowed!")
        st, result, err = callable_fn("renderBriefPdf", {"brief_id": brief_id}, other["idToken"])
        check("another user cannot export this brief", st != 200 and "NOT_FOUND" in (err or ""), err or "allowed!")

    st, result, err = callable_fn("generateBrief", {"brief_id": brief_id}, None)
    check("unauthenticated call rejected", st != 200, err or "allowed!")

    print()
    bad = [n for n, ok, _ in results if not ok]
    print(f"{len(results) - len(bad)}/{len(results)} passed")
    if bad:
        print("FAILED: " + "; ".join(bad))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
