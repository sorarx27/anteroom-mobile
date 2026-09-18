"""Renders each shot to a normalised 1920x1080/30fps segment, then concatenates."""
import os, subprocess, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shots import SHOTS
from frames import phone_overlay, PX, PY, PW, PH

HERE = os.path.dirname(os.path.abspath(__file__))
SEG = os.path.join(HERE, "seg"); os.makedirs(SEG, exist_ok=True)
OUT = os.path.join(HERE, "anteroom-demo.mp4")

V = ["-c:v", "libx264", "-preset", "medium", "-crf", "17",
     "-pix_fmt", "yuv420p", "-r", "30", "-an"]

def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print("FFMPEG FAILED:", " ".join(cmd[:14]), "...")
        print(r.stderr[-1600:]); sys.exit(1)

def overlay_for(s):
    path = os.path.join(HERE, f"ov_{s['id']}.png")
    phone_overlay(s.get("eyebrow"), s["head"], s.get("sub"), s["cap"],
                  badge=s.get("badge")).save(path)
    return path

for s in SHOTS:
    out = os.path.join(SEG, f"{s['id']}.mp4")
    d = s["dur"]

    if s["kind"] == "static":
        run(["ffmpeg", "-y", "-v", "error", "-loop", "1", "-t", str(d),
             "-i", os.path.join(HERE, s["img"]),
             "-vf", "scale=1920:1080,format=yuv420p", *V, out])

    else:
        ov = overlay_for(s)
        take = s.get("take", d)
        # Trim inside the graph rather than with -ss on the input. Input
        # seeking snaps to a keyframe, and on these recordings that landed up
        # to a second early -- which put the wrong screen in two shots.
        pad = ("" if s["kind"] != "hold"
               else f",tpad=stop_mode=clone:stop_duration={d - take}")
        chain = (
            f"[0:v]trim=start={s['ss']}:duration={take},setpts=PTS-STARTPTS,"
            f"scale={PW}:{PH},setsar=1{pad},fps=30,trim=duration={d},setpts=PTS-STARTPTS[v];"
            f"color=c=0x0B120F:s=1920x1080:r=30:d={d}[bg];"
            f"[bg][v]overlay={PX}:{PY}:shortest=0[b];"
            f"[b][1:v]overlay=0:0,format=yuv420p[out]"
        )
        run(["ffmpeg", "-y", "-v", "error",
             "-i", s["src"],
             "-loop", "1", "-t", str(d), "-i", ov,
             "-filter_complex", chain, "-map", "[out]", "-t", str(d), *V, out])

    print("  rendered", s["id"], f"{d}s")

lst = os.path.join(SEG, "concat.txt")
with open(lst, "w") as f:
    for s in SHOTS:
        f.write(f"file '{os.path.join(SEG, s['id'] + '.mp4')}'\n")

total = sum(s["dur"] for s in SHOTS)
run(["ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", lst,
     "-vf", f"fade=t=in:st=0:d=0.6,fade=t=out:st={total - 1.0}:d=1.0",
     *V, "-movflags", "+faststart", OUT])
print("\nwrote", OUT)
