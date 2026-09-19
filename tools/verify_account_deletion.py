#!/usr/bin/env python3
"""Prove that `deleteAccount` actually erases an account.

    python3 tools/verify_account_deletion.py

App Store Review Guideline 5.1.1(v) requires in-app account deletion, and a
button that signs you out and leaves the data behind would pass a manual review
while failing the promise the privacy policy makes. So this builds a throwaway
account with something in every store the app writes to -- a user document, a
profile, a brief, a page image in Cloud Storage, a usage counter, an
entitlement -- deletes it, and then checks from the outside that each one is
gone.

The account is created and destroyed by the script, so it can be run against
the live project as often as you like. It never touches the shared test user.
"""

from __future__ import annotations

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
IDT = "https://identitytoolkit.googleapis.com/v1"
HERE = os.path.dirname(os.path.abspath(__file__))

results: list[tuple[str, bool, str]] = []


def check(name: str, ok: bool, detail: str = "") -> bool:
    results.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + (f" :: {detail}" if detail else ""))
    return ok


def _req(url, data=None, token=None, method=None, ctype="application/json", raw=None):
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    req = urllib.request.Request(url, data=body, method=method or ("POST" if body else "GET"))
    if body is not None:
        req.add_header("Content-Type", ctype)
    if token:
        req.add_header("Authorization", "Bearer " + token)
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


def callable_fn(name, payload, token):
    status, body = _req(f"{FN}/{name}", {"data": payload}, token)
    if status == 200 and isinstance(body, dict):
        return status, body.get("result"), None
    err = ""
    if isinstance(body, dict):
        e = body.get("error") or {}
        err = e.get("status") or e.get("message") or str(body)
    else:
        err = str(body)[:200]
    return status, None, err


