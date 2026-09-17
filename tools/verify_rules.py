#!/usr/bin/env python3
"""
Exercises firestore.rules and storage.rules against the live project.

Run after any rules change:

    python3 tools/verify_rules.py

Every payload is the exact shape the Kotlin client sends -- in particular the
brief document arrives with every nullable field present as an explicit null,
because kotlinx-serialization encodes defaults. An earlier version of the
create rule required those keys to be *absent*, which no real client could
ever satisfy; only a test built from the real payload catches that.

This deliberately talks to the deployed project rather than the emulator, so
what it proves is what is actually enforced in production. It creates a
throwaway second account each run to test cross-user denial, and leaves a
couple of rulescheck_* briefs behind under the test user.

The API key below is the Firebase Web API key, which is public by design --
it identifies the project and grants nothing on its own. Security rules are
what stand between it and the data, which is the point of this script.
"""

import json, os, urllib.request, urllib.error, urllib.parse, sys, time

API_KEY = os.environ.get("ANTEROOM_WEB_API_KEY", "AIzaSyBhNhp_3oaFJXXHi7HEGE97cfVbxf6TYgw")
PROJECT = "anteroom-d2e72"
BUCKET  = "anteroom-d2e72.firebasestorage.app"
FS = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents"

def post(url, payload, token=None, raw=None, ctype="application/json"):
    data = raw if raw is not None else json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", ctype)
    if token: req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try: body = json.loads(body)
        except Exception: pass
        return e.code, body

def patch(url, payload, token):
    req = urllib.request.Request(url, data=json.dumps(payload).encode(), method="PATCH")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try: body = json.loads(body)
        except Exception: pass
        return e.code, body

def get(url, token):
    req = urllib.request.Request(url)
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try: body = json.loads(body)
        except Exception: pass
        return e.code, body

def signin(email, password):
    url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}"
    st, body = post(url, {"email": email, "password": password, "returnSecureToken": True})
    if st != 200:
        return None, None, body
    return body["idToken"], body["localId"], None

def signup(email, password):
    url = f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={API_KEY}"
    st, body = post(url, {"email": email, "password": password, "returnSecureToken": True})
    if st != 200:
        return None, None, body
    return body["idToken"], body["localId"], None

def err(body):
    if isinstance(body, dict):
        e = body.get("error", {})
        return e.get("message") or e.get("status") or str(body)[:120]
    return str(body)[:120]

results = []
def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + ((" :: " + str(detail)[:140]) if detail else ""))

# --- users -------------------------------------------------------------
TEST_EMAIL = os.environ.get("ANTEROOM_TEST_EMAIL", "test1@anteroom.dev")
TEST_PASSWORD = os.environ.get("ANTEROOM_TEST_PASSWORD", "password123")

tok, uid, e = signin(TEST_EMAIL, TEST_PASSWORD)
if not tok:
    tok, uid, e = signup(TEST_EMAIL, TEST_PASSWORD)
if not tok:
    print("CANNOT AUTHENTICATE USER A:", err(e)); sys.exit(1)
print("user A uid:", uid)

stamp = str(int(time.time()))
tokB, uidB, eB = signup(f"rulescheck+{stamp}@anteroom.dev", "password123")
if not tokB:
    print("CANNOT CREATE USER B:", err(eB)); sys.exit(1)
print("user B uid:", uidB)

def sv(v):
    if isinstance(v, bool):  return {"booleanValue": v}
    if isinstance(v, int):   return {"integerValue": str(v)}
    if isinstance(v, str):   return {"stringValue": v}
    if v is None:            return {"nullValue": None}
    if isinstance(v, list):  return {"arrayValue": {"values": [sv(x) for x in v]}}
    if isinstance(v, dict):  return {"mapValue": {"fields": {k: sv(x) for k, x in v.items()}}}
    raise ValueError(v)

def fields(d): return {"fields": {k: sv(v) for k, v in d.items()}}

NOW = "2026-09-17T10:20:00.000Z"

# 1. self profile at the pinned doc id
st, body = patch(f"{FS}/users/{uid}/profiles/self", fields({
    "profile_id": "self", "user_id": uid, "name": "Test User",
    "relationship": "self", "is_self": True, "dob": None, "sex": None,
    "created_at": NOW, "updated_at": NOW,
}), tok)
check("self profile write allowed", st == 200, err(body) if st != 200 else "")

# 2. a second profile must NOT claim is_self
st, body = patch(f"{FS}/users/{uid}/profiles/imposter", fields({
    "profile_id": "imposter", "user_id": uid, "name": "Imposter",
    "relationship": "self", "is_self": True,
    "created_at": NOW, "updated_at": NOW,
}), tok)
check("second self profile denied", st == 403, err(body))

# 3. create a brief -- exactly the shape the Kotlin client serialises,
#    i.e. every nullable field present as an explicit null.
BRIEF = "rulescheck_" + str(int(time.time()))
DRAFT = {
    "brief_id": BRIEF, "user_id": uid, "profile_id": "self",
    "doc_type": "referral", "doc_type_manual_override": False,
    "detected_doc_type": None, "detected_confidence": None,
    "status": "draft", "photos": [],
    "content": None, "content_translations": None,
    "source_language": None, "available_languages": [],
    "generated_at": None, "share_url_path": None,
    "share_token": None, "share_expires_at": None, "last_error": None,
    "created_at": NOW, "updated_at": NOW,
}
st, body = patch(f"{FS}/briefs/{BRIEF}", fields(DRAFT), tok)
check("brief create allowed (full client payload)", st == 200, err(body) if st != 200 else "")

