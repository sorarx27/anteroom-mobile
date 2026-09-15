"""Anteroom auto-detect / manual redetect tests (Gemini 3.5 Flash).

Covers:
  * First-photo upload triggers async classification; poll GET /briefs/{id}.
  * Manual PATCH doc_type sets doc_type_manual_override=true; later photos
    must NOT overwrite doc_type.
  * POST /briefs/{id}/detect-doc-type re-runs classifier, clears override.
  * detect-doc-type on brief with 0 photos returns 400.
  * detect-doc-type cross-user isolation (404), auth required (401).
  * Response schema always exposes doc_type_manual_override + detected_*.
"""
import io
import os
import time
import pytest
import requests
from PIL import Image, ImageDraw

BASE_URL = (
    os.environ.get("EXPO_PUBLIC_BACKEND_URL")
    or os.environ.get("EXPO_BACKEND_URL", "")
).rstrip("/")
assert BASE_URL, "EXPO_PUBLIC_BACKEND_URL must be set"

SEED_EMAIL = "test1@anteroom.dev"
SEED_PASSWORD = "password123"


def _make_lab_report_png() -> bytes:
    """Build a synthetic-but-realistic lab report page with visible text/tables."""
    im = Image.new("RGB", (900, 1200), "white")
    d = ImageDraw.Draw(im)
    # Header
    d.rectangle([(0, 0), (900, 80)], fill=(20, 80, 120))
    d.text((30, 30), "CENTRAL DIAGNOSTIC LABORATORY", fill="white")
    d.text((30, 100), "LABORATORY REPORT - HEMATOLOGY PANEL", fill="black")
    d.text((30, 130), "Patient: John Doe   DOB: 1965-04-12   MRN: 993421", fill="black")
    d.text((30, 155), "Collected: 2026-01-05    Reported: 2026-01-06", fill="black")
    d.line([(30, 190), (870, 190)], fill="black", width=2)
    # Table header
    d.text((30, 210), "Test", fill="black")
    d.text((340, 210), "Result", fill="black")
    d.text((520, 210), "Ref. Range", fill="black")
    d.text((730, 210), "Units", fill="black")
    d.line([(30, 235), (870, 235)], fill="gray", width=1)
    rows = [
        ("Hemoglobin", "13.8", "13.0 - 17.0", "g/dL"),
        ("Hematocrit", "41.2", "38.0 - 50.0", "%"),
        ("WBC", "6.4", "4.0 - 11.0", "10^3/uL"),
        ("Platelets", "245", "150 - 400", "10^3/uL"),
        ("Glucose (fasting)", "92", "70 - 99", "mg/dL"),
        ("Sodium", "140", "135 - 145", "mmol/L"),
        ("Potassium", "4.1", "3.5 - 5.1", "mmol/L"),
        ("Creatinine", "0.9", "0.7 - 1.3", "mg/dL"),
    ]
    y = 255
    for name, val, ref, unit in rows:
        d.text((30, y), name, fill="black")
        d.text((340, y), val, fill="black")
        d.text((520, y), ref, fill="black")
        d.text((730, y), unit, fill="black")
        y += 30
    d.line([(30, y + 10), (870, y + 10)], fill="black", width=1)
    d.text((30, y + 40), "Interpretation: All values within normal range.", fill="black")
    d.text((30, y + 70), "Reviewed by: Dr. A. Smith, MD  -  Pathologist", fill="black")
    # Signature block
    d.rectangle([(30, y + 100), (300, y + 160)], outline="black")
    d.text((45, y + 115), "Signature", fill="gray")
    buf = io.BytesIO()
    im.save(buf, format="PNG")
    return buf.getvalue()


LAB_PNG = _make_lab_report_png()


@pytest.fixture(scope="module")
def s():
    return requests.Session()


