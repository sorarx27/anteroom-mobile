"""Renders every static frame + phone overlay named in the shot list."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from design import *
from frames import phone_overlay, static_frame, page_card, PX, PY, PW, PH, PR
from PIL import Image, ImageDraw, ImageFilter

R = "/Users/ahmedzayed/Downloads/Revenue cat"
OUT = os.path.dirname(os.path.abspath(__file__))
def save(img, name):
    img.convert("RGBA").save(os.path.join(OUT, name)); print("  ", name)

# ---------------------------------------------------------------- problem 1/2
def problem(ring=False):
    base = gradient_bg().convert("RGBA")
    left  = page_card(f"{R}/tools/sample_page.jpg", 690)
    right = page_card(f"{R}/tools/sample_page_illegible.jpg", 690)
    base.alpha_composite(left,  (150, 150))
    base.alpha_composite(right, (150 + left.width + 46, 150))
    d = ImageDraw.Draw(base, "RGBA")

    if ring:
        # The blotted Warfarin dose sits ~62% down, ~46% across the right page.
        rx = 150 + left.width + 46 + 20
        card_w, card_h = right.width - 40, 690
        cx, cy = rx + int(card_w * 0.515), 170 + int(card_h * 0.288)
        for i, a in ((0, 255), (6, 90)):
            d.ellipse([cx - 92 - i, cy - 40 - i, cx + 92 + i, cy + 40 + i],
                      outline=(198, 74, 66, a), width=5)
        d.line([(cx + 96, cy), (1360, cy)], fill=(198, 74, 66, 220), width=4)
        d.text((1382, cy - 46), "unreadable", font=font(40, bold=True), fill=(236, 138, 130))
        d.text((1382, cy + 4),  "dose", font=font(40, bold=True), fill=(236, 138, 130))

    x = 150
    y = 848
    if not ring:
        d.text((x, y), "A 10-minute appointment.", font=font(50, bold=True), fill=PAPER)
        d.text((x, y + 62), "A folder of paper in three languages.",
               font=font(50, bold=True), fill=(150, 176, 163))
        caption_bar(base, "In Spain the appointment is ten minutes and the paperwork is a folder.")
    else:
        d.text((x, y), "And some of it cannot be read.", font=font(50, bold=True), fill=PAPER)
        d.text((x, y + 62), "A guessed dose is worse than no dose.",
               font=font(50, bold=True), fill=(236, 138, 130))
        caption_bar(base, "Some of it is unreadable. Guessing is worse.")
    return base

save(problem(False), "st_problem-1.png")
save(problem(True),  "st_problem-2.png")

# ---------------------------------------------------------------- export 2
def pdf_compare():
    base = gradient_bg().convert("RGBA")
    d = ImageDraw.Draw(base, "RGBA")
    free = page_card(f"{R}/docs/assets/pdf-free-watermarked.png", 742)
    pro  = page_card(f"{R}/docs/assets/pdf-pro-spanish.png", 742)
    base.alpha_composite(free, (250, 96))
    base.alpha_composite(pro,  (250 + free.width + 150, 96))
    d.text((270, 866), "FREE", font=font(26, bold=True), fill=(236, 138, 130))
    d.text((270, 900), "Server-side watermark", font=font(30), fill=(168, 190, 178))
    px = 250 + free.width + 170
    d.text((px, 866), "PRO", font=font(26, bold=True), fill=BRAND_LT)
    d.text((px, 900), "Clean, and in Spanish", font=font(30), fill=(168, 190, 178))
    ax = 250 + free.width + 42
    d.text((ax, 470), "→", font=font(80, bold=True), fill=BRAND_LT)
    caption_bar(base, "Pro removes the watermark and renders it in Spanish.")
    return base

save(pdf_compare(), "st_export-2.png")

# ---------------------------------------------------------------- billing 1
def paywall_still():
    """Real iOS paywall screenshot, dropped into the same device frame."""
    base = gradient_bg().convert("RGBA")
    shot = Image.open(f"{R}/docs/assets/03_paywall_modal.png").convert("RGB").resize((PW, PH), Image.LANCZOS)
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([PX-16, PY-10, PX+PW+16, PY+PH+18], PR+14, fill=(0,0,0,150))
    base = Image.alpha_composite(base, sh.filter(ImageFilter.GaussianBlur(26)))
    base.paste(shot, (PX, PY), rounded_mask((PW, PH), PR))
    d = ImageDraw.Draw(base, "RGBA")
    d.rounded_rectangle([PX-5, PY-5, PX+PW+5, PY+PH+5], PR+5, outline=(74,106,92,255), width=5)
    y = eyebrow(d, 700, 300, "Monetisation")
    y = draw_block(d, 700, y, "RevenueCat purchases-kmp", font(56, bold=True), PAPER, 1090, 68)
    draw_block(d, 700, y + 22,
               "One Kotlin implementation drives Google Play and StoreKit. "
               "The entitlement is anteroom_pro.",
               font(29), (168, 190, 178), 1090, 42)
    caption_bar(base, "RevenueCat purchases-kmp — one implementation, two stores.")
    return base

save(paywall_still(), "st_billing-1.png")

# ---------------------------------------------------------------- billing 2
def terminal():
    base = gradient_bg().convert("RGBA")
    d = ImageDraw.Draw(base, "RGBA")
    y = eyebrow(d, 130, 96, "Verified against the deployed project")
    draw_block(d, 130, y, "The paywall is enforced on the server", font(54, bold=True), PAPER, 1660, 66)

    tx, ty, tw, th = 130, 250, 1660, 660
    d.rounded_rectangle([tx, ty, tx + tw, ty + th], 16, fill=(8, 13, 11, 245),
                        outline=(48, 70, 60, 255), width=2)
    d.rounded_rectangle([tx, ty, tx + tw, ty + 46], 16, fill=(22, 33, 28, 255))
    d.rectangle([tx, ty + 30, tx + tw, ty + 46], fill=(22, 33, 28, 255))
    for i, c in enumerate([(236, 106, 94), (232, 190, 92), (120, 196, 130)]):
        d.ellipse([tx + 22 + i * 26, ty + 16, tx + 34 + i * 26, ty + 28], fill=c)
    d.text((tx + 120, ty + 12), "python3 tools/verify_pipeline.py", font=font(21, mono=True),
           fill=(150, 176, 163))

    lines = [
        ("PASS", "illegible warfarin dose was NOT invented", "got dose=None"),
        ("PASS", "a flag mentions the unreadable dose", "Warfarin dose unreadable on page 2"),
        ("PASS", "translateBrief denied for free user", "PERMISSION_DENIED"),
        ("PASS", "free PDF is watermarked", "True"),
        ("PASS", "webhook grants entitlement", "{'ok': True}"),
        ("PASS", "Pro PDF is NOT watermarked", "False"),
        ("PASS", "another user cannot export this brief", "NOT_FOUND"),
    ]
    fm, fy = font(25, mono=True), ty + 84
    for tag, name, detail in lines:
        d.text((tx + 40, fy), tag, font=font(25, mono=True), fill=(120, 196, 130))
        d.text((tx + 116, fy), name, font=fm, fill=(224, 234, 229))
        d.text((tx + 116 + d.textlength(name, font=fm) + 18, fy), ":: " + detail,
               font=fm, fill=(126, 150, 138))
        fy += 40
    d.text((tx + 40, fy + 26), "43/43 passed", font=font(30, mono=True), fill=(150, 214, 165))
    caption_bar(base, "The server decides, not the client. It cannot unlock itself.")
    return base

save(terminal(), "st_billing-2.png")

# ---------------------------------------------------------------- CTA
def cta():
    base = gradient_bg().convert("RGBA")
    d = ImageDraw.Draw(base, "RGBA")
    shot = Image.open(f"{R}/docs/assets/android-brief-flagged-dosage.png").convert("RGB")
    h = 760; wd = int(shot.width * h / shot.height)
    shot = shot.resize((wd, h), Image.LANCZOS)
    ph = Image.new("RGBA", (wd, h), (0, 0, 0, 0)); ph.paste(shot, (0, 0))
    ph.putalpha(Image.eval(rounded_mask((wd, h), 26), lambda v: int(v * 0.30)))
    base.alpha_composite(ph, (1380, 160))

    d.text((150, 300), "Anteroom", font=font(104, bold=True), fill=PAPER)
    d.text((150, 428), "It tells you what it could not read.", font=font(44), fill=BRAND_LT)
    d.text((150, 540), "Android  ·  iOS  ·  Desktop  ·  Web", font=font(31, bold=True),
           fill=(150, 176, 163))
    d.text((150, 592), "One Kotlin Multiplatform codebase. Gemini 2.5 on Vertex AI, EU-only.",
           font=font(28), fill=(126, 150, 138))
    d.text((150, 700), "github.com/sorarx27/anteroom-mobile", font=font(30, mono=True),
           fill=(168, 190, 178))
    d.rounded_rectangle([150, 770, 620, 838], 34, fill=(54, 95, 80, 255))
    d.text((186, 788), "RevenueCat Ship-a-ton 2026", font=font(27, bold=True), fill=WHITE)
    caption_bar(base, "Android, iOS, desktop and web — from one Kotlin codebase.")
    return base

save(cta(), "st_cta-1.png")
print("static frames done")
