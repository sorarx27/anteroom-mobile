"""Anteroom AI Brief Generation tests.

Coverage:
- POST /api/briefs/{id}/generate  (auth, 0-photo 400, cross-user 404, positive extraction, hallucination guard)
- GET  /api/briefs/{id}/pdf?watermark={true,false}  (auth, content-type, %PDF magic, size, different bytes, 400 when not generated)
- GET  /public/briefs/{token}  (no-auth html render, unknown token 404)
"""
import io
import os
import time

import pytest
import requests
from PIL import Image, ImageDraw, ImageFont

BASE_URL = (
    os.environ.get("EXPO_PUBLIC_BACKEND_URL")
    or os.environ.get("EXPO_BACKEND_URL", "")
).rstrip("/")
assert BASE_URL, "EXPO_PUBLIC_BACKEND_URL must be set"

SEED_EMAIL = "test1@anteroom.dev"
SEED_PASSWORD = "password123"


# --------------- image builders (real visual content) ---------------

def _font():
    for p in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        try:
            return ImageFont.truetype(p, 22), ImageFont.truetype(p, 18)
        except Exception:
            continue
    return ImageFont.load_default(), ImageFont.load_default()


def build_referral_png() -> bytes:
    """A referral-letter-looking image with clearly-printed fields."""
    img = Image.new("RGB", (900, 1200), "white")
    d = ImageDraw.Draw(img)
    bold, reg = _font()
    # header
    d.rectangle([0, 0, 900, 80], fill=(40, 90, 75))
    d.text((30, 25), "Northlake Family Clinic — Referral Letter", fill="white", font=bold)
    d.text((30, 110), "Patient Name: Maria Elena Rodriguez", fill="black", font=reg)
    d.text((30, 145), "Date of Birth: 1978-04-12", fill="black", font=reg)
    d.text((30, 180), "Sex: Female", fill="black", font=reg)
    d.text((30, 215), "MRN: 88-7712-A", fill="black", font=reg)
    d.text((30, 275), "Referral Reason:", fill="black", font=bold)
    d.text((30, 305), "Persistent atrial fibrillation for cardiology review.", fill="black", font=reg)
    d.text((30, 365), "Current Medications:", fill="black", font=bold)
    d.text((30, 395), "- Warfarin  5 mg  once daily", fill="black", font=reg)
    d.text((30, 425), "- Metoprolol  50 mg  twice daily", fill="black", font=reg)
    d.text((30, 455), "- Atorvastatin  20 mg  at night", fill="black", font=reg)
    d.text((30, 515), "Allergies:", fill="black", font=bold)
    d.text((30, 545), "- Penicillin  (rash)", fill="black", font=reg)
    d.text((30, 575), "- Sulfa drugs  (hives)", fill="black", font=reg)
    # give the model something visual — a signature-ish line and stamp
    d.line([(30, 900), (400, 900)], fill=(80, 80, 80), width=2)
    d.text((30, 910), "Dr. J. Hartley, MBBS", fill="black", font=reg)
    d.rectangle([600, 850, 860, 970], outline=(150, 20, 20), width=3)
    d.text((615, 890), "CLINIC STAMP", fill=(150, 20, 20), font=bold)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def build_blank_png() -> bytes:
    """A visually-featured but medically-empty photo — random noise texture.

    Per image_testing.md we must not send a flat-color image. We render soft
    noise + geometric shapes so the model receives real visual features but
    nothing that looks like patient data.
    """
    import random

    img = Image.new("RGB", (600, 800), "white")
    d = ImageDraw.Draw(img)
    # gentle noise
    rng = random.Random(7)
    for _ in range(1500):
        x = rng.randint(0, 599)
        y = rng.randint(0, 799)
        g = rng.randint(180, 240)
        d.point((x, y), fill=(g, g, g))
    # a few decorative shapes with no medical content
    d.rectangle([80, 100, 520, 220], outline=(120, 120, 120), width=3)
    d.ellipse([200, 320, 400, 520], outline=(160, 100, 100), width=3)
    d.line([(60, 700), (540, 700)], fill=(100, 100, 100), width=2)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