def _login(s, email=SEED_EMAIL, password=SEED_PASSWORD):
    r = s.post(f"{BASE_URL}/api/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200, r.text
    return r.json()["session_token"]


def _register(s, email, password="password123"):
    r = s.post(f"{BASE_URL}/api/auth/register", json={"email": email, "password": password})
    assert r.status_code == 200, r.text
    return r.json()["session_token"]


@pytest.fixture(scope="module")
def token_a(s):
    return _login(s)


@pytest.fixture(scope="module")
def token_b(s):
    email = f"qa_{int(time.time())}_detect_b@anteroom.dev"
    return _register(s, email)


def H(tok):
    return {"Authorization": f"Bearer {tok}"}


def _create_brief(s, tok, doc_type="other"):
    r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": doc_type}, headers=H(tok))
    assert r.status_code == 200, r.text
    return r.json()


def _upload(s, tok, brief_id, png=LAB_PNG, name="lab.png"):
    files = {"file": (name, io.BytesIO(png), "image/png")}
    r = s.post(f"{BASE_URL}/api/briefs/{brief_id}/photos", files=files, headers=H(tok))
    assert r.status_code == 200, r.text
    return r.json()


def _poll_detection(s, tok, brief_id, timeout=20.0, interval=2.0):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        r = s.get(f"{BASE_URL}/api/briefs/{brief_id}", headers=H(tok))
        assert r.status_code == 200, r.text
        last = r.json()
        if last.get("detected_doc_type") is not None:
            return last
        time.sleep(interval)
    return last


# ---------------- Schema exposure ----------------
class TestBriefSchema:
    def test_new_brief_exposes_detection_fields(self, s, token_a):
        b = _create_brief(s, token_a, "other")
        assert b["doc_type_manual_override"] is False
        assert b["detected_doc_type"] is None
        assert b["detected_confidence"] is None
        # never leak internal fields
        assert "_id" not in b
        assert "storage_path" not in b

    def test_photo_response_no_internal_leaks(self, s, token_a):
        b = _create_brief(s, token_a, "other")
        after = _upload(s, token_a, b["brief_id"])
        assert "storage_path" not in after
        for p in after["photos"]:
            assert "storage_path" not in p
            assert "_id" not in p


# ---------------- Auto-detect on first upload ----------------
class TestAutoDetectFirstPhoto:
    def test_first_photo_triggers_async_classification(self, s, token_a):
        b = _create_brief(s, token_a, "other")
        after = _upload(s, token_a, b["brief_id"])
        # Immediately after upload the detection may be pending.
        assert after["doc_type_manual_override"] is False
        final = _poll_detection(s, token_a, b["brief_id"])
        assert final is not None
        assert final["detected_doc_type"] in {"referral", "med_list", "lab_result", "other"}, final
        assert final["detected_confidence"] in {"low", "medium", "high"}, final
        # Confidence high/medium => doc_type must equal detected_doc_type
        if final["detected_confidence"] in {"high", "medium"}:
            assert final["doc_type"] == final["detected_doc_type"], final
        else:
            assert final["doc_type"] == "other", final
        assert final["doc_type_manual_override"] is False
        pytest.brief_auto = b["brief_id"]

    def test_second_photo_does_not_re_detect(self, s, token_a):
        brief_id = getattr(pytest, "brief_auto", None)
        assert brief_id, "requires previous test"
        # Grab current detection snapshot
        r = s.get(f"{BASE_URL}/api/briefs/{brief_id}", headers=H(token_a))
        before = r.json()
        # Upload another photo
        _upload(s, token_a, brief_id, name="page2.png")
        # Wait a bit to allow any misconfigured background task
        time.sleep(4)
        r = s.get(f"{BASE_URL}/api/briefs/{brief_id}", headers=H(token_a))
        after = r.json()
        # detected_doc_type must be unchanged
        assert after["detected_doc_type"] == before["detected_doc_type"]
        assert after["detected_confidence"] == before["detected_confidence"]


# ---------------- Manual override persistence ----------------
class TestManualOverride:
    def test_patch_doc_type_sets_override_and_blocks_auto_detect(self, s, token_a):
        # Real flow: upload first photo -> classifier populates detected_*,
        # user then PATCHes doc_type -> override=true, further uploads keep
        # doc_type unchanged.
        b = _create_brief(s, token_a, "other")
        _upload(s, token_a, b["brief_id"], name="page1.png")
        first_state = _poll_detection(s, token_a, b["brief_id"])
        assert first_state is not None
        assert first_state["detected_doc_type"] in {
            "referral", "med_list", "lab_result", "other",
        }
        assert first_state["detected_confidence"] in {"low", "medium", "high"}
        assert first_state["doc_type_manual_override"] is False

        # User picks a different chip (manual PATCH)
        r = s.patch(
            f"{BASE_URL}/api/briefs/{b['brief_id']}",
            json={"doc_type": "referral"},
            headers=H(token_a),
        )
        assert r.status_code == 200
        after_patch = r.json()
        assert after_patch["doc_type"] == "referral"
        assert after_patch["doc_type_manual_override"] is True
        # detected_* preserved by PATCH
        assert after_patch["detected_doc_type"] == first_state["detected_doc_type"]

        # Upload a second photo — must NOT overwrite doc_type.
        _upload(s, token_a, b["brief_id"], name="page2.png")
        time.sleep(4)
        r = s.get(f"{BASE_URL}/api/briefs/{b['brief_id']}", headers=H(token_a))
        final = r.json()
        assert final["doc_type"] == "referral", final
        assert final["doc_type_manual_override"] is True
        # detected_* remained the same (later photos don't re-detect)
        assert final["detected_doc_type"] == first_state["detected_doc_type"]
        assert final["detected_confidence"] == first_state["detected_confidence"]


# ---------------- Manual redetect endpoint ----------------
class TestRedetectEndpoint:
    def test_requires_auth(self, s):
        r = s.post(f"{BASE_URL}/api/briefs/brief_nonexistent/detect-doc-type")
        assert r.status_code == 401

    def test_no_photos_returns_400(self, s, token_a):
        b = _create_brief(s, token_a, "other")
        r = s.post(
            f"{BASE_URL}/api/briefs/{b['brief_id']}/detect-doc-type",
            headers=H(token_a),
        )
        assert r.status_code == 400, r.text

    def test_cross_user_isolation_404(self, s, token_a, token_b):
        b = _create_brief(s, token_a, "other")
        _upload(s, token_a, b["brief_id"])
        r = s.post(
            f"{BASE_URL}/api/briefs/{b['brief_id']}/detect-doc-type",
            headers=H(token_b),
        )
        assert r.status_code == 404

    def test_redetect_clears_override_and_updates(self, s, token_a):
        # Start with a manually overridden brief
        b = _create_brief(s, token_a, "other")
        s.patch(
            f"{BASE_URL}/api/briefs/{b['brief_id']}",
            json={"doc_type": "med_list"},
            headers=H(token_a),
        )
        _upload(s, token_a, b["brief_id"])
        # Wait for classifier to at least run once (populates detected_*)
        _poll_detection(s, token_a, b["brief_id"])

        # Now call redetect
        r = s.post(
            f"{BASE_URL}/api/briefs/{b['brief_id']}/detect-doc-type",
            headers=H(token_a),
        )
        assert r.status_code == 200, r.text
        out = r.json()
        # override must be cleared
        assert out["doc_type_manual_override"] is False
        assert out["detected_doc_type"] in {"referral", "med_list", "lab_result", "other"}
        assert out["detected_confidence"] in {"low", "medium", "high"}
        # doc_type follows detected unless low
        if out["detected_confidence"] == "low":
            assert out["doc_type"] == "other"
        else:
            assert out["doc_type"] == out["detected_doc_type"]
