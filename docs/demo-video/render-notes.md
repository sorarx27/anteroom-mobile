# Render notes

## Output

Two files, same picture:

| File | Audio | Use |
| --- | --- | --- |
| `anteroom-demo.mp4` | narrated | the one to upload |
| `anteroom-demo-silent.mp4` | none | re-voice from this |

1920x1080, 30fps, H.264 CRF 17, 91s. Captions are burned in either way,
because plenty of judges watch sound-off.

The narration is macOS `say` (Daniel, en_GB), timed per shot by
`pipeline/voice.py`. It is clear and correctly placed, and it is obviously
synthetic — replacing it with a real read is the single cheapest upgrade this
video has left:

```
ffmpeg -i anteroom-demo-silent.mp4 -i your-vo.wav \
       -c:v copy -c:a aac -b:a 192k anteroom-demo.mp4
```

## Rebuilding

```
cd docs/demo-video/pipeline
python3 build_frames.py     # static frames
python3 render.py           # segments -> anteroom-demo-silent.mp4
python3 voice.py            # narration -> anteroom-demo.mp4
```

Both need Pillow and ffmpeg. `render.py` expects the three source recordings
at the paths in `shots.py`; they are not committed (140 MB of raw capture).

## Re-capturing the source footage

Recorded on an Android emulator signed into the live project. The app must be
a build **without** the QA launch arguments, which were removed before this
was shot.

```
adb shell screenrecord --bit-rate 12000000 --time-limit 120 /sdcard/take1.mp4
# drive the UI with `adb shell input tap`, then
adb pull /sdcard/take1.mp4
ffmpeg -i take1.mp4 -vf fps=30 -c:v libx264 -crf 16 take1_cfr.mp4   # required
```

The `_cfr` step is not optional — see `engine-decision.md`.

| Take | Covers |
| --- | --- |
| `take1` | Dashboard, capture, gallery pick, upload, classification |
| `take2` | Generate, the finished brief, the medication list |
| `take3` | Page inspector, PDF export and share sheet, paywall |

## Deliberate choices

- **Nothing is sped up.** The one trim — about 18 seconds of model time in
  `generate-2` — is labelled on screen rather than hidden.
- **The Android paywall is not in the video.** It currently renders a
  RevenueCat error because no Play Store products are registered yet, so the
  billing scene uses the real iOS paywall capture instead. Re-shoot that
  scene on Android once the Play Console products exist.
- **No stock footage, no mockups, no invented UI.** Every phone frame is a
  recording of the app against the live backend.
- **No music.** A bed would mask the flatness of the synthetic voice, but it
  is not something that can be judged without listening, so it was left out
  rather than guessed at. Add one when you re-record.
- **Lines are fitted by speaking rate, never by clipping.** Where a line
  would not fit its shot, the line was shortened — in the storyboard, the
  caption and the narration together, so the three cannot disagree.
