# Demo video

Superseded by [`docs/demo-video/`](./demo-video/), which holds the storyboard,
the rendered master, and the build pipeline.

| File | What it is |
| --- | --- |
| [`demo-video/anteroom-demo.mp4`](./demo-video/anteroom-demo.mp4) | The cut. 1920x1080, 30fps, 91s, burned-in captions, no audio. |
| [`demo-video/storyboard.xml`](./demo-video/storyboard.xml) | Source of truth. Edit this, not the renderer. |
| [`demo-video/script.md`](./demo-video/script.md) | Voice-over, timed to the cut. |
| [`demo-video/captions.srt`](./demo-video/captions.srt) | Matches the burned-in captions exactly. |
| [`demo-video/engine-decision.md`](./demo-video/engine-decision.md) | Why ffmpeg and not Remotion. |
| [`demo-video/qa-checklist.md`](./demo-video/qa-checklist.md) | The pre-ship pass, including two deliberate deviations. |
| [`demo-video/render-notes.md`](./demo-video/render-notes.md) | How to rebuild it and how to re-capture the footage. |

## Why this file changed

The original beat sheet told the narrator **not** to say "AI extracts",
because classification, generation and translation returned deterministic
sample content. That was true when it was written and is now false: the
pipeline runs Gemini 2.5 Pro and 2.5 Flash on Vertex AI in `europe-west1`,
and the flagged Warfarin dose in the video is a real model output.

Keeping a guardrail that talks you out of your strongest true claim is worse
than having no guardrail, so the accuracy rules now live in
[`qa-checklist.md`](./demo-video/qa-checklist.md) next to the evidence for
each one.

## The claims the video makes, and where they are checked

| Claim on screen | Checked by |
| --- | --- |
| Extraction is a real model call | `tools/verify_pipeline.py`, 43 checks |
| The unreadable dose is flagged, not guessed | Same suite, asserted both ways |
| Translation cannot alter a dose | `functions/tests/test_pipeline.py` |
| The clean export is a server decision | `renderBriefPdf` takes no watermark argument |
| Cross-user data is unreachable | `tools/verify_rules.py`, 20 checks |

## Still to do before submitting

- Re-shoot the billing scene on Android once Play Console products exist. The
  Android paywall currently shows a RevenueCat "no products registered" error,
  so the cut uses the real iOS paywall capture instead.
- Optionally record the voice-over in `script.md` and mux it on; the master is
  deliberately silent and caption-led.
