"""Generates every static frame and phone-shot overlay for the demo video."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from design import *
from PIL import Image, ImageDraw, ImageFilter

R = "/Users/ahmedzayed/Downloads/Revenue cat"
OUT = os.path.dirname(os.path.abspath(__file__))

# Phone window the screen recording shows through. 428/952 == 1080/2400.
PX, PY, PW, PH, PR = 190, 42, 402, 894, 30
TX, TW = 700, 1090


def phone_overlay(eyebrow_text, headline, sub, caption, badge=None):
    """Opaque frame with a rounded hole; the video plays behind the hole."""
    base = gradient_bg().convert("RGBA")
    d = ImageDraw.Draw(base, "RGBA")

    # Device shadow, drawn before punching the hole.
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle(
        [PX - 16, PY - 10, PX + PW + 16, PY + PH + 18], PR + 14, fill=(0, 0, 0, 150))
    base = Image.alpha_composite(base, sh.filter(ImageFilter.GaussianBlur(26)))
    d = ImageDraw.Draw(base, "RGBA")
    d.rounded_rectangle([PX - 5, PY - 5, PX + PW + 5, PY + PH + 5], PR + 5,
                        outline=(74, 106, 92, 255), width=5)

    y = 316
    if eyebrow_text:
        y = eyebrow(d, TX, y, eyebrow_text)
    y = draw_block(d, TX, y, headline, font(56, bold=True), PAPER, TW, 68)
    y += 22
    if sub:
        draw_block(d, TX, y, sub, font(29), (168, 190, 178), TW, 42)

    if badge:
        bf = font(22, bold=True)
        bw = d.textlength(badge, font=bf) + 36
        d.rounded_rectangle([TX, 240, TX + bw, 286], 23,
                            fill=(198, 74, 66, 235))
        d.text((TX + 18, 251), badge, font=bf, fill=WHITE)

    caption_bar(base, caption)

    # Punch the hole last so nothing paints over it.
    hole = Image.new("L", (W, H), 255)
    ImageDraw.Draw(hole).rounded_rectangle([PX, PY, PX + PW, PY + PH], PR, fill=0)
    r, g, b, a = base.split()
    a = Image.composite(a, Image.new("L", (W, H), 0), hole)
    return Image.merge("RGBA", (r, g, b, a))


def page_card(path, height):
    """A source document rendered as a paper card with a soft shadow.

    PDFs converted with `sips` carry an alpha channel, and pasting one onto a
    dark background renders the page solid black. Flatten onto white first.
    """
    im = Image.open(path)
    if im.mode in ("RGBA", "LA", "P"):
        im = im.convert("RGBA")
        flat = Image.new("RGB", im.size, (255, 255, 255))
        flat.paste(im, mask=im.split()[-1])
        im = flat
    else:
        im = im.convert("RGB")
    w = int(im.width * height / im.height)
    im = im.resize((w, height), Image.LANCZOS)
    card = Image.new("RGBA", (w + 40, height + 40), (0, 0, 0, 0))
    sh = Image.new("RGBA", card.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rectangle([20, 26, w + 20, height + 26], fill=(0, 0, 0, 165))
    card = Image.alpha_composite(card, sh.filter(ImageFilter.GaussianBlur(16)))
    card.paste(im, (20, 20))
    ImageDraw.Draw(card).rectangle([20, 20, w + 20, height + 20],
                                   outline=(200, 212, 206), width=2)
    return card


def static_frame(headline, sub, caption, eyebrow_text=None, badge=None):
    base = gradient_bg().convert("RGBA")
    d = ImageDraw.Draw(base, "RGBA")
    y = 96
    if eyebrow_text:
        y = eyebrow(d, 130, y, eyebrow_text)
    y = draw_block(d, 130, y, headline, font(54, bold=True), PAPER, 1660, 66)
    if sub:
        draw_block(d, 130, y + 16, sub, font(28), (168, 190, 178), 1500, 40)
    if badge:
        bf = font(22, bold=True)
        bw = d.textlength(badge, font=bf) + 36
        d.rounded_rectangle([1920 - 130 - bw, 96, 1920 - 130, 142], 23, fill=(198, 74, 66, 235))
        d.text((1920 - 130 - bw + 18, 107), badge, font=bf, fill=WHITE)
    caption_bar(base, caption)
    return base
