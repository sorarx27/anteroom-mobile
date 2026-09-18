# Engine decision

**Chosen: ffmpeg compositing, driven by a small Python frame generator.**

The skill's rubric points product demos at Remotion, and for a demo built out
of synthetic UI it would be the right answer. This one isn't. Every frame that
matters is a real screen recording from an Android device signed into the live
Firebase project, or a PDF the deployed `renderBriefPdf` actually produced.
The job is therefore compositing real footage, not animating a UI — and for
that, Remotion's React runtime is a layer between us and the pixels rather
than a help.

| | ffmpeg + PIL | Remotion |
| --- | --- | --- |
| Real screen recordings | native | works, via `<Video>` |
| Motion graphics | weak | excellent |
| Setup cost | zero, already installed | Node project, headless Chrome |
| Deterministic re-render | yes | yes |
| Frame-exact control | yes, via filter graph | yes |

The deciding factor was honesty rather than capability. The storyboard's
rules say no undisclosed speed-ups and real screenshots only, and a pipeline
that can *only* place real footage makes that easy to hold to. There is no
tempting "just animate it" path.

**What we'd switch for.** If a future cut needs an animated architecture
diagram — the client, the four callables, Vertex, and the entitlement
document, revealed one box at a time — that is Motion Canvas's job, and the
storyboard XML ports to it without change. The storyboard is the artifact;
this pipeline is one renderer of it.

## Structure

- `pipeline/design.py` — palette, type scale, gradient, caption bar.
- `pipeline/frames.py` — the device frame and the full-frame layouts.
- `pipeline/build_frames.py` — renders every static frame.
- `pipeline/shots.py` — the edit list: one entry per `<shot>` in the storyboard.
- `pipeline/render.py` — one 1920x1080/30fps segment per shot, then concat.

## One thing that cost an hour

`adb screenrecord` writes a variable-frame-rate file that reports
`r_frame_rate=90000/1`. Seeking it — with `-ss` before the input *or* after —
landed up to five seconds away from the requested timestamp, which silently
put the wrong screen in three shots. The fix is two steps: normalise each take
to constant 30fps first, then trim inside the filter graph rather than with
`-ss`. Don't trust a timestamp on a raw screenrecord file.