# --------------- fixtures ---------------

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
    email = f"qa_{int(time.time())}_gen@anteroom.dev"
    r = s.post(f"{BASE_URL}/api/auth/register", json={"email": email, "password": "password123"})
    assert r.status_code == 200, r.text
    return r.json()["session_token"]


def H(tok): return {"Authorization": f"Bearer {tok}"}


def _create_brief_with_photo(s, tok, png: bytes, doc_type="referral") -> str:
    r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": doc_type}, headers=H(tok))
    assert r.status_code == 200, r.text
    bid = r.json()["brief_id"]
    files = {"file": ("scan.png", io.BytesIO(png), "image/png")}
    r = s.post(f"{BASE_URL}/api/briefs/{bid}/photos", files=files, headers=H(tok))
    assert r.status_code == 200, r.text
    return bid


# --------------- module-scoped generated briefs (kept for pdf/public tests) ---------------

@pytest.fixture(scope="module")
def generated_brief(s, token_a):
    """Create a brief with the referral image and generate content once."""
    bid = _create_brief_with_photo(s, token_a, build_referral_png(), "referral")
    r = s.post(f"{BASE_URL}/api/briefs/{bid}/generate", headers=H(token_a), timeout=90)
    assert r.status_code == 200, r.text
    return r.json()


@pytest.fixture(scope="module")
def blank_brief(s, token_a):
    bid = _create_brief_with_photo(s, token_a, build_blank_png(), "other")
    r = s.post(f"{BASE_URL}/api/briefs/{bid}/generate", headers=H(token_a), timeout=90)
    assert r.status_code == 200, r.text
    return r.json()


# =============================================================================
# 1.  POST /api/briefs/{id}/generate  — auth & validation
# =============================================================================

class TestGenerateAuthAndValidation:
    def test_requires_auth(self, s):
        r = s.post(f"{BASE_URL}/api/briefs/brief_doesnotexist/generate")
        assert r.status_code == 401

    def test_missing_brief_returns_404(self, s, token_a):
        r = s.post(f"{BASE_URL}/api/briefs/brief_missing_xyz/generate", headers=H(token_a))
        assert r.status_code == 404

    def test_cross_user_brief_returns_404(self, s, token_a, token_b):
        r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "referral"}, headers=H(token_a))
        bid = r.json()["brief_id"]
        r = s.post(f"{BASE_URL}/api/briefs/{bid}/generate", headers=H(token_b))
        assert r.status_code == 404

    def test_generate_with_no_photos_returns_400(self, s, token_a):
        r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": "referral"}, headers=H(token_a))
        bid = r.json()["brief_id"]
        r = s.post(f"{BASE_URL}/api/briefs/{bid}/generate", headers=H(token_a))
        assert r.status_code == 400
        assert "Attach at least one photo" in r.text


# =============================================================================
# 2.  Positive extraction — referral letter
# =============================================================================

class TestGenerateExtraction:
    def test_referral_fields_extracted(self, generated_brief):
        d = generated_brief
        assert d["status"] == "complete"
        assert d.get("generated_at"), "generated_at must be populated"
        sup = d.get("share_url_path") or ""
        assert sup.startswith("/public/briefs/"), sup
        # /public/briefs/<32hex>
        token_part = sup.split("/")[-1]
        assert len(token_part) == 32 and all(c in "0123456789abcdef" for c in token_part)

        content = d.get("content") or {}
        patient = content.get("patient") or {}
        # verbatim name & dob
        assert (patient.get("name") or "").lower().startswith("maria"), patient
        # dob substring 1978
        assert "1978" in (patient.get("dob") or ""), patient
        # medications
        meds = content.get("medications") or []
        med_names = " | ".join((m.get("name") or "").lower() for m in meds)
        assert "warfarin" in med_names, med_names
        assert "metoprolol" in med_names, med_names
        # At least one med has dose & frequency populated
        has_dose_freq = any(m.get("dose") and m.get("frequency") for m in meds)
        assert has_dose_freq, meds
        # allergies with reaction
        allergies = content.get("allergies") or []
        subs = " | ".join((a.get("substance") or "").lower() for a in allergies)
        assert "penicillin" in subs, allergies
        has_reaction = any(a.get("reaction") for a in allergies)
        assert has_reaction, allergies
        # referral reason non-empty & related
        rr = (content.get("referral_reason") or "").lower()
        assert rr and ("atrial" in rr or "fibrillation" in rr or "cardio" in rr), rr


