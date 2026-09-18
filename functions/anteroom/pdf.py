"""Render a brief's structured content into a one-page A4 PDF.

Ported from backend/briefpdf.py on the `main` branch. Two deliberate changes.

The QR code is gone, along with the public share page it pointed at. The old
build advertised "Scan to view online" against a guessable `/s/{brief_id}` URL
on a domain that may not resolve -- a link to a patient's medication list that
anyone could enumerate. Cutting the page and the claim together was the right
trade for a two-week runway.

`watermarked` is not a parameter the caller chooses. It is derived from the
entitlement by `renderBriefPdf`, because the previous design let the client
decide whether its own PDF was watermarked.
"""

from __future__ import annotations

import io
from typing import Optional

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

BRAND = colors.HexColor("#365F50")
INK = colors.HexColor("#111815")
MUTED = colors.HexColor("#698075")
LINE = colors.HexColor("#E6EDE9")
FLAG = colors.HexColor("#9E3838")

# Accented Spanish is safe with reportlab's built-in Helvetica: the standard
# Type 1 fonts are WinAnsiEncoding, which covers Latin-1. Anything outside it
# (Greek, Cyrillic, CJK) would need an embedded TTF.
LABELS = {
    "en": {
        "subtitle": "Pre-visit brief",
        "generated": "Generated",
        "patient": "Patient",
        "name": "Name",
        "dob": "DOB",
        "sex": "Sex",
        "id": "ID",
        "no_patient": "No patient details detected on the pages.",
        "referral": "Referral reason",
        "not_detected": "Not detected on the pages.",
        "medications": "Medications",
        "col_name": "NAME",
        "col_dose": "DOSE",
        "col_freq": "FREQUENCY",
        "none": "None detected.",
        "allergies": "Allergies",
        "flagged": "Flagged for review",
        "footer_id": "Anteroom pre-visit brief - ID",
        "footer_note": "Extracted directly from patient documents - no diagnosis.",
        "watermark_ribbon": "ANTEROOM FREE - Upgrade for clean export",
    },
    "es": {
        "subtitle": "Resumen previo a la visita",
        "generated": "Generado",
        "patient": "Paciente",
        "name": "Nombre",
        "dob": "Fecha nac.",
        "sex": "Sexo",
        "id": "ID",
        "no_patient": "No se detectaron datos del paciente en las páginas.",
        "referral": "Motivo de derivación",
        "not_detected": "No detectado en las páginas.",
        "medications": "Medicamentos",
        "col_name": "NOMBRE",
        "col_dose": "DOSIS",
        "col_freq": "FRECUENCIA",
        "none": "No se detectaron.",
        "allergies": "Alergias",
        "flagged": "Marcado para revisar",
        "footer_id": "Resumen previo Anteroom - ID",
        "footer_note": "Extraído directamente de los documentos del paciente - sin diagnóstico.",
        "watermark_ribbon": "ANTEROOM FREE - Actualiza para exportar sin marca",
    },
}


def _draw_multiline(
    c: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    max_width: float,
    font: str,
    size: float,
    leading: Optional[float] = None,
    color=INK,
) -> float:
    c.setFont(font, size)
    c.setFillColor(color)
    leading = leading or size + 2
    line = ""
    for word in text.split():
        candidate = f"{line} {word}".strip()
        if c.stringWidth(candidate, font, size) <= max_width:
            line = candidate
        else:
            if line:
                c.drawString(x, y, line)
                y -= leading
            line = word
    if line:
        c.drawString(x, y, line)
        y -= leading
    return y


def _draw_watermark(c: canvas.Canvas, page_w: float, page_h: float, ribbon: str) -> None:
    c.saveState()
    c.setFillColor(colors.Color(0.55, 0.63, 0.58, alpha=0.15))
    c.setFont("Helvetica-Bold", 90)
    c.translate(page_w / 2, page_h / 2)
    c.rotate(30)
    c.drawCentredString(0, 0, "ANTEROOM FREE")
    c.restoreState()

    c.saveState()
    c.setFillColor(colors.Color(0.21, 0.37, 0.31, alpha=0.9))
    c.rect(page_w - 62 * mm, page_h - 10 * mm, 62 * mm, 6 * mm, stroke=0, fill=1)
    c.setFillColor(colors.white)
    c.setFont("Helvetica-Bold", 8)
    c.drawCentredString(page_w - 31 * mm, page_h - 8 * mm, ribbon)
    c.restoreState()