# 3b. adding a photo is a full-document set() in the client
WITH_PHOTO = dict(DRAFT)
WITH_PHOTO["photos"] = [{
    "photo_id": "p1", "filename": "page.jpg", "content_type": "image/jpeg",
    "size": 253843, "storage_path": f"users/{uid}/briefs/{BRIEF}/pages/photo_p1.jpg",
    "url": "https://example.invalid/p1",
}]
WITH_PHOTO["updated_at"] = "2026-09-17T10:21:00.000Z"
st, body = patch(f"{FS}/briefs/{BRIEF}", fields(WITH_PHOTO), tok)
check("add photo via full set() allowed", st == 200, err(body) if st != 200 else "")

# 3c. a seventh page must be refused (MAX_PAGES = 6)
TOO_MANY = dict(WITH_PHOTO)
TOO_MANY["photos"] = [dict(WITH_PHOTO["photos"][0], photo_id=f"p{i}") for i in range(7)]
st, body = patch(f"{FS}/briefs/{BRIEF}", fields(TOO_MANY), tok)
check("seventh page denied", st == 403, err(body))

# 3d. the merge write the client now issues (updateMask = photos, updated_at)
MERGE = "?updateMask.fieldPaths=photos&updateMask.fieldPaths=updated_at"
st, body = patch(f"{FS}/briefs/{BRIEF}{MERGE}", fields({
    "photos": WITH_PHOTO["photos"], "updated_at": "2026-09-17T10:22:00.000Z",
}), tok)
check("merge write of photos allowed", st == 200, err(body) if st != 200 else "")

DT = "?updateMask.fieldPaths=doc_type&updateMask.fieldPaths=doc_type_manual_override&updateMask.fieldPaths=updated_at"
st, body = patch(f"{FS}/briefs/{BRIEF}{DT}", fields({
    "doc_type": "med_list", "doc_type_manual_override": True,
    "updated_at": "2026-09-17T10:23:00.000Z",
}), tok)
check("merge write of doc_type allowed", st == 200, err(body) if st != 200 else "")

# 4. clinical fields are server-only
st, body = patch(f"{FS}/briefs/{BRIEF}?updateMask.fieldPaths=status", fields({"status": "complete"}), tok)
check("client write to status denied", st == 403, err(body))

st, body = patch(f"{FS}/briefs/{BRIEF}?updateMask.fieldPaths=content", fields({
    "content": {"summary": "invented"}}), tok)
check("client write to content denied", st == 403, err(body))

# 5. cross-user read
st, body = get(f"{FS}/briefs/{BRIEF}", tokB)
check("cross-user brief read denied", st == 403, err(body))

st, body = get(f"{FS}/briefs/{BRIEF}", tok)
check("owner brief read allowed", st == 200, err(body) if st != 200 else "")

# 6. unconstrained list query
q = {"structuredQuery": {"from": [{"collectionId": "briefs"}], "limit": 1}}
st, body = post(f"{FS}:runQuery", q, tok)
denied = st == 403 or (isinstance(body, list) and any("error" in (x or {}) for x in body))
check("unconstrained briefs query denied", denied, err(body) if not denied else "")

# 7. the owner-scoped query the app actually issues
q = {"structuredQuery": {
        "from": [{"collectionId": "briefs"}],
        "where": {"fieldFilter": {"field": {"fieldPath": "user_id"},
                                  "op": "EQUAL", "value": {"stringValue": uid}}},
        "orderBy": [{"field": {"fieldPath": "created_at"}, "direction": "DESCENDING"}],
        "limit": 5}}
st, body = post(f"{FS}:runQuery", q, tok)
ok = st == 200 and isinstance(body, list)
check("owner-scoped briefs query allowed", ok, err(body) if not ok else f"{len(body)} row(s)")

# 8. billing is server-only
st, body = patch(f"{FS}/users/{uid}/billing/entitlement", fields({"active": True}), tok)
check("client write to billing denied", st == 403, err(body))

# 9. share_tokens fully closed
st, body = get(f"{FS}/share_tokens/anything", tok)
check("share_tokens read denied", st == 403, err(body))

# --- storage -----------------------------------------------------------
def upload(token, uid_, brief_, name, blob, ctype):
    obj = urllib.parse.quote(f"users/{uid_}/briefs/{brief_}/pages/{name}", safe="")
    url = f"https://firebasestorage.googleapis.com/v0/b/{BUCKET}/o?uploadType=media&name={obj}"
    return post(url, None, token, raw=blob, ctype=ctype)

SAMPLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sample_page.jpg")
jpeg = open(SAMPLE, "rb").read()

st, body = upload(tok, uid, BRIEF, "photo_ruleschecka1.jpg", jpeg, "image/jpeg")
check("page upload allowed", st == 200, err(body) if st != 200 else f"{len(jpeg)} bytes")

st, body = upload(tok, uid, BRIEF, "bad-name.jpg", jpeg, "image/jpeg")
check("bad page filename denied", st in (401, 403), err(body))

st, body = upload(tok, uid, BRIEF, "photo_ruleschecka2.jpg", b"not an image", "application/pdf")
check("non-image content type denied", st in (401, 403), err(body))

st, body = upload(tokB, uid, BRIEF, "photo_ruleschecka3.jpg", jpeg, "image/jpeg")
check("cross-user page upload denied", st in (401, 403), err(body))

obj = urllib.parse.quote(f"users/{uid}/briefs/{BRIEF}/exports/brief.pdf", safe="")
url = f"https://firebasestorage.googleapis.com/v0/b/{BUCKET}/o?uploadType=media&name={obj}"
st, body = post(url, None, tok, raw=b"%PDF-1.4", ctype="application/pdf")
check("client write to exports denied", st in (401, 403), err(body))

print()
bad = [n for n, ok, _ in results if not ok]
print(f"{len(results) - len(bad)}/{len(results)} passed")
if bad:
    print("FAILED:", "; ".join(bad))
    sys.exit(1)
