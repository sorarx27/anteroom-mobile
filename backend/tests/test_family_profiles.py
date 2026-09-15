"""Family profiles backend tests."""
import os
import time
import pytest
import requests

BASE_URL = os.environ.get("EXPO_PUBLIC_BACKEND_URL", "https://medical-brief-setup.preview.emergentagent.com").rstrip("/")
API = f"{BASE_URL}/api"


def _register(email_prefix: str = "qa"):
    email = f"{email_prefix}_{int(time.time()*1000)}@anteroom.dev"
    r = requests.post(f"{API}/auth/register", json={"email": email, "password": "password123"})
    assert r.status_code == 200, r.text
    return r.json()["session_token"], r.json()["user"]


def _hdr(tok):
    return {"Authorization": f"Bearer {tok}"}


# -------- GET /api/profiles --------
class TestProfilesList:
    def test_requires_auth(self):
        r = requests.get(f"{API}/profiles")
        assert r.status_code == 401

    def test_auto_self_profile_created(self):
        tok, _ = _register("qa_list")
        r = requests.get(f"{API}/profiles", headers=_hdr(tok))
        assert r.status_code == 200
        data = r.json()
        assert len(data) >= 1
        selfs = [p for p in data if p["is_self"]]
        assert len(selfs) == 1
        assert selfs[0]["relationship"] == "self"
        assert selfs[0]["is_self"] is True


# -------- POST /api/profiles --------
class TestProfilesCreate:
    def setup_method(self):
        self.tok, _ = _register("qa_create")

    def test_create_partner(self):
        r = requests.post(f"{API}/profiles", json={
            "name": "Alex", "relationship": "partner", "dob": "1985-03-15", "sex": "female"
        }, headers=_hdr(self.tok))
        assert r.status_code == 200, r.text
        p = r.json()
        assert p["name"] == "Alex"
        assert p["relationship"] == "partner"
        assert p["is_self"] is False
        assert p["dob"] == "1985-03-15"
        assert p["sex"] == "female"

    def test_reject_relationship_self(self):
        r = requests.post(f"{API}/profiles", json={
            "name": "X", "relationship": "self"
        }, headers=_hdr(self.tok))
        assert r.status_code == 400

    def test_reject_bad_dob(self):
        r = requests.post(f"{API}/profiles", json={
            "name": "X", "relationship": "child", "dob": "03/15/1985"
        }, headers=_hdr(self.tok))
        assert r.status_code == 400

    def test_reject_bad_sex(self):
        r = requests.post(f"{API}/profiles", json={
            "name": "X", "relationship": "child", "sex": "unknown"
        }, headers=_hdr(self.tok))
        assert r.status_code == 400


# -------- PATCH /api/profiles/{id} --------
class TestProfilesUpdate:
    def setup_method(self):
        self.tok, _ = _register("qa_update")
        profs = requests.get(f"{API}/profiles", headers=_hdr(self.tok)).json()
        self.self_id = [p for p in profs if p["is_self"]][0]["profile_id"]
        r = requests.post(f"{API}/profiles", json={
            "name": "Alex", "relationship": "partner"
        }, headers=_hdr(self.tok))
        self.partner_id = r.json()["profile_id"]

    def test_update_name_dob_sex(self):
        r = requests.patch(f"{API}/profiles/{self.partner_id}", json={
            "name": "Alexa", "dob": "1990-01-01", "sex": "male"
        }, headers=_hdr(self.tok))
        assert r.status_code == 200
        p = r.json()
        assert p["name"] == "Alexa" and p["dob"] == "1990-01-01" and p["sex"] == "male"

    def test_reject_change_self_relationship(self):
        r = requests.patch(f"{API}/profiles/{self.self_id}", json={
            "relationship": "partner"
        }, headers=_hdr(self.tok))
        assert r.status_code == 400

    def test_reject_promote_to_self(self):
        r = requests.patch(f"{API}/profiles/{self.partner_id}", json={
            "relationship": "self"
        }, headers=_hdr(self.tok))
        assert r.status_code == 400

    def test_cross_user_isolation(self):
        tok_b, _ = _register("qa_update_b")
        r = requests.patch(f"{API}/profiles/{self.partner_id}", json={"name": "Hax"}, headers=_hdr(tok_b))
        assert r.status_code == 404


