"""Builds a narration track timed to the shot list, then muxes it on.

The caption text and the spoken line are the same string, so the two can
never drift. Each line is fitted to its shot by adjusting speaking rate, not
by clipping audio.
"""
import os, subprocess, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from shots import SHOTS

HERE = os.path.dirname(os.path.abspath(__file__))
AUD = os.path.join(HERE, "vo"); os.makedirs(AUD, exist_ok=True)
VOICE = "Daniel"          # en_GB, measured cadence; suits a clinical register
BASE_RATE = 158           # words per minute
LEAD_IN = 0.30            # let the cut land before the line starts

def dur(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries",
                          "format=duration", "-of", "csv=p=0", path],
                         capture_output=True, text=True).stdout.strip()
    return float(out)

def synth(text, path, rate):
    subprocess.run(["say", "-v", VOICE, "-r", str(int(rate)), "-o", path, text],
                   check=True, capture_output=True)

clips, t = [], 0.0
for sh in SHOTS:
    line = sh.get("cap")
    if not line:
        t += sh["dur"]; continue

    budget = sh["dur"] - LEAD_IN - 0.25
    raw = os.path.join(AUD, f"{sh['id']}.aiff")
    synth(line, raw, BASE_RATE)
    d = dur(raw)

    # Speak faster only as far as needed, and never past 205 wpm - beyond
    # that the delivery stops sounding like a person reading.
    if d > budget:
        rate = min(205, BASE_RATE * (d / budget) * 1.02)
        synth(line, raw, rate)
        d = dur(raw)
        if d > budget:
            print(f"  ! {sh['id']}: {d:.1f}s into a {budget:.1f}s slot")

    clips.append((t + LEAD_IN, raw, d, sh["id"]))
    t += sh["dur"]

total = t
print(f"{len(clips)} lines, timeline {total:.0f}s")

# One filter graph: pad every clip to its start, mix, normalise.
inputs, parts, labels = [], [], []
for i, (start, path, d, sid) in enumerate(clips):
    inputs += ["-i", path]
    parts.append(f"[{i}:a]aresample=48000,adelay={int(start*1000)}|{int(start*1000)},"
                 f"volume=1.0[a{i}]")
    labels.append(f"[a{i}]")
graph = (";".join(parts) + ";" + "".join(labels) +
         f"amix=inputs={len(clips)}:normalize=0:dropout_transition=0[mix];"
         f"[mix]loudnorm=I=-16:TP=-1.5:LRA=11,apad,atrim=0:{total}[out]")

wav = os.path.join(AUD, "narration.wav")
subprocess.run(["ffmpeg", "-y", "-v", "error", *inputs, "-filter_complex", graph,
                "-map", "[out]", "-ar", "48000", "-ac", "2", wav], check=True)
print("narration:", dur(wav), "s")

src = os.path.join(HERE, "anteroom-demo-silent.mp4")
out = os.path.join(HERE, "anteroom-demo.mp4")
subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", src, "-i", wav,
                "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                "-movflags", "+faststart", out], check=True)
print("wrote", out, dur(out), "s")
