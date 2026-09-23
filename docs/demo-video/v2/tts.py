"""Narration for demo v2, one ElevenLabs take per shot.

    python3 tts.py            # all lines
    python3 tts.py 07 11      # just these

Reads the key from $ELEVENLABS_API_KEY or ~/.elevenlabs_key. Writes vo/NN.mp3,
which edit.py picks up. Each line is checked against its shot: a take that
would be cut off is regenerated faster (up to 1.15x) rather than clipped.
"""
import json, os, subprocess, sys, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from edit import SHOTS                     # noqa: E402

VOICE = os.environ.get("ANTEROOM_VOICE", "JBFqnCBsd6RMkjVDRZzb")   # George: calm, British
MODEL = "eleven_multilingual_v2"
OUT = os.path.join(HERE, "vo")
os.makedirs(OUT, exist_ok=True)

# The spoken line can differ from the caption only in pacing marks.
SPOKEN = {
    7: 'It found seven medications. <break time="0.7s" /> And it refused to guess the eighth.',
    6: "Gemini two point five Pro reads both pages, in the EU.",
}


def key():
    k = os.environ.get("ELEVENLABS_API_KEY")
    if not k:
        with open(os.path.expanduser("~/.elevenlabs_key")) as f:
            k = f.read().strip()
    return k


def speak(text, prev, nxt, speed, path):
    body = json.dumps({
        "text": text, "model_id": MODEL,
        "previous_text": prev, "next_text": nxt,
        "voice_settings": {"stability": 0.55, "similarity_boost": 0.8,
                           "style": 0.1, "use_speaker_boost": True, "speed": speed},
    }).encode()
    req = urllib.request.Request(
        f"https://api.elevenlabs.io/v1/text-to-speech/{VOICE}?output_format=mp3_44100_128",
        data=body, headers={"xi-api-key": key(), "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
        f.write(r.read())


def spoken_length(path):
    """Duration with leading/trailing silence removed, as edit.py will place it."""
    r = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", path, "-af",
         "silenceremove=start_periods=1:start_threshold=-45dB,areverse,"
         "silenceremove=start_periods=1:start_threshold=-45dB,areverse",
         "-ac", "1", "-ar", "44100", "-f", "s16le", "-"], capture_output=True)
    return len(r.stdout) / (44100 * 2)


def main(only):
    lines = [SPOKEN.get(i, s["cap"]) for i, s in enumerate(SHOTS, 1)]
    for i, s in enumerate(SHOTS, 1):
        n = f"{i:02d}"
        if only and n not in only:
            continue
        limit = s["dur"] - 0.35
        path = os.path.join(OUT, f"{n}.mp3")
        prev = lines[i - 2] if i > 1 else ""
        nxt = lines[i] if i < len(lines) else ""
        speed = 1.0
        for _ in range(3):
            speak(lines[i - 1], prev, nxt, speed, path)
            got = spoken_length(path)
            if got <= limit:
                break
            speed = min(1.15, round(speed * got / limit + 0.02, 2))
        flag = "" if got <= limit else "  OVER, will be cut"
        print(f"  {n}  {got:4.1f}s / {limit:4.1f}s  speed {speed}{flag}")


if __name__ == "__main__":
    main(set(sys.argv[1:]))
