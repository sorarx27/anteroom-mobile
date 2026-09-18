# Render notes

## Output

`anteroom-demo.mp4` — 1920x1080, 30fps, H.264 CRF 17, **no audio**, 91s, ~2 MB.

Captions are burned in, because demo videos are watched sound-off. If you
record the voice-over in `script.md`, mux it on rather than re-rendering:

```
ffmpeg -i anteroom-demo.mp4 -i vo.wav -c:v copy -c:a aac -shortest anteroom-demo-vo.mp4
```

## Rebuilding

```
cd docs/demo-video/pipeline
python3 build_frames.py     # static frames
python3 render.py           # segments, then the concatenated master
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
