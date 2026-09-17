# Demo video beat sheet

This is the shot-by-shot script for Anteroom's two-minute Ship-a-ton demo.
It runs 120 seconds and covers five beats: intake chaos, deterministic brief
generation with Spanish translation, the paywall trigger, a RevenueCat
sandbox purchase, and a clean PDF export.

Read the preparation checklist before you record. Several beats depend on
state you must set up in advance, and one of them — the sandbox purchase —
can't be repeated on the same store account without a reset.

## Format

| Item | Value |
| --- | --- |
| Total runtime | 2:00 |
| Aspect ratio | 16:9, 1080p minimum |
| Primary capture | Android physical device screen recording |
| Secondary capture | iOS simulator, for the cross-platform beat |
| Voiceover | Recorded separately, then laid under the screen capture |
| Captions | Burned in, since judges often watch muted |

## Preparation checklist

Complete every item before you hit record.

1. Confirm the RevenueCat dashboard has the `anteroom_pro` entitlement
   attached to the `default` offering, with both `$rc_monthly` and
   `$rc_lifetime` packages active.
2. Sign the Android device into a Google Play license tester account, or
   confirm the build uses the RevenueCat Test Store key.
3. Verify the account has **no** active `anteroom_pro` entitlement. If a
   previous test granted it, clear the purchase in the RevenueCat dashboard
   first.
4. Launch the app once and confirm the paywall shows live prices, not the
   `$7.99/mo` and `$129.99` fallback values. Fallback pricing on camera
   means the offerings fetch failed.
5. Stage the physical props for the opening shot: a paper referral, a lab
   printout, and two or three medication boxes.
6. Create the family profiles in advance so the demo doesn't spend time on
   data entry.
7. Silence notifications and enable do-not-disturb on the recording device.

> **Warning:** A sandbox purchase is consumed once you make it. Do a full
> dry run with a second test account, then reset before the real take.

## Beat 1 — Intake chaos (0:00–0:18)

Open on the problem, physically, before any UI appears. The viewer must feel
the paper before they see the software.

- **0:00–0:06 — Shot:** Overhead, real desk. Hands spread a paper referral,
  a lab printout, and three medication boxes across the surface. No app on
  screen.
- **0:06–0:12 — Shot:** Cut to a waiting-room-style wide shot, person
  holding the folder, checking the time.
- **0:12–0:18 — Shot:** Hard cut to the Anteroom dashboard on the device.
  Family profile strip visible at the top.

**Voiceover:**

> A ten-minute appointment in Spain. Half of it goes to the doctor reading
> paperwork in three languages. The patient brought everything. Nobody
> organized any of it. This is the anteroom problem — and it's solvable
> before the consultation ever starts.

**Capture notes:** Keep the desk shot tight and busy. The contrast between
the clutter and the first clean app screen is doing the argument for you.

## Beat 2 — Capture and brief generation (0:18–0:52)

Show the app doing the triage work, and make the flagging behavior the hero.
This is the beat that separates Anteroom from a document scanner.

- **0:18–0:24 — Screen:** Tap into the child profile. Tap **Snap
  paperwork**. Capture the referral and the medication list.
- **0:24–0:30 — Screen:** The classifier chip resolves to **Referral** with
  a confidence label. Tap a different chip to show the manual override, then
  tap back.
- **0:30–0:38 — Screen:** Tap generate. Let the processing dialog play. The
  brief view opens with patient details, referral reason, medications with
  doses, and allergies.
- **0:38–0:52 — Screen:** Scroll to the red **Flagged for clinical review**
  banner. Hold on it. Then tap into a flagged line and open the original
  photo in the verification view, showing the source document behind the
  claim.

**Voiceover:**

> Photograph the paperwork. Anteroom types each document, then collapses all
> of it into one structured brief — patient, referral reason, medications,
> doses, allergies.
>
> Here's the part that matters clinically. The referral says twenty
> milligrams of lisinopril. The bottle says ten. Anteroom doesn't pick one.
> It flags the conflict and keeps the original photograph attached, so the
> doctor resolves it in a single tap. A wrong dose delivered confidently is
> more dangerous than no dose at all.

**Capture notes:** Hold on the flagged banner for a full two seconds. Do not
speed-ramp through the verification tap — that tap is the credibility of the
whole product.

## Beat 3 — Spanish translation (0:52–1:08)

Show the language bottleneck closing. Keep it fast; the point lands
visually.

- **0:52–0:58 — Screen:** Open the language selector on the brief. Tap
  **Español**.
