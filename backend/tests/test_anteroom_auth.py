"""Anteroom auth backend integration tests."""
import os
import time
import pytest
import requests

BASE_URL = os.environ.get("EXPO_PUBLIC_BACKEND_URL", "https://medical-brief-setup.preview.emergentagent.com").rstrip("/")
API = f"{BASE_URL}/api"

TS = int(time.time())
FRESH_EMAIL = f"qa_{TS}@anteroom.dev"
PASSWORD = "password123"


@pytest.fixture(scope="session")
def s():
    sess = requests.Session()
    sess.headers.update({"Content-Type": "application/json"})
    return sess


# ---- Health ----
def test_health(s):
    r = s.get(f"{API}/")
    assert r.status_code == 200
    assert r.json().get("status") == "ok"


# ---- Register ----
class TestRegister:
    def test_register_fresh(self, s):
        r = s.post(f"{API}/auth/register", json={"email": FRESH_EMAIL, "password": PASSWORD})
        assert r.status_code == 200, r.text
        data = r.json()
        assert "session_token" in data and data["session_token"]
        assert data["user"]["email"] == FRESH_EMAIL
        assert data["user"]["profile_completed"] is False
        pytest.fresh_token = data["session_token"]
        pytest.fresh_user_id = data["user"]["user_id"]

    def test_register_duplicate(self, s):
        r = s.post(f"{API}/auth/register", json={"email": FRESH_EMAIL, "password": PASSWORD})
        assert r.status_code == 409

    def test_register_invalid_email(self, s):
        r = s.post(f"{API}/auth/register", json={"email": "not-an-email", "password": PASSWORD})
        assert r.status_code == 422

    def test_register_short_password(self, s):
        r = s.post(f"{API}/auth/register", json={"email": f"qa_short_{TS}@anteroom.dev", "password": "abc"})
        assert r.status_code == 422


# ---- Login ----
class TestLogin:
    def test_login_valid(self, s):
        r = s.post(f"{API}/auth/login", json={"email": "test1@anteroom.dev", "password": PASSWORD})
        assert r.status_code == 200, r.text
        data = r.json()
        assert data["session_token"]
        assert data["user"]["email"] == "test1@anteroom.dev"
        assert data["user"]["profile_completed"] is True
        pytest.seed_token = data["session_token"]

    def test_login_invalid_email(self, s):
        r = s.post(f"{API}/auth/login", json={"email": f"nobody_{TS}@anteroom.dev", "password": PASSWORD})
        assert r.status_code == 401

    def test_login_wrong_password(self, s):
        r = s.post(f"{API}/auth/login", json={"email": "test1@anteroom.dev", "password": "wrongwrong"})
        assert r.status_code == 401


# ---- /auth/me ----
class TestMe:
    def test_me_valid_token(self, s):
        r = s.get(f"{API}/auth/me", headers={"Authorization": f"Bearer {pytest.fresh_token}"})
        assert r.status_code == 200
        assert r.json()["email"] == FRESH_EMAIL

    def test_me_no_token(self, s):
        # bypass session headers
        r = requests.get(f"{API}/auth/me")
        assert r.status_code == 401

    def test_me_bogus_token(self, s):
        r = requests.get(f"{API}/auth/me", headers={"Authorization": "Bearer garbage_token_xyz"})
        assert r.status_code == 401


# ---- Profile ----
class TestProfile:
    def test_profile_update_valid(self, s):
        payload = {"name": "QA User", "dob": "1992-03-15", "language": "en", "country": "United States"}
        r = s.put(f"{API}/auth/profile", headers={"Authorization": f"Bearer {pytest.fresh_token}"}, json=payload)
        assert r.status_code == 200, r.text
        user = r.json()
        assert user["profile_completed"] is True
        assert user["name"] == "QA User"
        assert user["dob"] == "1992-03-15"
        assert user["language"] == "en"

        # Verify persistence via /me
        r2 = s.get(f"{API}/auth/me", headers={"Authorization": f"Bearer {pytest.fresh_token}"})
        assert r2.status_code == 200
        assert r2.json()["profile_completed"] is True

    def test_profile_invalid_dob(self, s):
        payload = {"name": "X", "dob": "15/03/1992", "language": "en", "country": "US"}
        r = s.put(f"{API}/auth/profile", headers={"Authorization": f"Bearer {pytest.fresh_token}"}, json=payload)
        assert r.status_code == 400

    def test_profile_invalid_language(self, s):
        payload = {"name": "X", "dob": "1992-03-15", "language": "fr", "country": "US"}
        r = s.put(f"{API}/auth/profile", headers={"Authorization": f"Bearer {pytest.fresh_token}"}, json=payload)
        assert r.status_code == 400


# ---- Logout ----
class TestLogout:
    def test_logout_invalidates_token(self, s):
        # Create fresh token to logout
        email = f"qa_logout_{TS}@anteroom.dev"
        reg = s.post(f"{API}/auth/register", json={"email": email, "password": PASSWORD})
        assert reg.status_code == 200
        token = reg.json()["session_token"]

        # Logout
        r = s.post(f"{API}/auth/logout", headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 200

        # Same token must now be 401
        r2 = requests.get(f"{API}/auth/me", headers={"Authorization": f"Bearer {token}"})
        assert r2.status_code == 401


# ---- Account deletion (App Store Guideline 5.1.1(v)) ----
class TestDeleteAccount:
    def test_delete_requires_auth(self, s):
        r = s.delete(f"{API}/auth/account")
        assert r.status_code == 401

    def test_delete_removes_account_and_owned_data(self, s):
        email = f"qa_delete_{TS}@anteroom.dev"
        reg = s.post(f"{API}/auth/register", json={"email": email, "password": PASSWORD})
        assert reg.status_code == 200, reg.text
        token = reg.json()["session_token"]
        auth = {"Authorization": f"Bearer {token}"}

        # Give the account something to own: a self profile and a draft brief.
        profile = s.put(
            f"{API}/auth/profile",
            headers=auth,
            json={"name": "QA Delete", "dob": "1990-01-01", "language": "en", "country": "ES"},
        )
        assert profile.status_code == 200, profile.text
        brief = s.post(f"{API}/briefs", headers=auth, json={})
        assert brief.status_code == 200, brief.text

        r = s.delete(f"{API}/auth/account", headers=auth)
        assert r.status_code == 200, r.text
        assert r.json().get("ok") is True

        # The session must be dead immediately.
        assert requests.get(f"{API}/auth/me", headers=auth).status_code == 401

        # The old credentials must no longer authenticate.
        assert s.post(f"{API}/auth/login", json={"email": email, "password": PASSWORD}).status_code == 401

        # The address must be reusable — the record is gone, not tombstoned.
        again = s.post(f"{API}/auth/register", json={"email": email, "password": PASSWORD})
        assert again.status_code == 200, again.text
        assert again.json()["user"]["profile_completed"] is False
