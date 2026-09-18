#!/usr/bin/env python3
"""Offline checks of the pipeline logic that does not need a network.

    functions/venv/bin/python tests/test_pipeline.py     # from functions/

The translation cases are the point of this file. `merge_translation` is a
safety boundary, not tidying: a translation pass must not be able to alter a
drug name, a dose, a frequency, a patient's name or an ID, whatever the model
returns. Those cases feed it a deliberately hostile response -- one that
translates and corrupts every clinical field -- and assert none of it lands.

Network-dependent behaviour (Vertex, Firestore, Storage) is covered by
tools/verify_rules.py and by the end-to-end run on a device.
"""
import json, os, re, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from anteroom import vertex
from anteroom.pdf import render_brief_pdf

ok = True
def check(name, cond, detail=""):
    global ok
    ok = ok and cond
    print(("PASS " if cond else "FAIL ") + name + (f" :: {detail}" if detail else ""))

# --- sanitize ---
messy = {
    "patient": {"name": "  Jane Roe ", "dob": "", "sex": None, "id_number": "X1"},
    "referral_reason": "   ",
    "medications": [
        {"name": " Warfarin ", "dose": " 3 mg ", "frequency": ""},
        {"name": "", "dose": "5 mg"},
        "nonsense",
    ],
    "allergies": [{"substance": "Penicillin", "reaction": ""}, {"reaction": "rash"}],
    "flagged_items": ["  dose unreadable ", "", 7],
}
c = vertex.sanitize_content(messy)
check("sanitize drops blank patient fields", c["patient"] == {"name": "Jane Roe", "id_number": "X1"}, c["patient"])
check("sanitize nulls blank referral_reason", c["referral_reason"] is None)
check("sanitize keeps only named meds", c["medications"] == [{"name": "Warfarin", "dose": "3 mg"}], c["medications"])
check("sanitize drops allergy without substance", c["allergies"] == [{"substance": "Penicillin"}], c["allergies"])
check("sanitize keeps only non-empty flags", c["flagged_items"] == ["dose unreadable"], c["flagged_items"])
check("sanitize of junk yields full shape",
      set(vertex.sanitize_content("nope")) == {"patient","referral_reason","medications","allergies","flagged_items"})

# --- merge_translation: the safety boundary ---
source = {
    "patient": {"name": "Jane Roe", "dob": "04/11/1968", "sex": "Female", "id_number": "X1"},
    "referral_reason": "Exertional chest tightness.",
    "medications": [{"name": "Bisoprolol", "dose": "2.5 mg", "frequency": "once daily"}],
    "allergies": [{"substance": "Penicillin", "reaction": "rash"}],
    "flagged_items": ["Warfarin dose unreadable on page 2"],
}
hostile = {
    "patient": {"name": "Juana Corza", "dob": "11/04/1968", "sex": "Mujer", "id_number": "X9"},
    "referral_reason": "Opresion toracica de esfuerzo.",
    "medications": [{"name": "Bisoprololo", "dose": "25 mg", "frequency": "dos veces al dia"}],
    "allergies": [{"substance": "Penicilina", "reaction": "erupcion"}],
    "flagged_items": ["Dosis de warfarina ilegible en la pagina 2"],
}
m = vertex.merge_translation(source, hostile)
check("translation cannot change patient name", m["patient"]["name"] == "Jane Roe", m["patient"]["name"])
check("translation cannot change dob", m["patient"]["dob"] == "04/11/1968")
check("translation cannot change id_number", m["patient"]["id_number"] == "X1")
check("translation DOES render sex", m["patient"]["sex"] == "Mujer")
check("translation cannot change drug name", m["medications"][0]["name"] == "Bisoprolol", m["medications"][0])
check("translation cannot change dose", m["medications"][0]["dose"] == "2.5 mg")
check("translation cannot change frequency", m["medications"][0]["frequency"] == "once daily")
check("translation cannot change allergy substance", m["allergies"][0]["substance"] == "Penicillin")
check("translation DOES render reaction", m["allergies"][0]["reaction"] == "erupcion")
check("translation DOES render flags", m["flagged_items"][0].startswith("Dosis"))
check("translation DOES render referral_reason", m["referral_reason"].startswith("Opresion"))

# --- PDF ---
free = render_brief_pdf(source, generated_at="2026-09-18", watermarked=True, brief_id="abc123", language="en")
pro  = render_brief_pdf(source, generated_at="2026-09-18", watermarked=False, brief_id="abc123", language="en")
es   = render_brief_pdf(source, generated_at="2026-09-18", watermarked=False, brief_id="abc123", language="es")
check("free PDF is a PDF", free[:4] == b"%PDF", free[:8])
check("pro PDF is a PDF", pro[:4] == b"%PDF")
check("watermark changes the bytes", len(free) != len(pro), f"free={len(free)} pro={len(pro)}")
def pdf_text(raw: bytes) -> str:
    """Drawn strings are not literal in the file: reportlab filters content
    streams through ASCII85Decode then FlateDecode. Undo both."""
    import base64, zlib
    out = []
    for m in re.finditer(rb"stream\r?\n(.*?)endstream", raw, re.DOTALL):
        body = m.group(1).strip()
        for attempt in (
            lambda b: zlib.decompress(base64.a85decode(b, adobe=b.endswith(b"~>"))),
            zlib.decompress,
            lambda b: b,
        ):
            try:
                out.append(attempt(body).decode("latin-1"))
                break
            except Exception:
                continue
    return "\n".join(out)

free_text, pro_text, es_text = pdf_text(free), pdf_text(pro), pdf_text(es)
check("watermark drawn only on the free export",
      ("ANTEROOM FREE" in free_text) and ("ANTEROOM FREE" not in pro_text))
check("clinical content reaches the page",
      all(t in pro_text for t in ("Bisoprolol", "2.5 mg", "Penicillin")), pro_text[:0])
check("flagged section is rendered", "Flagged for review" in pro_text)
check("spanish labels used for es", "Medicamentos" in es_text and "Medications" not in es_text)
check("spanish PDF renders", es[:4] == b"%PDF" and len(es) > 1000)
check("no QR/share claim in PDF", "Scan to view" not in free_text and "Escanear" not in es_text)


print()
print("ALL PASS" if ok else "SOME FAILED")
sys.exit(0 if ok else 1)
