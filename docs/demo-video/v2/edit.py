"""Anteroom demo v2 — iPhone-first cut with a real purchase scene.

    python3 edit.py

Drop iPhone screen recordings into clips/ named G1..G10 (any of .mov/.mp4/
.MP4). Narration is either one file per line, clips/vo/01.m4a .. 15.m4a
(numbered by shot, see RECORDING_GUIDE.md), or one continuous
clips/voiceover.(m4a|wav|mp3) read against the cut. Any
clip that is missing is filled with a labelled stand-in from the v1 master,
so the cut always renders and always shows what is still to record.

Writes build/anteroom-demo-v2.mp4 (narrated if a voiceover exists) and
build/anteroom-demo-v2-silent.mp4.
"""
import glob, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PIPE = os.path.join(HERE, "..", "pipeline")
sys.path.insert(0, PIPE)
from design import *                      # noqa: E402,F403
from PIL import Image, ImageDraw, ImageFilter  # noqa: E402

CLIPS = os.path.join(HERE, "clips")
BUILD = os.path.join(HERE, "build")
SEG = os.path.join(BUILD, "seg")
MASTER_V1 = os.path.join(HERE, "..", "anteroom-demo-silent.mp4")
os.makedirs(SEG, exist_ok=True)
os.makedirs(CLIPS, exist_ok=True)

# Phone window, iPhone aspect (1179x2556). Taller than v1 so the UI reads.
PX, PY, PW, PH, PR = 190, 34, 416, 902, 34
TX, TW = 700, 1090
# Magnified callout, in the text column under the headline.
CX, CY, CW, CH, CR = TX, 590, 1090, 250, 22
BAND = CH / CW * PW / PH          # band height as a fraction of the phone screen
# Where the phone sat in the v1 master, for stand-ins.
V1_PHONE = (190, 42, 402, 894)

# kind: static (full-frame png) | clip (iPhone recording behind the overlay)
# ss:   seconds into the clip to start. v1: seconds into the v1 master for
#       the stand-in, or None for a slate.
# callout: vertical centre (0..1 of the phone screen) of a full-width band that
#       is shown magnified beside the phone. Set it to where the flagged item
#       sits in the recording; callout_v1 is the same for the v1 stand-in.
SHOTS = [
    dict(id="problem-1", dur=5, kind="static", img="st_problem-1.png",
         cap="In Spain the appointment is ten minutes and the paperwork is a folder."),
    dict(id="problem-2", dur=4, kind="static", img="st_problem-2.png",
         cap="Some of it is unreadable. A guessed dose is worse than none."),

    dict(id="hero", dur=5, kind="clip", clip="G1", ss=0.0, v1=9.5,
         eyebrow="Anteroom", head="The waiting room does the paperwork",
         sub="On iPhone, signed into the live project.",
         cap="Anteroom turns that folder into one structured brief."),
    dict(id="capture", dur=7, kind="clip", clip="G2", ss=0.0, v1=14.5,
         eyebrow="Step 1", head="Two pages, straight from the library",
         sub="Uploaded to your account. Scoped to you by security rules.",
         cap="Pick the pages from your library, or photograph them. They upload to your account."),
    dict(id="classify", dur=6, kind="clip", clip="G3", ss=0.0, v1=27.5,
         eyebrow="Step 2", head="Anteroom types each document",
         sub="Gemini 2.5 Flash. One tap to override it.",
         cap="Gemini Flash types each document. One tap overrides it."),
    dict(id="generate", dur=5, kind="clip", clip="G4", ss=0.0, v1=34.5,
         eyebrow="Step 3", head="One structured brief", badge="Model time trimmed",
         sub="Gemini 2.5 Pro, structured output, in europe-west1.",
         cap="Gemini 2.5 Pro reads both pages, in the EU."),
    dict(id="flag", dur=8, kind="clip", clip="G5", ss=0.0, v1=44.5,
         callout=0.52, callout_v1=0.475,
         eyebrow="The part that matters", head="It flags what it cannot read",
         sub="Seven medications found. The eighth dose was not guessed.",
         cap="It found seven medications. And it refused to guess the eighth."),
    dict(id="no-dose", dur=6, kind="clip", clip="G6", ss=0.0, v1=51.5,
         eyebrow="The part that matters", head="No dose. Not a guessed dose.",
         sub="Every other drug carries its dose verbatim.",
         cap="Every other drug has its dose, verbatim. Warfarin has none."),
    dict(id="provenance", dur=5, kind="clip", clip="G7", ss=0.0, v1=57.5,
         eyebrow="Provenance", head="Every line traces back to the page",
         sub="The original photograph is one tap away.",
         cap="Tap the flag, and the original page is right there."),
    dict(id="export-free", dur=6, kind="clip", clip="G8", ss=0.0, v1=62.5,
         eyebrow="Step 4", head="Hand your doctor a PDF",
         sub="Rendered on the server. The free tier is watermarked.",
         cap="Export a PDF for the clinic. On the free tier, the server watermarks it."),
    dict(id="purchase", dur=10, kind="clip", clip="G9", ss=0.0, v1=None,
         eyebrow="RevenueCat", head="Upgrade, and Pro unlocks itself",
         sub="purchases-kmp on StoreKit. Sandbox purchase on the TestFlight build.",
         cap="Anteroom Pro runs on RevenueCat. Buy it, and the app unlocks the moment the entitlement lands.",
         slate="Paywall  >  Monthly  >  Apple sheet  >  Pro unlocked"),
    dict(id="export-pro", dur=6, kind="clip", clip="G10", ss=0.0, v1=None,
         eyebrow="Pro", head="Clean export, in Spanish",
         sub="Doses and drug names are copied, never re-translated.",
         cap="Pro removes the watermark and renders the brief in Spanish.",
         slate="Español  >  Download PDF  >  no watermark"),
    dict(id="server", dur=5, kind="static", img="st_billing-2.png",
         cap="The server decides. The app cannot unlock itself."),
    dict(id="cta", dur=8, kind="static", img="st_cta-v2.png",
         cap="One Kotlin codebase. Live on iOS today. Try it on TestFlight."),
]
TOTAL = sum(s["dur"] for s in SHOTS)