- **0:58–1:04 — Screen:** The brief re-renders in Spanish. Show the
  translated referral reason and the translated flagged items next to the
  language chip.
- **1:04–1:08 — Screen:** Quick cut to the same brief running on the iOS
  simulator, identical layout.

**Voiceover:**

> One tap, and the same brief is in the doctor's language. The flags
> translate too — an ambiguity in English stays an ambiguity in Spanish.
>
> Same screen on iOS. One Compose Multiplatform codebase, both platforms.

**Capture notes:** Frame the iOS cut so the visual match with Android is
obvious. This three-second shot is the Kotlin Multiplatform Reach Award in
one image.

## Beat 4 — Paywall and sandbox purchase (1:08–1:42)

This is the RevenueCat beat. Show a real gate, a real offering, and a real
sandbox transaction.

- **1:08–1:14 — Screen:** Go back to the dashboard. Tap a locked family
  profile avatar, or tap **Add profile**. The paywall opens.
- **1:14–1:22 — Screen:** Scroll the paywall slowly. Show the three Pro
  features, then both packages with their live prices from RevenueCat.
- **1:22–1:26 — Screen:** Select the lifetime package. Tap **Continue**.
- **1:26–1:36 — Screen:** The native Google Play sandbox sheet appears.
  Complete the purchase. Do not cut away — the store sheet on camera is the
  proof.
- **1:36–1:42 — Screen:** The paywall closes on its own. The dashboard
  re-renders with every lock removed: family avatars unlocked, upgrade
  banner gone.

**Voiceover:**

> Adding a family member is a Pro feature. The paywall pulls its packages
> and its prices straight from RevenueCat — nothing hardcoded.
>
> Lifetime. Purchase. That's the real sandbox transaction.
>
> And watch the whole app change at once. Anteroom verifies the
> `anteroom_pro` entitlement from customer info, pushes it into one shared
> state flow, and every lock in the UI reads from that flow. The paywall
> closed itself because the entitlement turned on, not because a button was
> tapped.

**Capture notes:** The unlock cascade in the final six seconds is the
strongest technical moment in the video. Frame the dashboard so at least two
locked avatars and the upgrade banner are all visible before the purchase,
so the change is unmissable.

## Beat 5 — Clean PDF export (1:42–2:00)

Close the loop. The output is the thing the doctor actually receives.

- **1:42–1:48 — Screen:** Open the brief. Tap the export row. Note that it
  now reads **Download doctor-ready PDF** instead of **Download PDF
  (watermarked)**.
- **1:48–1:54 — Screen:** The clean PDF opens. Pan down it once: structured
  sections, flagged items preserved, no watermark.
- **1:54–2:00 — Shot:** Cut back to the opening desk. The paper is stacked
  and pushed aside; a phone showing the brief sits on top. End card with the
  app name, the platforms, and the repository URL.

**Voiceover:**

> Pro exports clean. The doctor gets one page instead of a folder —
> structured, in their language, with the uncertainties marked rather than
> hidden.
>
> Anteroom. Built with Kotlin Multiplatform and RevenueCat, by a physician
> who has been on the other side of that desk for twelve years.

**Capture notes:** The closing desk shot must match the opening framing
exactly. Shoot both in the same session so the lighting matches.

## Shot list summary

| Time | Beat | Capture source |
| --- | --- | --- |
| 0:00–0:18 | Intake chaos | Live-action desk and waiting room |
| 0:18–0:52 | Capture, generate, flag | Android screen recording |
| 0:52–1:08 | Translation and iOS parity | Android, then iOS simulator |
| 1:08–1:42 | Paywall and sandbox purchase | Android screen recording |
| 1:42–2:00 | Clean PDF export and close | Android, then live-action desk |

## Accuracy guardrails

Keep the narration defensible. Judges test claims.

- Don't say "AI extracts" over the generation beat. Document classification,
  generation, and translation currently return deterministic sample content.
  Say "Anteroom collapses all of it into one structured brief," which is
  true of what is on screen.
- Don't call the flagging a model output. It's a schema guarantee, and
  that's the stronger claim anyway.
- Don't record the paywall beat on Desktop or Web. Those targets run
  `SimulatedRevenueCatService` and label themselves as a simulation on
  screen.
- Do show the real store sheet. A recorded sandbox purchase is the single
  most load-bearing frame in the submission.

## Next steps

- Draft the voiceover as a single continuous read, then cut the screen
  capture to it rather than the reverse.
- Review the claims in this script against
  [the Devpost submission](./DEVPOST_SUBMISSION.md) before publishing, so
  the written entry and the video agree.
