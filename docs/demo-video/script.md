# Anteroom demo - voice-over script

Ninety-one seconds. Lines are one-for-one with `<voiceover>` in
`storyboard.xml`, and the timings are the cut as rendered.

The master render carries **burned-in captions and no audio**, because most
judges watch sound-off. Record this over the top if you want narration; the
caption text is the same words, so the two cannot drift.

Read at roughly 2.5 words per second. Where a line is short, let the shot
breathe rather than filling it.

| In | Out | Shot | Line |
| --- | --- | --- | --- |
| 0.0s | 5.0s | `problem-1` | In Spain the appointment is ten minutes and the paperwork is a folder. |
| 5.0s | 9.0s | `problem-2` | Some of it is unreadable. A guessed dose is worse than none. |
| 9.0s | 14.0s | `hero-1` | Anteroom turns that folder into one structured brief before the visit starts. |
| 14.0s | 21.0s | `capture-1` | Photograph the paperwork, or pick it from the library. |
| 21.0s | 27.0s | `capture-2` | They upload to your account, and you can see exactly what you captured. |
| 27.0s | 34.0s | `classify-1` | Gemini Flash types the document. You can override it in one tap. |
| 34.0s | 39.0s | `generate-1` | Generate. Gemini 2.5 Pro reads both pages, in the EU. |
| 39.0s | 44.0s | `generate-2` | Eighteen seconds later, the brief is there. |
| 44.0s | 51.0s | `flag-1` | It found seven medications. And it refused to guess the eighth. |
| 51.0s | 57.0s | `flag-2` | Every other drug has its dose, verbatim. Warfarin has none, because the page did not. |
| 57.0s | 62.0s | `flag-3` | And the original page is one tap away, so any line can be checked. |
| 62.0s | 68.0s | `export-1` | Export a PDF for the clinic. On the free tier the server watermarks it. |
| 68.0s | 73.0s | `export-2` | Pro removes the watermark and renders it in Spanish. Doses are never translated. |
| 73.0s | 78.0s | `billing-1` | Billing is RevenueCat's Kotlin Multiplatform SDK - one implementation, Play and StoreKit. |
| 78.0s | 83.0s | `billing-2` | A RevenueCat webhook writes the entitlement, and the server decides. The client cannot unlock itself. |
| 83.0s | 91.0s | `cta-1` | Anteroom. Android, iOS, desktop and web, from one Kotlin codebase. |

## Notes for the read

- The payoff is `flag-1`. Slow down there; it is the only claim in the
  video a judge cannot get from any other submission.
- Do not say "AI reads it perfectly". The whole point is the opposite:
  it read seven of eight and said so.
- `generate-2` carries an on-screen label saying the model time was
  trimmed. Do not imply it was instant.