V = ["-c:v", "libx264", "-preset", "medium", "-crf", "17",
     "-pix_fmt", "yuv420p", "-r", "30", "-an"]


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print("FFMPEG FAILED:", " ".join(cmd[:12]), "...")
        print(r.stderr[-1600:])
        sys.exit(1)


def find_clip(name):
    for ext in ("mov", "MOV", "mp4", "MP4", "m4v"):
        p = os.path.join(CLIPS, f"{name}.{ext}")
        if os.path.exists(p):
            return p
    return None


def tag(d, x, y, text, fill):
    f = font(22, bold=True)
    w = d.textlength(text, font=f) + 36
    d.rounded_rectangle([x, y, x + w, y + 46], 23, fill=fill)
    d.text((x + 18, y + 11), text, font=f, fill=WHITE if fill[0] < 200 else INK)


def overlay(s, stand_in):
    base = gradient_bg().convert("RGBA")
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle(
        [PX - 16, PY - 10, PX + PW + 16, PY + PH + 18], PR + 14, fill=(0, 0, 0, 150))
    base = Image.alpha_composite(base, sh.filter(ImageFilter.GaussianBlur(26)))
    d = ImageDraw.Draw(base, "RGBA")
    d.rounded_rectangle([PX - 5, PY - 5, PX + PW + 5, PY + PH + 5], PR + 5,
                        outline=(74, 106, 92, 255), width=5)
    y = 316
    if s.get("eyebrow"):
        y = eyebrow(d, TX, y, s["eyebrow"])
    y = draw_block(d, TX, y, s["head"], font(56, bold=True), PAPER, TW, 68)
    if s.get("sub"):
        draw_block(d, TX, y + 22, s["sub"], font(29), (168, 190, 178), TW, 42)
    if s.get("badge"):
        tag(d, TX, 240, s["badge"], (198, 74, 66, 235))
    if stand_in:
        tag(d, TX, 176, f"STAND-IN  ·  record {s['clip']} on iPhone", (240, 196, 64, 255))
    has_callout = callout_y(s, stand_in) is not None
    if has_callout:
        d.rounded_rectangle([CX - 4, CY - 4, CX + CW + 4, CY + CH + 4], CR + 4,
                            outline=(198, 74, 66, 255), width=4)
    caption_bar(base, s["cap"])
    hole = Image.new("L", (W, H), 255)
    hd = ImageDraw.Draw(hole)
    hd.rounded_rectangle([PX, PY, PX + PW, PY + PH], PR, fill=0)
    if has_callout:
        hd.rounded_rectangle([CX, CY, CX + CW, CY + CH], CR, fill=0)
    r, g, b, a = base.split()
    a = Image.composite(a, Image.new("L", (W, H), 0), hole)
    return Image.merge("RGBA", (r, g, b, a))


