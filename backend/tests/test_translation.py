"""Anteroom Pro Translation feature tests (BACKEND ONLY).

Coverage:
- POST /api/briefs/{id}/translate  (auth, 404, 400 not-generated, invalid lang,
  same-language no-op, EN→ES round-trip, caching/idempotency, verbatim rules
  on names/doses/dates/IDs).
- GET  /api/briefs/{id}/pdf?lang=  (en/es/fake-fallback → application/pdf bytes).
- GET  /api/public/briefs/{share_token}?lang=  (Spanish section titles present,
  language switcher rendered when translations are cached).
"""
import io
import os
import time
from typing import Any

import pytest
import requests
from PIL import Image, ImageDraw, ImageFont

BASE_URL = (
    os.environ.get("EXPO_PUBLIC_BACKEND_URL")
    or os.environ.get("EXPO_BACKEND_URL", "")
).rstrip("/")
assert BASE_URL, "EXPO_PUBLIC_BACKEND_URL / EXPO_BACKEND_URL must be set"

SEED_EMAIL = "test1@anteroom.dev"
SEED_PASSWORD = "password123"


# ---------------- image helper (real medical-looking referral) ----------------

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
    img = Image.new("RGB", (900, 1200), "white")
    d = ImageDraw.Draw(img)
    bold, reg = _font()
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
    d.line([(30, 900), (400, 900)], fill=(80, 80, 80), width=2)
    d.text((30, 910), "Dr. J. Hartley, MBBS", fill="black", font=reg)
    d.rectangle([600, 850, 860, 970], outline=(150, 20, 20), width=3)
    d.text((615, 890), "CLINIC STAMP", fill=(150, 20, 20), font=bold)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


# ---------------- fixtures ----------------

def H(tok): return {"Authorization": f"Bearer {tok}"}


@pytest.fixture(scope="module")
def s():
    return requests.Session()


@pytest.fixture(scope="module")
def token_a(s):
    r = s.post(f"{BASE_URL}/api/auth/login",
               json={"email": SEED_EMAIL, "password": SEED_PASSWORD})
    assert r.status_code == 200, r.text
    return r.json()["session_token"]


def _create_brief_with_photo(s, tok, png: bytes, doc_type="referral") -> str:
    r = s.post(f"{BASE_URL}/api/briefs", json={"doc_type": doc_type}, headers=H(tok))
    assert r.status_code == 200, r.text
    bid = r.json()["brief_id"]
    files = {"file": ("scan.png", io.BytesIO(png), "image/png")}
    r = s.post(f"{BASE_URL}/api/briefs/{bid}/photos", files=files, headers=H(tok))
    assert r.status_code == 200, r.text
    return bid


@pytest.fixture(scope="module")
def generated_brief(s, token_a):
    """One generated brief re-used across the translation suite."""
    last_err = None
    for _ in range(2):  # retry once on LLM timeout
        try:
            bid = _create_brief_with_photo(s, token_a, build_referral_png(), "referral")
            r = s.post(f"{BASE_URL}/api/briefs/{bid}/generate",
                       headers=H(token_a), timeout=90)
            assert r.status_code == 200, r.text
            data = r.json()
            assert data.get("content"), "no content extracted"
            return data
        except Exception as e:
            last_err = e
            time.sleep(2)
    pytest.fail(f"Could not generate a brief for translation tests: {last_err}")


# =============================================================================
# 1. Post-generate default shape
# =============================================================================

class TestGenerateSetsSourceLanguage:
    def test_generated_brief_has_source_language_en(self, generated_brief):
        d = generated_brief
        assert d.get("source_language") == "en", d.get("source_language")
        # content_translations may be {} or None on the wire — accept both
        ct = d.get("content_translations")
        assert ct in ({}, None), ct
        assert d.get("available_languages") == ["en"], d.get("available_languages")


# =============================================================================
# 2. Translate endpoint — auth, validation, error codes
# =============================================================================

