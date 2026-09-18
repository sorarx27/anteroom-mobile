# QA checklist — anteroom-demo

Walked end to end against the rendered master, not against intentions.
Anything marked ✗ is a real deviation with a reason, not an oversight.

## Structural

- [x] `validate_storyboard.py` passes with no warnings.
- [x] Root `<video>`, `<metadata>` complete, 9 scenes, 16 shots.
- [x] Every shot has `<visual>`, `<text>`, `<motion>`, `<voiceover>`.
- [x] Scene durations sum to 91s, matching `duration_seconds`.
- [x] Shot durations sum to their scene duration exactly.
- [x] Rendered master is 91.000s — the edit matches the storyboard, shot for shot.

## Narrative

- [x] Payoff in one sentence: *it found seven medications and refused to guess
      the eighth.*
- [x] First three seconds are the two real documents with the blotted dose, not
      a logo or a title card.
- [x] Each scene has one intent.
- [x] `metadata.intent` is answered by `flag-1`..`flag-3` and restated in the CTA.
- [x] No filler scenes.

## Visual / motion

- [x] Shortest shot 4s, longest 8s. No shot under 1s.
- [x] The two 7s and one 8s shots carry internal motion (live screen recording,
      or a scroll) rather than sitting still.
- [x] Never more than one thing animating at a time.
- [x] Product shots are real screen recordings. No invented UI anywhere.
- [x] No `TODO: capture` left in the storyboard.

## Voice / text

- [x] Every `<voiceover>` line fits its shot at ≤3.0 words/sec.
- [x] On-screen headlines are ≤7 words.
- [x] The voice-over does not read the headline aloud — the headline is the
      claim, the caption is the sentence.
- [x] `captions.srt` exists and matches the burned-in captions exactly.

## Captions

- [x] 16 cues, one per shot, never two on screen at once.
- [x] Worst reading speed 17.0 chars/sec, at the guideline. The `billing-1`
      caption was rewritten to get there — it was 18.9.
- [x] No cue under 1.0s.
- [ ] ✗ Four cues run 6.7–7.7s, over the 6.0s guideline. **Deliberate.** These
      captions are part of the frame composition and are timed to the shot, so
      cutting them early would leave the lower third empty mid-shot. Their
      reading speeds are 7–10 chars/sec, so nobody is straining to keep up —
      the cue is simply present longer than it needs to be.
- [x] Cues break at sentence boundaries; none splits a clause across a cut.
- [x] Checked against the render at 1.5×.

## Honesty

- [x] No speed-ups. The one trim — ~18s of model time in `generate-2` — is
      labelled on screen.
- [x] No mocks. Every phone frame is a recording of the app against the live
      Firebase project and the deployed Cloud Functions.
- [x] Both PDFs are real output from the deployed `renderBriefPdf`.
- [x] The terminal frame reproduces real output from `tools/verify_pipeline.py`.
- [x] Claims audited:
      - "Gemini 2.5 Pro / Flash, europe-west1" — `functions/anteroom/config.py`.
      - "seven medications, eighth not guessed" — visible on screen, and
        asserted by the pipeline suite.
      - "the client cannot unlock itself" — `renderBriefPdf` takes no
        watermark argument; the suite shows `PERMISSION_DENIED` for a free
        caller.
      - No performance or accuracy benchmark is claimed anywhere.
- [x] Fonts are system Arial and SF Mono. The only logo is Anteroom's own.

## Known gap, carried deliberately

- [ ] ✗ The billing scene uses the **iOS** paywall capture. The Android paywall
      currently renders a RevenueCat error — no Play Store products are
      registered for the offering yet — so it is not fit to show. Re-shoot
      `billing-1` on Android once the Play Console products exist; the iOS
      capture is real and correctly priced in the meantime.