def slate(s):
    img = Image.new("RGB", (PW, PH), (20, 30, 26))
    d = ImageDraw.Draw(img)
    d.text((34, 330), s["clip"], font=font(120, bold=True), fill=(240, 196, 64))
    d.text((34, 480), "Record on iPhone", font=font(30, bold=True), fill=PAPER)
    draw_block(d, 34, 540, s["slate"].replace(">", "→"), font(26), (168, 190, 178), PW - 60, 38)
    return img


def cta_v2():
    base = gradient_bg().convert("RGBA")
    d = ImageDraw.Draw(base, "RGBA")
    d.text((150, 270), "Anteroom", font=font(104, bold=True), fill=PAPER)
    d.text((150, 398), "It tells you what it could not read.", font=font(44), fill=BRAND_LT)
    d.text((150, 510), "iOS today  ·  Android, Desktop and Web from the same code",
           font=font(31, bold=True), fill=(150, 176, 163))
    d.text((150, 562), "Kotlin Multiplatform + Compose. RevenueCat purchases-kmp. Gemini 2.5, EU-only.",
           font=font(28), fill=(126, 150, 138))
    d.text((150, 660), "testflight.apple.com/join/EvbxqXGU", font=font(30, mono=True),
           fill=(168, 190, 178))
    d.text((150, 708), "github.com/sorarx27/anteroom-mobile", font=font(30, mono=True),
           fill=(168, 190, 178))
    d.rounded_rectangle([150, 790, 590, 858], 34, fill=(54, 95, 80, 255))
    d.text((186, 808), "RevenueCat Shipaton 2026", font=font(27, bold=True), fill=WHITE)
    caption_bar(base, SHOTS[-1]["cap"])
    return base


def phone_chain(s, stand_in):
    """Filter producing [v]: the phone screen, PW x PH, 30fps, exactly dur."""
    d = s["dur"]
    if stand_in and s.get("v1") is not None:
        x, y, w, h = V1_PHONE
        src = f"[0:v]trim=start={s['v1']}:duration={d},setpts=PTS-STARTPTS,crop={w}:{h}:{x}:{y},"
    elif stand_in:
        src = "[0:v]"                      # looped slate png
    else:
        src = f"[0:v]trim=start={s['ss']}:duration={d},setpts=PTS-STARTPTS,"
    pre = f"scale={PW}:{PH},setsar=1,"
    return (f"{src}{pre}fps=30,tpad=stop_mode=clone:stop_duration={d},"
            f"trim=duration={d},setpts=PTS-STARTPTS[v]")


def callout_y(s, stand_in):
    if stand_in:
        return s.get("callout_v1") if s.get("v1") is not None else None
    return s.get("callout")


def render_shot(s):
    out = os.path.join(SEG, f"{s['id']}.mp4")
    d = s["dur"]
    if s["kind"] == "static":
        img = os.path.join(PIPE, s["img"]) if s["img"] != "st_cta-v2.png" \
            else os.path.join(BUILD, s["img"])
        run(["ffmpeg", "-y", "-v", "error", "-loop", "1", "-t", str(d), "-i", img,
             "-vf", "scale=1920:1080,format=yuv420p", *V, out])
        return "static"

    clip = find_clip(s["clip"])
    stand_in = clip is None
    ov = os.path.join(BUILD, f"ov_{s['id']}.png")
    overlay(s, stand_in).save(ov)
    if not stand_in:
        inp = ["-i", clip]
    elif s.get("v1") is not None:
        inp = ["-i", MASTER_V1]
    else:
        sl = os.path.join(BUILD, f"slate_{s['id']}.png")
        slate(s).save(sl)
        inp = ["-loop", "1", "-t", str(d), "-i", sl]
    cy = callout_y(s, stand_in)
    if cy is None:
        mid = f"[bg][v]overlay={PX}:{PY}:shortest=0[b];"
    else:
        y0 = max(0.0, min(1.0 - BAND, cy - BAND / 2))
        mid = (f"[v]split[v1][v2];"
               f"[v2]crop={PW}:{round(BAND * PH)}:0:{round(y0 * PH)},"
               f"scale={CW}:{CH}:flags=lanczos[z];"
               f"[bg][v1]overlay={PX}:{PY}:shortest=0[b0];"
               f"[b0][z]overlay={CX}:{CY}[b];")
    chain = (phone_chain(s, stand_in) + ";"
             f"color=c=0x0B120F:s=1920x1080:r=30:d={d}[bg];" + mid +
             f"[b][1:v]overlay=0:0,format=yuv420p[out]")
    run(["ffmpeg", "-y", "-v", "error", *inp, "-loop", "1", "-t", str(d), "-i", ov,
         "-filter_complex", chain, "-map", "[out]", "-t", str(d), *V, out])
    return "STAND-IN" if stand_in else os.path.basename(clip)