class TestTranslateErrors:
    def test_requires_auth(self, s, generated_brief):
        bid = generated_brief["brief_id"]
        r = s.post(f"{BASE_URL}/api/briefs/{bid}/translate",
                   json={"target_language": "es"})
        assert r.status_code == 401, r.text

    def test_unknown_brief_returns_404(self, s, token_a):
        r = s.post(f"{BASE_URL}/api/briefs/brief_doesnotexist_xyz/translate",
                   json={"target_language": "es"}, headers=H(token_a))
        assert r.status_code == 404, r.text

    def test_invalid_language_returns_400(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        r = s.post(f"{BASE_URL}/api/briefs/{bid}/translate",
                   json={"target_language": "fr"}, headers=H(token_a))
        assert r.status_code == 400, r.text
        # Sanity: error message should be helpful
        body = r.text.lower()
        assert "target_language" in body or "language" in body, r.text

    def test_translate_before_generate_returns_400(self, s, token_a):
        """A brief with a photo but no /generate call."""
        bid = _create_brief_with_photo(s, token_a, build_referral_png(), "referral")
        r = s.post(f"{BASE_URL}/api/briefs/{bid}/translate",
                   json={"target_language": "es"}, headers=H(token_a))
        assert r.status_code == 400, r.text
        assert "not been generated" in r.text.lower(), r.text


# =============================================================================
# 3. Translate — same-language no-op
# =============================================================================

class TestTranslateSameLanguage:
    def test_target_equals_source_is_noop(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        r = s.post(f"{BASE_URL}/api/briefs/{bid}/translate",
                   json={"target_language": "en"}, headers=H(token_a))
        assert r.status_code == 200, r.text
        data = r.json()
        # No new cached translation should appear
        ct = data.get("content_translations") or {}
        assert "en" not in ct, ct
        assert data.get("source_language") == "en"


# =============================================================================
# 4. Translate EN → ES: verbatim rules + caching
# =============================================================================

def _first_str(*vals: Any) -> str:
    for v in vals:
        if isinstance(v, str) and v.strip():
            return v
    return ""


class TestTranslateEnglishToSpanish:
    def test_translate_es_returns_200_and_caches(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        t0 = time.time()
        r = s.post(f"{BASE_URL}/api/briefs/{bid}/translate",
                   json={"target_language": "es"}, headers=H(token_a), timeout=90)
        elapsed_first = time.time() - t0
        assert r.status_code == 200, r.text
        data = r.json()
        # Persist for later tests
        pytest.tr_brief = data
        pytest.tr_first_elapsed = elapsed_first

        # available_languages must now include both
        langs = set(data.get("available_languages") or [])
        assert langs == {"en", "es"}, langs

        ct = data.get("content_translations") or {}
        assert "es" in ct, ct
        es = ct["es"]
        source = data.get("content") or {}
        assert isinstance(es, dict) and es, es

        # ---- verbatim rules ----
        src_pat = source.get("patient") or {}
        es_pat = es.get("patient") or {}
        for k in ("name", "dob", "id_number"):
            if src_pat.get(k) is not None:
                assert es_pat.get(k) == src_pat.get(k), (
                    f"patient.{k} must be byte-for-byte identical between EN and ES: "
                    f"EN={src_pat.get(k)!r} ES={es_pat.get(k)!r}"
                )

        src_meds = source.get("medications") or []
        es_meds = es.get("medications") or []
        assert len(es_meds) == len(src_meds), (len(src_meds), len(es_meds))
        for i, (a, b) in enumerate(zip(src_meds, es_meds)):
            for k in ("name", "dose"):
                if a.get(k) is not None:
                    assert b.get(k) == a.get(k), (
                        f"medications[{i}].{k} must be identical: EN={a.get(k)!r} ES={b.get(k)!r}"
                    )

        # ---- soft check: translated fields are actually Spanish (or at least
        # not byte-for-byte identical to English) when there was English text ----
        src_rr = _first_str(source.get("referral_reason"))
        es_rr = _first_str(es.get("referral_reason"))
        if src_rr:
            # Either differs, OR contains a spanish-ish token — soft check.
            differs = es_rr and es_rr != src_rr
            spanishy = any(tok in (es_rr or "").lower() for tok in
                           ("motivo", "cardiología", "cardiologia", "fibrilación",
                            "auricular", "evaluación", "revisión", "ó", "á", "í"))
            assert differs or spanishy, (
                f"referral_reason should be Spanish, got same as EN: {es_rr!r}"
            )

        # Allergy reactions: at least one must be non-empty & (differ or look spanish)
        src_all = source.get("allergies") or []
        es_all = es.get("allergies") or []
        if src_all and any(a.get("reaction") for a in src_all):
            saw_translated = False
            for a_src, a_es in zip(src_all, es_all):
                s_rx = _first_str(a_src.get("reaction"))
                t_rx = _first_str(a_es.get("reaction"))
                if s_rx and t_rx and t_rx != s_rx:
                    saw_translated = True
                    break
            # This is a soft check — log rather than hard-fail if LLM chose to
            # keep a very short medical term unchanged (e.g. "rash" is common
            # even in Spanish clinical notes).
            if not saw_translated:
                print("[soft] allergy reactions unchanged between EN and ES")

    def test_translate_es_second_call_is_cached(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        t0 = time.time()
        r = s.post(f"{BASE_URL}/api/briefs/{bid}/translate",
                   json={"target_language": "es"}, headers=H(token_a), timeout=30)
        elapsed = time.time() - t0
        assert r.status_code == 200, r.text
        data = r.json()

        # Content translation for 'es' should be identical to the first call
        prev = getattr(pytest, "tr_brief", None)
        assert prev, "first-call fixture missing"
        assert (data.get("content_translations") or {}).get("es") == (
            (prev.get("content_translations") or {}).get("es")
        ), "cached ES translation content changed between calls"

        # And it should be fast (no LLM roundtrip). We allow generous margin.
        assert elapsed < 5.0, f"second call took {elapsed:.2f}s — likely not cached"


# =============================================================================
# 5. PDF ?lang= behaviour
# =============================================================================

class TestPdfLocalized:
    def test_pdf_lang_es_returns_pdf(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        # Ensure ES translation exists (idempotent)
        s.post(f"{BASE_URL}/api/briefs/{bid}/translate",
               json={"target_language": "es"}, headers=H(token_a), timeout=90)

        r = s.get(f"{BASE_URL}/api/briefs/{bid}/pdf?lang=es",
                  headers=H(token_a))
        assert r.status_code == 200, r.text[:400]
        assert r.headers.get("Content-Type", "").startswith("application/pdf"), \
            r.headers.get("Content-Type")
        assert r.content[:4] == b"%PDF"
        assert len(r.content) > 4000, f"pdf too small: {len(r.content)}"

    def test_pdf_lang_en_returns_pdf(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        r = s.get(f"{BASE_URL}/api/briefs/{bid}/pdf?lang=en",
                  headers=H(token_a))
        assert r.status_code == 200, r.text[:400]
        assert r.headers.get("Content-Type", "").startswith("application/pdf")
        assert r.content[:4] == b"%PDF"

    def test_pdf_lang_unknown_falls_back_to_source(self, s, token_a, generated_brief):
        bid = generated_brief["brief_id"]
        r = s.get(f"{BASE_URL}/api/briefs/{bid}/pdf?lang=fr",
                  headers=H(token_a))
        # Per spec: unknown/missing → falls back to source (still a valid PDF)
        assert r.status_code == 200, r.text[:400]
        assert r.headers.get("Content-Type", "").startswith("application/pdf")
        assert r.content[:4] == b"%PDF"


# =============================================================================
# 6. Public share ?lang= behaviour
# =============================================================================

class TestPublicShareLocalized:
    def test_public_share_default_is_english(self, s, generated_brief):
        sup = generated_brief.get("share_url_path")
        assert sup, generated_brief
        r = requests.get(f"{BASE_URL}{sup}")
        assert r.status_code == 200, r.text[:400]
        body = r.text
        # English section titles
        assert "Patient" in body, "English title 'Patient' missing"
        assert "Medications" in body, "English title 'Medications' missing"
        assert "Allergies" in body, "English title 'Allergies' missing"

    def test_public_share_es_shows_spanish_titles_and_switcher(
        self, s, token_a, generated_brief
    ):
        bid = generated_brief["brief_id"]
        # Ensure ES translation exists so switcher renders + ES titles work
        s.post(f"{BASE_URL}/api/briefs/{bid}/translate",
               json={"target_language": "es"}, headers=H(token_a), timeout=90)
        sup = generated_brief.get("share_url_path")
        r = requests.get(f"{BASE_URL}{sup}?lang=es")
        assert r.status_code == 200, r.text[:400]
        assert r.headers.get("Content-Type", "").startswith("text/html")
        body = r.text
        # Spanish section titles
        assert "Paciente" in body, "Spanish title 'Paciente' missing"
        assert "Medicamentos" in body, "Spanish title 'Medicamentos' missing"
        assert "Alergias" in body, "Spanish title 'Alergias' missing"
        # Language switcher chips rendered when translations are cached
        assert 'class="lang"' in body, "language switcher chip strip missing"
        # And both EN + ES chips present
        assert "?lang=en" in body and "?lang=es" in body, body[-2000:]