# -------- DELETE /api/profiles/{id} --------
class TestProfilesDelete:
    def setup_method(self):
        self.tok, _ = _register("qa_delete")
        profs = requests.get(f"{API}/profiles", headers=_hdr(self.tok)).json()
        self.self_id = [p for p in profs if p["is_self"]][0]["profile_id"]
        r = requests.post(f"{API}/profiles", json={
            "name": "Kid", "relationship": "child"
        }, headers=_hdr(self.tok))
        self.child_id = r.json()["profile_id"]

    def test_delete_requires_auth(self):
        r = requests.delete(f"{API}/profiles/{self.child_id}")
        assert r.status_code == 401

    def test_cannot_delete_self(self):
        r = requests.delete(f"{API}/profiles/{self.self_id}", headers=_hdr(self.tok))
        assert r.status_code == 400
        assert "Self" in r.text

    def test_delete_soft_archives_profile_and_briefs(self):
        # Create a brief for that non-self profile
        r = requests.post(f"{API}/briefs", json={"profile_id": self.child_id, "doc_type": "other"}, headers=_hdr(self.tok))
        assert r.status_code == 200
        brief_id = r.json()["brief_id"]

        # Delete profile
        r = requests.delete(f"{API}/profiles/{self.child_id}", headers=_hdr(self.tok))
        assert r.status_code == 200

        # Profile gone from list
        profs = requests.get(f"{API}/profiles", headers=_hdr(self.tok)).json()
        assert not any(p["profile_id"] == self.child_id for p in profs)

        # Brief 404 on GET
        r = requests.get(f"{API}/briefs/{brief_id}", headers=_hdr(self.tok))
        assert r.status_code == 404

        # Brief omitted from global list
        briefs = requests.get(f"{API}/briefs", headers=_hdr(self.tok)).json()
        assert not any(b["brief_id"] == brief_id for b in briefs)


# -------- POST /api/briefs with profile_id --------
class TestBriefsProfileScope:
    def setup_method(self):
        self.tok, _ = _register("qa_briefs")
        profs = requests.get(f"{API}/profiles", headers=_hdr(self.tok)).json()
        self.self_id = [p for p in profs if p["is_self"]][0]["profile_id"]
        r = requests.post(f"{API}/profiles", json={
            "name": "Nora", "relationship": "partner"
        }, headers=_hdr(self.tok))
        self.partner_id = r.json()["profile_id"]

    def test_brief_default_uses_self(self):
        r = requests.post(f"{API}/briefs", json={"doc_type": "other"}, headers=_hdr(self.tok))
        assert r.status_code == 200
        assert r.json()["profile_id"] == self.self_id

    def test_brief_with_profile_id(self):
        r = requests.post(f"{API}/briefs", json={"doc_type": "other", "profile_id": self.partner_id}, headers=_hdr(self.tok))
        assert r.status_code == 200
        assert r.json()["profile_id"] == self.partner_id

    def test_brief_cross_user_profile_404(self):
        tok_b, _ = _register("qa_briefs_b")
        r = requests.post(f"{API}/briefs", json={"doc_type": "other", "profile_id": self.partner_id}, headers=_hdr(tok_b))
        assert r.status_code == 404

    def test_list_filter_by_profile(self):
        # Create one against self, one against partner
        b_self = requests.post(f"{API}/briefs", json={"profile_id": self.self_id}, headers=_hdr(self.tok)).json()["brief_id"]
        b_par = requests.post(f"{API}/briefs", json={"profile_id": self.partner_id}, headers=_hdr(self.tok)).json()["brief_id"]

        r_self = requests.get(f"{API}/briefs?profile_id={self.self_id}", headers=_hdr(self.tok))
        assert r_self.status_code == 200
        ids_self = {b["brief_id"] for b in r_self.json()}
        assert b_self in ids_self
        assert b_par not in ids_self

        r_par = requests.get(f"{API}/briefs?profile_id={self.partner_id}", headers=_hdr(self.tok))
        assert r_par.status_code == 200
        ids_par = {b["brief_id"] for b in r_par.json()}
        assert b_par in ids_par
        assert b_self not in ids_par

    def test_list_filter_cross_user_profile_404(self):
        tok_b, _ = _register("qa_briefs_c")
        r = requests.get(f"{API}/briefs?profile_id={self.partner_id}", headers=_hdr(tok_b))
        assert r.status_code == 404


# -------- Startup backfill --------
class TestBackfill:
    def test_test1_seed_user_backfilled(self):
        r = requests.post(f"{API}/auth/login", json={"email": "test1@anteroom.dev", "password": "password123"})
        assert r.status_code == 200
        tok = r.json()["session_token"]
        profs = requests.get(f"{API}/profiles", headers=_hdr(tok)).json()
        selfs = [p for p in profs if p["is_self"]]
        assert len(selfs) == 1
        self_id = selfs[0]["profile_id"]
        # Every brief has a profile_id populated
        briefs = requests.get(f"{API}/briefs", headers=_hdr(tok)).json()
        for b in briefs:
            assert b["profile_id"], f"Brief {b['brief_id']} missing profile_id"
            assert isinstance(b["profile_id"], str)
        # Any pre-existing (older briefs) should map to Self
        # Not strictly required to be all self, but backfill defaults to Self
        for b in briefs:
            # confirm at least it's a real profile
            assert b["profile_id"].startswith("prof_")