def main():
    for f in ("st_problem-1.png", "st_problem-2.png", "st_billing-2.png"):
        if not os.path.exists(os.path.join(PIPE, f)):
            run([sys.executable, os.path.join(PIPE, "build_frames.py")])
            break
    cta_v2().save(os.path.join(BUILD, "st_cta-v2.png"))

    missing = []
    for s in SHOTS:
        src = render_shot(s)
        if src == "STAND-IN":
            missing.append(s["clip"])
        print(f"  {s['id']:<12} {s['dur']:>2}s  {src}")

    lst = os.path.join(SEG, "concat.txt")
    with open(lst, "w") as f:
        for s in SHOTS:
            f.write(f"file '{os.path.join(SEG, s['id'] + '.mp4')}'\n")
    silent = os.path.join(BUILD, "anteroom-demo-v2-silent.mp4")
    # Fade in from the first real frame, not from black: the opening must show
    # the documents immediately.
    run(["ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", lst,
         "-vf", f"fade=t=in:st=0:d=0.25,fade=t=out:st={TOTAL - 1.0}:d=1.0",
         *V, "-movflags", "+faststart", silent])
    print(f"\nwrote {silent}  ({TOTAL}s)")

    final = os.path.join(BUILD, "anteroom-demo-v2.mp4")
    lines, t = [], 0
    for i, sh in enumerate(SHOTS, 1):
        hit = sorted(glob.glob(os.path.join(CLIPS, "vo", f"{i:02d}.*")))
        if hit:
            lines.append((hit[0], t, sh["dur"]))
        t += sh["dur"]
    vo = next(iter(sorted(glob.glob(os.path.join(CLIPS, "voiceover.*")))), None)
    if lines:
        # Each line starts 0.2s into its shot and is cut at the shot's end, so a
        # slow read can never bleed into the next scene.
        inp, parts = [], []
        for k, (f, start, dur) in enumerate(lines, 1):
            inp += ["-i", f]
            ms = int((start + 0.2) * 1000)
            parts.append(f"[{k}:a]aresample=48000,aformat=channel_layouts=stereo,"
                         f"silenceremove=start_periods=1:start_threshold=-45dB,"
                         f"atrim=duration={dur - 0.3},adelay={ms}|{ms}[a{k}]")
        mix = "".join(f"[a{k}]" for k in range(1, len(lines) + 1))
        graph = ";".join(parts) + f";{mix}amix=inputs={len(lines)}:normalize=0,"
        graph += "loudnorm=I=-16:TP=-1.5:LRA=11,apad[aout]"
        run(["ffmpeg", "-y", "-v", "error", "-i", silent, *inp,
             "-filter_complex", graph, "-map", "0:v", "-map", "[aout]",
             "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-t", str(TOTAL),
             "-movflags", "+faststart", final])
        print(f"wrote {final}  (narration: {len(lines)} of {len(SHOTS)} lines)")
    elif vo:
        run(["ffmpeg", "-y", "-v", "error", "-i", silent, "-i", vo,
             "-map", "0:v", "-map", "1:a", "-c:v", "copy",
             "-af", "loudnorm=I=-16:TP=-1.5:LRA=11,apad",
             "-c:a", "aac", "-b:a", "192k", "-t", str(TOTAL),
             "-movflags", "+faststart", final])
        print(f"wrote {final}  (narration: {os.path.basename(vo)})")
    else:
        print("no narration yet: silent cut only")
    if missing:
        print("\nstill to record:", ", ".join(missing))


if __name__ == "__main__":
    main()