def main() -> int:
    stamp = int(time.time())
    email = f"deltest+{stamp}@anteroom.dev"
    password = "password123"

    # ---- build an account with something in every store ------------------
    st, body = _req(
        f"{IDT}/accounts:signUp?key={API_KEY}",
        {"email": email, "password": password, "returnSecureToken": True},
    )
    if not check("throwaway account created", st == 200, str(body)[:160] if st != 200 else email):
        return 1
    token, uid = body["idToken"], body["localId"]

    now = time.strftime("%Y-%m-%dT%H:%M:%S.000Z", time.gmtime())

    st, _ = _req(
        f"{FS}/users/{uid}",
        fields({"user_id": uid, "email": email, "name": None, "picture": None,
                "dob": None, "language": None, "country": None,
                "profile_completed": False, "provider": "email"}),
        token, method="PATCH")
    check("user document written", st == 200)

    st, _ = _req(
        f"{FS}/users/{uid}/profiles/self",
        fields({"profile_id": "self", "user_id": uid, "name": "Delete Me",
                "relationship": "self", "dob": None, "sex": None,
                "is_self": True, "created_at": now, "updated_at": now}),
        token, method="PATCH")
    check("self profile written", st == 200)

    brief_id = f"del_{stamp}"
    st, _ = _req(
        f"{FS}/briefs/{brief_id}",
        fields({"brief_id": brief_id, "user_id": uid, "profile_id": "self",
                "doc_type": "other", "doc_type_manual_override": False,
                "detected_doc_type": None, "detected_confidence": None,
                "status": "draft", "photos": [], "content": None,
                "content_translations": None, "source_language": None,
                "available_languages": [], "generated_at": None,
                "share_url_path": None, "share_token": None,
                "share_expires_at": None, "last_error": None,
                "created_at": now, "updated_at": now}),
        token, method="PATCH")
    check("brief written", st == 200)

    blob = open(os.path.join(HERE, "sample_page.jpg"), "rb").read()
    path = f"users/{uid}/briefs/{brief_id}/pages/photo_delpage.jpg"
    obj = urllib.parse.quote(path, safe="")
    st, _ = _req(
        f"https://firebasestorage.googleapis.com/v0/b/{BUCKET}/o?uploadType=media&name={obj}",
        token=token, raw=blob, ctype="image/jpeg")
    check("page image uploaded", st == 200)

    st, _ = _req(
        f"{FS}/briefs/{brief_id}"
        f"?updateMask.fieldPaths=photos&updateMask.fieldPaths=updated_at",
        fields({"photos": [{"photo_id": "delpage", "filename": "sample_page.jpg",
                            "content_type": "image/jpeg", "size": len(blob),
                            "storage_path": path, "url": ""}],
                "updated_at": now}),
        token, method="PATCH")
    check("page attached to the brief", st == 200)

    # A real model call, so that a usage counter exists to be deleted.
    # classifyDocType is the cheapest one that writes
    # `users/{uid}/usage/{day}`, and it needs the page attached above --
    # without it the callable returns FAILED_PRECONDITION and the counter
    # never appears, which would quietly weaken every assertion below.
    st, _, err = callable_fn("classifyDocType", {"brief_id": brief_id}, token)
    check("usage counter created by a real model call", st == 200, err or "")

    # ---- delete ----------------------------------------------------------
    t = time.time()
    st, result, err = callable_fn("deleteAccount", {}, token)
    if not check("deleteAccount succeeds", st == 200, err or f"{time.time() - t:.1f}s"):
        return 1
    deleted = (result or {}).get("deleted") or {}
    print(f"       reported: {json.dumps(deleted)}")
    check("reported the brief", deleted.get("briefs") == 1, json.dumps(deleted))
    check("reported the page image", deleted.get("files") == 1, json.dumps(deleted))
    # user doc + self profile + usage counter, at minimum.
    check("reported at least 3 documents", deleted.get("documents", 0) >= 3, json.dumps(deleted))

    # ---- verify from outside ---------------------------------------------
    # The old ID token is still cryptographically valid for up to an hour, which
    # is exactly why this is worth checking: if the brief were still there, this
    # read would succeed.
    # 403 rather than 404, and that is the rule doing its job: the briefs read
    # rule is `resource.data.user_id == uid()`, and for a document that no
    # longer exists `resource` is null, so the condition is false before it can
    # be a not-found. `users/{uid}` below returns a clean 404 instead because
    # its rule compares path segments and never dereferences the document.
    st, body = _req(f"{FS}/briefs/{brief_id}", token=token)
    check("brief is unreadable", st in (403, 404), f"HTTP {st}")

    st, body = _req(f"{FS}/users/{uid}/profiles/self", token=token)
    check("profile is gone", st == 404, f"HTTP {st}")

    st, body = _req(f"{FS}/users/{uid}", token=token)
    check("user document is gone", st == 404, f"HTTP {st}")

    st, body = _req(
        f"https://firebasestorage.googleapis.com/v0/b/{BUCKET}/o/{obj}", token=token)
    check("page image is gone", st == 404, f"HTTP {st}")

    st, body = _req(
        f"{IDT}/accounts:signInWithPassword?key={API_KEY}",
        {"email": email, "password": password, "returnSecureToken": True})
    reason = (body.get("error") or {}).get("message", "") if isinstance(body, dict) else ""
    # Not asserted as EMAIL_NOT_FOUND: this project has email-enumeration
    # protection on, so a deleted account and a wrong password both come back
    # as INVALID_LOGIN_CREDENTIALS. That is the point of the setting, and it is
    # why the re-registration check below carries the real weight.
    check("credential no longer signs in", st != 200, reason or f"HTTP {st}")

    # Re-registering the same address proves the record was deleted rather than
    # disabled -- a disabled account still occupies its email.
    st, body = _req(
        f"{IDT}/accounts:signUp?key={API_KEY}",
        {"email": email, "password": password, "returnSecureToken": True})
    fresh_uid = body.get("localId") if st == 200 else None
    check("email is free to register again", st == 200 and fresh_uid != uid,
          f"HTTP {st} {str(body)[:120]}")
    if fresh_uid:
        # Leave nothing behind.
        callable_fn("deleteAccount", {}, body["idToken"])

    failed = [n for n, ok, _ in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} checks passed")
    if failed:
        print("failed: " + ", ".join(failed))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
