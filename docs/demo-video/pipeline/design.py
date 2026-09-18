"""Shared design system for the Anteroom demo video frames."""
from PIL import Image, ImageDraw, ImageFont

W, H = 1920, 1080

# Anteroom brand, lifted from AnteroomColors / briefpdf.py
BRAND      = (54, 95, 80)
BRAND_LT   = (126, 170, 150)
BRAND_DIM  = (86, 122, 106)
INK        = (17, 24, 21)
PAPER      = (247, 249, 248)
MUTED      = (105, 128, 117)
FLAG       = (198, 74, 66)
FLAG_SOFT  = (255, 214, 210)
BG_TOP     = (11, 18, 15)
BG_BOT     = (22, 34, 29)
WHITE      = (255, 255, 255)

F = "/System/Library/Fonts/Supplemental/"
def font(size, bold=False, mono=False):
    if mono:
        return ImageFont.truetype("/System/Library/Fonts/SFNSMono.ttf", size)
    return ImageFont.truetype(F + ("Arial Bold.ttf" if bold else "Arial.ttf"), size)

def gradient_bg(w=W, h=H, top=BG_TOP, bot=BG_BOT):
    """Vertical gradient with a faint brand glow top-left."""
    bg = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(bg)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)],
               fill=tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-420, -520, 900, 640], fill=(54, 95, 80, 46))
    from PIL import ImageFilter
    glow = glow.filter(ImageFilter.GaussianBlur(190))
    bg = Image.alpha_composite(bg.convert("RGBA"), glow)
    return bg.convert("RGB")

def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius, fill=255)
    return m

def wrap(draw, text, fnt, max_w):
    words, lines, line = text.split(), [], ""
    for wd in words:
        c = (line + " " + wd).strip()
        if draw.textlength(c, font=fnt) <= max_w:
            line = c
        else:
            if line:
                lines.append(line)
            line = wd
    if line:
        lines.append(line)
    return lines

def draw_block(d, x, y, text, fnt, fill, max_w, leading):
    for ln in wrap(d, text, fnt, max_w):
        d.text((x, y), ln, font=fnt, fill=fill)
        y += leading
    return y

def caption_bar(img, text):
    """Burned-in caption strip. Demo videos are watched sound-off."""
    d = ImageDraw.Draw(img, "RGBA")
    d.rectangle([0, H - 104, W, H], fill=(6, 11, 9, 235))
    d.line([(0, H - 104), (W, H - 104)], fill=(54, 95, 80), width=2)
    f = font(31)
    tw = d.textlength(text, font=f)
    d.text(((W - tw) / 2, H - 70), text, font=f, fill=(226, 236, 231))
    return img

def eyebrow(d, x, y, text, color=BRAND_LT):
    f = font(22, bold=True)
    d.text((x, y), text.upper(), font=f, fill=color)
    return y + 40
