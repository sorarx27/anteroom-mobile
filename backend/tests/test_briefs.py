"""Anteroom brief capture tests — auth, briefs CRUD, photo upload/serve/delete, isolation."""
import os
import time
import io
import pytest
import requests

BASE_URL = (os.environ.get("EXPO_PUBLIC_BACKEND_URL") or os.environ.get("EXPO_BACKEND_URL", "")).rstrip("/")
assert BASE_URL, "EXPO_PUBLIC_BACKEND_URL must be set"

# --- 1x1 PNG bytes ---
PNG_BYTES = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000d49444154789c6360000000000200015d8f5c3b0000000049454e44ae426082"
)

SEED_EMAIL = "test1@anteroom.dev"
SEED_PASSWORD = "password123"


@pytest.fixture(scope="module")
def s():
    return requests.Session()


@pytest.fixture(scope="module")
def token_a(s):
    r = s.post(f"{BASE_URL}/api/auth/login", json={"email": SEED_EMAIL, "password": SEED_PASSWORD})
    assert r.status_code == 200, r.text
    return r.json()["session_token"]


@pytest.fixture(scope="module")
def token_b(s):
    email = f"qa_{int(time.time())}_b@anteroom.dev"
    r = s.post(f"{BASE_URL}/api/auth/register", json={"email": email, "password": "password123"})
    assert r.status_code == 200, r.text
    return r.json()["session_token"]


def H(tok): return {"Authorization": f"Bearer {tok}"}


# ------------------ Create brief ------------------
class TestCreateBrief:
    def test_requires_auth(self, s):
        r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "referral"})
        assert r.status_code == 401

    def test_invalid_doc_type_400(self, s, token_a):
        r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "bogus"}, headers=H(token_a))
        assert r.status_code == 400

    def test_create_ok(self, s, token_a):
        r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "referral"}, headers=H(token_a))
        assert r.status_code == 200, r.text
        d = r.json()
        assert d["status"] == "draft"
        assert d["doc_type"] == "referral"
        assert d["photos"] == []
        assert d["brief_id"].startswith("brief_")


# ------------------ Upload photo & serve ------------------
class TestPhotoLifecycle:
    def test_full_flow(self, s, token_a):
        # create
        r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "other"}, headers=H(token_a))
        brief_id = r.json()["brief_id"]

        # bad content-type -> 400
        files = {"file": ("bad.txt", io.BytesIO(b"hello"), "text/plain")}
        r = s.post(f"{BASE_URL}/api/briefs/{brief_id}/photos", files=files, headers=H(token_a))
        assert r.status_code == 400, r.text

        # good PNG upload
        files = {"file": ("scan.png", io.BytesIO(PNG_BYTES), "image/png")}
        r = s.post(f"{BASE_URL}/api/briefs/{brief_id}/photos", files=files, headers=H(token_a))
        assert r.status_code == 200, r.text
        brief = r.json()
        assert len(brief["photos"]) == 1
        p = brief["photos"][0]
        assert p["content_type"] == "image/png"
        assert p["size"] == len(PNG_BYTES)
        assert p["photo_id"].startswith("photo_")
        assert p["url"] == f"/api/briefs/{brief_id}/photos/{p['photo_id']}/file"

        # GET file via Bearer
        r = s.get(f"{BASE_URL}{p['url']}", headers=H(token_a))
        assert r.status_code == 200
        assert r.headers.get("Content-Type", "").startswith("image/png")
        assert r.content == PNG_BYTES

        # GET file via ?token=
        r = s.get(f"{BASE_URL}{p['url']}?token={token_a}")
        assert r.status_code == 200
        assert r.content == PNG_BYTES

        # Missing auth -> 401
        r = s.get(f"{BASE_URL}{p['url']}")
        assert r.status_code == 401

        # store for other tests
        pytest.brief_id_a = brief_id
        pytest.photo_id_a = p["photo_id"]


# ------------------ Cross-user isolation ------------------
class TestIsolation:
    def test_user_b_cannot_read_a_photo(self, s, token_b):
        b = getattr(pytest, "brief_id_a", None)
        p = getattr(pytest, "photo_id_a", None)
        assert b and p, "prev test must run first"
        r = s.get(f"{BASE_URL}/api/briefs/{b}/photos/{p}/file", headers=H(token_b))
        assert r.status_code == 404


# ------------------ List / Patch / Delete ------------------
class TestListAndUpdate:
    def test_list_only_current_user_sorted(self, s, token_a):
        # create two briefs; second is newer
        r1 = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "other"}, headers=H(token_a))
        older = r1.json()["brief_id"]
        time.sleep(0.2)
        r2 = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "med_list"}, headers=H(token_a))
        newer = r2.json()["brief_id"]

        r = s.get(f"{BASE_URL}/api/briefs", headers=H(token_a))
        assert r.status_code == 200
        items = r.json()
        ids = [b["brief_id"] for b in items]
        # both must be present and newer must precede older
        assert newer in ids and older in ids
        assert ids.index(newer) < ids.index(older)
        # user_ids all match (no cross-user leak)
        uids = {b["user_id"] for b in items}
        assert len(uids) == 1

    def test_patch_doc_type_status_and_order(self, s, token_a):
        r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "other"}, headers=H(token_a))
        brief_id = r.json()["brief_id"]
        # Add two photos
        pids = []
        for _ in range(2):
            files = {"file": ("scan.png", io.BytesIO(PNG_BYTES), "image/png")}
            r = s.post(f"{BASE_URL}/api/briefs/{brief_id}/photos", files=files, headers=H(token_a))
            pids.append(r.json()["photos"][-1]["photo_id"])

        # update doc_type
        r = s.patch(f"{BASE_URL}/api/briefs/{brief_id}", json={"doc_type": "lab_result"}, headers=H(token_a))
        assert r.status_code == 200
        assert r.json()["doc_type"] == "lab_result"

        # reorder (reverse)
        reversed_ids = list(reversed(pids))
        r = s.patch(f"{BASE_URL}/api/briefs/{brief_id}", json={"photo_order": reversed_ids}, headers=H(token_a))
        assert r.status_code == 200
        got = [p["photo_id"] for p in r.json()["photos"]]
        assert got == reversed_ids

        # status toggle
        r = s.patch(f"{BASE_URL}/api/briefs/{brief_id}", json={"status": "complete"}, headers=H(token_a))
        assert r.status_code == 200
        assert r.json()["status"] == "complete"

    def test_soft_delete_photo_and_brief(self, s, token_a):
        r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "other"}, headers=H(token_a))
        brief_id = r.json()["brief_id"]
        files = {"file": ("scan.png", io.BytesIO(PNG_BYTES), "image/png")}
        r = s.post(f"{BASE_URL}/api/briefs/{brief_id}/photos", files=files, headers=H(token_a))
        pid = r.json()["photos"][-1]["photo_id"]

        # delete photo
        r = s.delete(f"{BASE_URL}/api/briefs/{brief_id}/photos/{pid}", headers=H(token_a))
        assert r.status_code == 200
        assert all(p["photo_id"] != pid for p in r.json()["photos"])

        # delete brief
        r = s.delete(f"{BASE_URL}/api/briefs/{brief_id}", headers=H(token_a))
        assert r.status_code == 200
        # subsequent GET
        r = s.get(f"{BASE_URL}/api/briefs/{brief_id}", headers=H(token_a))
        assert r.status_code == 404
        # list omits it
        r = s.get(f"{BASE_URL}/api/briefs", headers=H(token_a))
        assert all(b["brief_id"] != brief_id for b in r.json())