def render_brief_pdf(
    content: dict,
    *,
    generated_at: str = "",
    watermarked: bool = True,
    brief_id: str = "",
    language: str = "en",
) -> bytes:
    L = LABELS.get(language, LABELS["en"])
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    page_w, page_h = A4
    margin_x = 20 * mm
    right_x = page_w - margin_x
    text_width = right_x - margin_x
    y = page_h - 20 * mm

    # Header
    c.setFillColor(BRAND)
    c.setFont("Helvetica-Bold", 22)
    c.drawString(margin_x, y, "Anteroom")
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 9)
    c.drawString(margin_x, y - 12, L["subtitle"])
    if generated_at:
        c.drawRightString(right_x, y, f"{L['generated']} {generated_at}")
    c.setFillColor(LINE)
    c.setLineWidth(0.5)
    c.line(margin_x, y - 18, right_x, y - 18)
    y -= 30

    patient = content.get("patient") or {}
    referral = content.get("referral_reason")
    meds = content.get("medications") or []
    allergies = content.get("allergies") or []
    flagged = content.get("flagged_items") or []

    # Patient
    c.setFillColor(INK)
    c.setFont("Helvetica-Bold", 12)
    c.drawString(margin_x, y, L["patient"])
    y -= 14
    c.setFont("Helvetica", 10)
    rows = [
        (L[key], patient[field])
        for key, field in (("name", "name"), ("dob", "dob"), ("sex", "sex"), ("id", "id_number"))
        if patient.get(field)
    ]
    if not rows:
        c.setFillColor(MUTED)
        c.drawString(margin_x, y, L["no_patient"])
        y -= 14
        c.setFillColor(INK)
    else:
        for label, value in rows:
            c.setFillColor(MUTED)
            c.drawString(margin_x, y, label)
            c.setFillColor(INK)
            c.drawString(margin_x + 30 * mm, y, str(value))
            y -= 12
    y -= 6

    # Referral reason
    c.setFillColor(INK)
    c.setFont("Helvetica-Bold", 12)
    c.drawString(margin_x, y, L["referral"])
    y -= 14
    if referral:
        y = _draw_multiline(c, referral, margin_x, y, text_width, "Helvetica", 10, leading=13)
    else:
        c.setFillColor(MUTED)
        c.setFont("Helvetica-Oblique", 10)
        c.drawString(margin_x, y, L["not_detected"])
        y -= 12
    y -= 8

    # Medications
    c.setFillColor(INK)
    c.setFont("Helvetica-Bold", 12)
    c.drawString(margin_x, y, f"{L['medications']} ({len(meds)})")
    y -= 14
    if meds:
        col2 = margin_x + 65 * mm
        col3 = margin_x + 115 * mm
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(MUTED)
        c.drawString(margin_x, y, L["col_name"])
        c.drawString(col2, y, L["col_dose"])
        c.drawString(col3, y, L["col_freq"])
        y -= 12
        c.setFont("Helvetica", 10)
        c.setFillColor(INK)
        for med in meds[:12]:
            if y < 70 * mm:
                break
            c.drawString(margin_x, y, (med.get("name") or "")[:38])
            c.drawString(col2, y, (med.get("dose") or "-")[:22])
            c.drawString(col3, y, (med.get("frequency") or "-")[:36])
            y -= 12
    else:
        c.setFillColor(MUTED)
        c.setFont("Helvetica-Oblique", 10)
        c.drawString(margin_x, y, L["none"])
        y -= 12
    y -= 6

    # Allergies
    c.setFillColor(INK)
    c.setFont("Helvetica-Bold", 12)
    c.drawString(margin_x, y, f"{L['allergies']} ({len(allergies)})")
    y -= 14
    if allergies:
        for allergy in allergies[:8]:
            if y < 50 * mm:
                break
            substance = allergy.get("substance") or ""
            reaction = allergy.get("reaction")
            line = substance + (f" - {reaction}" if reaction else "")
            y = _draw_multiline(c, line, margin_x, y, text_width, "Helvetica", 10, leading=13)
    else:
        c.setFillColor(MUTED)
        c.setFont("Helvetica-Oblique", 10)
        c.drawString(margin_x, y, L["none"])
        y -= 12
    y -= 6

    # Flagged for review. Last section but the one that matters most: these are
    # the items a clinician must check against the original page.
    if flagged:
        c.setFillColor(FLAG)
        c.setFont("Helvetica-Bold", 12)
        c.drawString(margin_x, y, f"{L['flagged']} ({len(flagged)})")
        y -= 14
        for item in flagged[:6]:
            if y < 30 * mm:
                break
            y = _draw_multiline(
                c, f"- {item}", margin_x, y, text_width, "Helvetica", 10, leading=13, color=INK
            )

    # Footer
    c.setFillColor(LINE)
    c.line(margin_x, 15 * mm, right_x, 15 * mm)
    c.setFillColor(MUTED)
    c.setFont("Helvetica", 8)
    c.drawString(margin_x, 10 * mm, f"{L['footer_id']} {brief_id}")
    c.drawRightString(right_x, 10 * mm, L["footer_note"])

    if watermarked:
        _draw_watermark(c, page_w, page_h, L["watermark_ribbon"])

    c.showPage()
    c.save()
    return buf.getvalue()