# =============================================================================
# 3.  Hallucination guard — blank/noise image
# =============================================================================

class TestHallucinationGuard:
    def test_blank_image_yields_empty_lists_and_null_patient(self, blank_brief):
        d = blank_brief
        assert d["status"] == "complete"
        content = d.get("content") or {}
        assert content.get("medications") == [], content.get("medications")
        assert content.get("allergies") == [], content.get("allergies")
        patient = content.get("patient") or {}
        # every patient field must be missing or null
        for k in ("name", "dob", "sex", "id_number"):
            v = patient.get(k)
            assert v in (None, "", None), f"patient.{k} unexpectedly populated: {v!r}"
        assert content.get("referral_reason") in (None, ""), content.get("referral_reason")


# =============================================================================
# 4.  GET /api/briefs/{id}/pdf
# =============================================================================

class TestBriefPdf:
    def test_pdf_requires_auth(self, s, generated_brief):
        bid = generated_brief["brief_id"]
        r = s.get(f"{BASE_URL}/api/briefs/{bid}/pdf")
        assert r.status_code == 401

    def test_pdf_watermarked_ok(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        r = s.get(f"{BASE_URL}/api/briefs/{bid}/pdf?watermark=true", headers=H(token_a))
        assert r.status_code == 200, r.text[:400]
        assert r.headers.get("Content-Type", "").startswith("application/pdf")
        assert "Content-Disposition" in r.headers
        assert r.content[:4] == b"%PDF"
        assert len(r.content) > 5000, f"pdf too small: {len(r.content)} bytes"
        # store for next assertion
        pytest.pdf_wm_size = len(r.content)
        pytest.pdf_wm_bytes = r.content

    def test_pdf_clean_differs_from_watermarked(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        r = s.get(f"{BASE_URL}/api/briefs/{bid}/pdf?watermark=false", headers=H(token_a))
        assert r.status_code == 200, r.text[:400]
        assert r.content[:4] == b"%PDF"
        assert len(r.content) > 5000
        # clean version should have different bytes than the watermarked one
        assert r.content != getattr(pytest, "pdf_wm_bytes", b""), "clean == watermarked pdf"
        # typically clean is smaller — allow either direction but sizes must differ
        assert len(r.content) != getattr(pytest, "pdf_wm_size", 0)

    def test_pdf_via_query_token_works(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        r = s.get(f"{BASE_URL}/api/briefs/{bid}/pdf?watermark=true&token={token_a}")
        assert r.status_code == 200
        assert r.content[:4] == b"%PDF"

    def test_pdf_400_when_not_generated(self, s, token_a):
        # brief with a photo but no /generate call
        bid = _create_brief_with_photo(s, token_a, build_referral_png(), "referral")
        r = s.get(f"{BASE_URL}/api/briefs/{bid}/pdf?watermark=true", headers=H(token_a))
        assert r.status_code == 400, r.text
        assert "not been generated" in r.text.lower()


# =============================================================================
# 5.  GET /public/briefs/{token}  — unauthenticated share page
# =============================================================================

class TestPublicShare:
    def test_unknown_token_returns_404_html(self, s):
        r = s.get(f"{BASE_URL}/public/briefs/deadbeefdeadbeefdeadbeefdeadbeef")
        assert r.status_code == 404
        assert r.headers.get("Content-Type", "").startswith("text/html")
        assert "invalid" in r.text.lower() or "not found" in r.text.lower()

    def test_public_share_renders_no_auth(self, s, generated_brief):
        sup = generated_brief["share_url_path"]
        # must work with NO Authorization header
        r = requests.get(f"{BASE_URL}{sup}")
        assert r.status_code == 200, r.text[:400]
        assert r.headers.get("Content-Type", "").startswith("text/html")
        body = r.text.lower()
        # patient name & at least one med name
        assert "maria" in body, "patient name missing on share page"
        assert ("warfarin" in body) or ("metoprolol" in body), "meds missing on share page"
