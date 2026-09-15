"""Rendering of the public (unauthenticated) HTML share page.

Extracted from server.py so the public router stays tiny.
"""

from __future__ import annotations

from typing import Any, Optional


_SHARE_LABELS = {
    "en": {
        "title": "Pre-visit brief",
        "generated": "Generated",
        "disclaimer": "Anteroom · Extracted directly from patient documents — no diagnosis.",
        "patient": "Patient",
        "name": "Name",
        "dob": "DOB",
        "sex": "Sex",
        "id": "ID",
        "no_patient": "No patient details detected.",
        "referral": "Referral reason",
        "not_detected": "Not detected on the pages.",
        "medications": "Medications",
        "allergies": "Allergies",
        "none": "None detected.",
        "flagged": "Flagged for review",
        "not_found_title": "Brief not found",
        "not_found_body": "This brief link is invalid.",
        "expired_title": "Link expired",
        "expired_body": "This share link has expired. Ask the patient to regenerate it.",
    },
    "es": {
        "title": "Resumen previo a la visita",
        "generated": "Generado",
        "disclaimer": "Anteroom · Extraído directamente de los documentos del paciente — sin diagnóstico.",
        "patient": "Paciente",
        "name": "Nombre",
        "dob": "Fecha nac.",
        "sex": "Sexo",
        "id": "ID",
        "no_patient": "No se detectaron datos del paciente.",
        "referral": "Motivo de derivación",
        "not_detected": "No detectado en las páginas.",
        "medications": "Medicamentos",
        "allergies": "Alergias",
        "none": "No se detectaron.",
        "flagged": "Marcado para revisar",
        "not_found_title": "Resumen no encontrado",
        "not_found_body": "Este enlace no es válido.",
        "expired_title": "Enlace caducado",
        "expired_body": "Este enlace ha caducado. Pide al paciente que lo regenere.",
    },
}


def esc(v: Any) -> str:
    import html
    return html.escape(str(v)) if v is not None else ""


def share_html_not_found() -> str:
    L = _SHARE_LABELS["en"]
    return _share_wrapper(
        L["not_found_title"], f"<p>{L['not_found_body']}</p>", L["disclaimer"]
    )


def share_html_expired() -> str:
    L = _SHARE_LABELS["en"]
    return _share_wrapper(
        L["expired_title"], f"<p>{L['expired_body']}</p>", L["disclaimer"]
    )


def _share_wrapper(title: str, body: str, disclaimer: str) -> str:
    return f"""<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title} — Anteroom</title>
<style>
  body{{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif;
       margin:0;padding:24px;background:#F4F7F6;color:#111815}}
  main{{max-width:640px;margin:24px auto;background:#fff;border-radius:20px;
       padding:32px;box-shadow:0 1px 3px rgba(17,24,21,0.08)}}
  h1{{color:#365F50;margin:0 0 8px}}
  h2{{color:#111815;font-size:16px;margin:20px 0 8px}}
  .muted{{color:#698075;font-size:12px}}
  .row{{display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid #E6EDE9}}
  .row:last-child{{border-bottom:0}}
  .label{{color:#698075}}
  .footer{{margin-top:32px;color:#698075;font-size:11px;text-align:center}}
  .flag{{background:#FFF3F3;border:1px solid #E6D0D0;color:#9E3838;padding:8px 12px;border-radius:12px;margin:6px 0;font-size:13px}}
  .empty{{color:#698075;font-style:italic}}
  .lang{{display:inline-flex;gap:6px;margin:8px 0 0}}
  .lang a{{display:inline-block;padding:4px 10px;border-radius:999px;background:#E6EDE9;
       color:#365F50;text-decoration:none;font-size:11px;font-weight:700;letter-spacing:0.4px}}
  .lang a.active{{background:#365F50;color:#fff}}
</style>
</head><body><main><h1>{title}</h1>{body}
<div class="footer">{disclaimer}</div>
</main></body></html>"""


def render_share_html(doc: dict, lang: Optional[str] = None) -> str:
    """Render the public share HTML for a brief document."""
    source_lang = (doc.get("source_language") or "en").lower()
    translations = doc.get("content_translations") or {}
    requested = (lang or "").lower() if lang else source_lang
    if requested == source_lang:
        content = doc.get("content") or {}
        active_lang = source_lang
    elif requested in translations:
        content = translations[requested]
        active_lang = requested
    else:
        content = doc.get("content") or {}
        active_lang = source_lang

    L = _SHARE_LABELS.get(active_lang, _SHARE_LABELS["en"])
    patient = content.get("patient") or {}
    meds = content.get("medications") or []
    allergies = content.get("allergies") or []
    referral = content.get("referral_reason")
    flagged = content.get("flagged_items") or []
    generated_at = doc.get("generated_at")
    gen_str = generated_at.strftime("%Y-%m-%d %H:%M UTC") if generated_at else ""

    def kv(label: str, value: Any) -> str:
        if value is None or value == "":
            return ""
        return f'<div class="row"><span class="label">{esc(label)}</span><span>{esc(value)}</span></div>'

    patient_html = "".join(
        [
            kv(L["name"], patient.get("name")),
            kv(L["dob"], patient.get("dob")),
            kv(L["sex"], patient.get("sex")),
            kv(L["id"], patient.get("id_number")),
        ]
    ) or f'<div class="empty">{L["no_patient"]}</div>'

    meds_html = (
        "".join(
            f'<div class="row"><span>{esc(m.get("name",""))}</span>'
            f'<span class="muted">{esc(m.get("dose") or "—")} · {esc(m.get("frequency") or "—")}</span></div>'
            for m in meds
        )
        or f'<div class="empty">{L["none"]}</div>'
    )

    allergies_html = (
        "".join(
            f'<div class="row"><span>{esc(a.get("substance",""))}</span>'
            f'<span class="muted">{esc(a.get("reaction") or "—")}</span></div>'
            for a in allergies
        )
        or f'<div class="empty">{L["none"]}</div>'
    )

    referral_html = (
        f"<p>{esc(referral)}</p>"
        if referral
        else f'<p class="empty">{L["not_detected"]}</p>'
    )

    flagged_html = ""
    if flagged:
        flagged_html = f'<h2>{L["flagged"]}</h2>' + "".join(
            f'<div class="flag">{esc(f)}</div>' for f in flagged
        )

    # Build language switcher — always show source; other langs only if cached.
    available = [source_lang] + [l for l in translations.keys() if l != source_lang]
    if len(available) > 1:
        base_path = f"/api/public/briefs/{doc.get('share_token','')}"
        chips = " ".join(
            f'<a class="{"active" if l == active_lang else ""}" href="{base_path}?lang={l}">{esc(l.upper())}</a>'
            for l in available
        )
        lang_switch = f'<div class="lang">{chips}</div>'
    else:
        lang_switch = ""

    return _share_wrapper(
        L["title"],
        f"""
<p class="muted">{L["generated"]} {esc(gen_str)} · {L["disclaimer"]}</p>
{lang_switch}
<h2>{L["patient"]}</h2>{patient_html}
<h2>{L["referral"]}</h2>{referral_html}
<h2>{L["medications"]} ({len(meds)})</h2>{meds_html}
<h2>{L["allergies"]} ({len(allergies)})</h2>{allergies_html}
{flagged_html}
""",
        L["disclaimer"],
    )
