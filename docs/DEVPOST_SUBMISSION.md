# Anteroom — Devpost submission

Anteroom turns the pile of paper a patient carries into a doctor's office
into one structured, bilingual clinical brief. It's a Kotlin Multiplatform
app with a 100% Compose Multiplatform UI on iOS and Android, monetized end
to end with the official RevenueCat `purchases-kmp` SDK.

This document is the working copy of the RevenueCat Ship-a-ton 2026
submission. It covers the problem, the build, exactly what runs today, and
what doesn't.

- **Submission deadline:** October 1, 2026
- **Repository branch:** `kotlin`
- **Bundle ID:** `com.zayedmd.anteroom`

## Elevator pitch

Anteroom converts a shoebox of referrals, lab printouts, and medication
boxes into one structured brief your doctor can read in 30 seconds — in
English or Spanish, with every claim traceable to the original photo.

## Inspiration

In Spain, the appointment is short and the paperwork is long. An expat or a
retiree arrives at a consulta with a plastic folder: a referral from a GP in
another country, a lab printout in a third language, three medication boxes,
and a memory of what a specialist said eight months ago. The physician
spends the first half of a 10-minute slot doing document triage instead of
medicine.

The friction is specific, and it has two halves:

- **The intake bottleneck.** Nobody has structured the patient's history
  before the consultation starts. The doctor does it live, under time
  pressure, from paper.
- **The language bottleneck.** The patient's documents and the doctor's
  working language often differ. Translation happens ad hoc, by a family
  member, at the worst possible moment.

Anteroom moves both jobs out of the consultation room and into the waiting
room — the anteroom. That's the name.

The clinical framing comes from 12 years of emergency and general practice.
The failure mode we care most about isn't a missing document; it's a
confidently wrong one. A medication dose transcribed incorrectly is more
dangerous than a medication that was never transcribed at all.

## What it does

Anteroom gives one family a shared, structured medical record they control.

- **Capture.** Photograph referrals, lab results, and medication lists with
  the device camera, or import from the photo library.
- **Classify.** Each capture is typed as a referral, medication list, lab
  result, or other, with a confidence level. You can override the
  classification with one tap.
- **Generate a brief.** The captures collapse into a single `BriefContent`
  record: patient identifiers, referral reason, medications with dose and
  frequency, allergies with reactions, and a list of flagged items.
- **Flag, don't guess.** Anything ambiguous or contradictory lands in
  `flagged_items` and renders as a red "Flagged for clinical review" banner
  instead of being silently resolved. A dose that reads 20 mg on the
  referral and 10 mg on the bottle becomes a flag, not an answer.
- **Keep the source.** Original photos stay attached to the brief, and a
  photo verification view lets the doctor check any line against the paper
  it came from.
- **Translate.** A brief generated in English can be rendered in Spanish,
  and the reverse, with the source language recorded on the record.
- **Manage a family.** Profiles cover you, a partner, children, and parents,
  each with their own briefs.
- **Export.** Produce a PDF for the clinic. The free tier watermarks it; Pro
  doesn't.

### The zero-hallucination design constraint

"Zero-hallucination" is a constraint on the data model, not a claim about a
model's accuracy. Three decisions enforce it:

1. **A closed schema.** `BriefContent` has fixed fields. There is no
   free-text summary field for a model to fill with plausible prose, so
   there's nothing for a narrative hallucination to occupy.
2. **Uncertainty is a first-class value.** `flagged_items`,
   `detected_confidence`, and `doc_type_manual_override` exist so the app
   can say "these two documents disagree" rather than picking a winner.
3. **Provenance survives.** Extracted fields never replace the source. The
   original photographs remain on the brief and are reachable from the
   clinical view, so every line is falsifiable in one tap.

A clinician doesn't need the app to be right. A clinician needs to know,
fast, where the app is unsure.

## How we built it

The whole product is one Kotlin Multiplatform codebase.

| Layer | Technology |
| --- | --- |
| UI | Compose Multiplatform 1.12, Material 3, shared across all targets |
| Shared logic | Kotlin Multiplatform 2.4.20 |
| Targets | Android, iOS (arm64 and simulator), Desktop (JVM), Web (JS) |
| Auth and data | Firebase Auth, Firestore, and Storage through GitLive |
| Billing | RevenueCat `purchases-kmp` 3.8.0 |
| Networking | Ktor client |

There is no per-platform UI code. `App.kt` is the single entry point that
`MainActivity` on Android, `MainViewController` on iOS, the Compose desktop
window, and the web viewport all render. Screens, the paywall, the family
profile strip, and the clinical brief view are written once.

### The RevenueCat integration

The entitlement is `anteroom_pro`. The `default` offering carries two
packages: `$rc_monthly` and `$rc_lifetime`.

The interesting engineering problem is that `purchases-kmp` publishes
artifacts for Android and Apple targets only, while Anteroom also ships
Desktop and Web. Putting the SDK in `commonMain` breaks the JVM and JS
compilations.

The solution is a small expect/actual seam in the `subscription` package
under `app/shared/src`:

- `RevenueCatService` is the common interface: four `StateFlow`s plus
  `initialize`, `fetchOfferings`, `purchasePackage`, `restorePurchases`,
  and `refreshCustomerInfo`.
- `expect fun createRevenueCatService()` picks the implementation per
  target.
- The store-backed `RevenueCatServiceImpl` lives in `src/mobileMain/kotlin`,
  which is compiled into both `androidMain` and `iosMain`. One
  implementation, two stores, zero duplication — Google Play billing and
  StoreKit run the same Kotlin.
- Desktop and Web get `SimulatedRevenueCatService`, which keeps the paywall
  navigable on a laptop and reports `isSimulated = true` so the UI says out
  loud that no charge occurred.

Entitlement state has exactly one source of truth. `applyEntitlement()`
reads `customerInfo.entitlements["anteroom_pro"]?.isActive` and pushes the
result into the app-wide `SubscriptionService`. Everything else in the app —
locked family profile avatars, the watermark notice on export, the
translation lock, the upgrade banner — observes that flow. Nothing in the UI
sets the entitlement directly.

Because the app registers a `PurchasesDelegate`, entitlement changes that
originate outside the paywall propagate on their own: a renewal, a lapse, a
restore performed on another device, or a promoted App Store purchase. If
the paywall happens to be open when `anteroom_pro` turns on, it closes
itself.

Failures are handled as distinct cases rather than one generic error:

- A user dismissing the store sheet is a cancellation, not an error, and
  shows no red text.
- A purchase that succeeds without granting the entitlement says so and
  points you at **Restore purchases**.
- A restore that finds nothing says it found nothing instead of silently
  unlocking.
- An unreachable store falls back to dashboard pricing so the paywall still
  renders, and surfaces the reason.

## What runs today

Being precise about this matters more than the pitch does.

**Live and real:**

- The complete Compose Multiplatform UI across Android, iOS, Desktop, and
  Web.
- RevenueCat billing through `purchases-kmp`: offerings fetch, package
  purchase, restore, customer info refresh, and delegate-driven entitlement
  updates, all verified against `anteroom_pro`.
- Firebase authentication, Firestore persistence, and Storage photo upload.
- Family profile management, document capture, and the clinical brief view
  with flagged-item banners and photo verification.
- Every paywall gate in the app, driven by the verified entitlement.

**Stubbed, and honest about it:**

- Document classification, brief generation, and translation currently
  return fixed sample content behind a delay in `BriefsRepositoryImpl`. The
  schema, the flagging behavior, and the full user journey are real; the
  extraction model behind them isn't wired yet.
- The Ktor server module is a scaffold.

> **Note:** The demo video shows the real app and the real RevenueCat
> sandbox purchase. The extraction step it demonstrates is deterministic
> sample content, not a live model call.

## Challenges we ran into

- **A multiplatform SDK that isn't available on every platform.**
  Reconciling `purchases-kmp`'s Android-and-Apple artifact set with a
  four-target app took the shared-source-directory approach described above.
  The obvious fix — an intermediate source set wired with `dependsOn()` —
  disables Kotlin's default hierarchy template and breaks `iosMain`.
- **Two SDKs configuring one singleton.** An earlier build configured the
  native Android RevenueCat SDK in `MainActivity` while `purchases-kmp`
  pulled in a different major version of the same library. Removing the
  native call and letting shared code configure once fixed both the version
  conflict and the double configuration.
- **Designing for uncertainty.** The hard product question wasn't extraction
  accuracy. It was what the screen does when two documents disagree. Showing
  both and flagging the conflict took more UI work than picking one would
  have.

## Accomplishments we're proud of

- One `RevenueCatService` implementation drives billing on both Google Play
  and StoreKit, with no platform branches in the calling code.
- A paywall that reacts to entitlement state rather than to button taps, so
  it behaves correctly when a subscription changes somewhere else.
- A clinical data model that treats "we don't know" as information worth
  rendering.

## What we learned

`purchases-kmp` collapses two native billing integrations into one Kotlin
surface, and the remaining work is almost entirely about state, not stores.
The lesson that transferred best from clinical practice: the value of an
intake document is not how much it asserts, it's how clearly it marks what
it can't confirm.

## What's next

- Wire the extraction pipeline behind the existing schema, with the flagging
  contract as an acceptance test rather than an afterthought.
- Validate the brief format with clinicians in Valencia, as a student
  research project.
- Expand language coverage beyond English and Spanish.
- Ship the QR handoff so a clinic can open a brief without an install.

## Award tracks

- **Next Gen / Student Award.** Anteroom is submitted with a working demo
  and a public open-source repository, built by an actively enrolled
  Master's student.
- **Kotlin Multiplatform Reach Award.** 100% Compose Multiplatform UI across
  iOS and Android, with Desktop and Web from the same codebase, monetized
  with the official `purchases-kmp` SDK.
- **Most Likely to Make Money.** The clinical intake and language bottleneck
  for expats and retirees seeing doctors in Spain is a recurring,
  paid-for problem with a clear willingness to pay at the household level.

## Team

- **Dr. Ahmed Zayed** — Medical Advisor, Clinical Co-founder, and Student
  Researcher. MBBCh, Alexandria University, 2013. Twelve years in clinical
  practice. Currently enrolled in a Master's program at Universitat
  Politècnica de València.
- **Gerhard Homveld** — Business entity and app publishing, through a US
  entity.

## Build and run

The app builds from the `kotlin` branch with the standard Gradle tasks.

- Android: `./gradlew :app:androidApp:assembleDebug`
- Desktop: `./gradlew :app:desktopApp:run`
- Web: `./gradlew :app:webApp:jsBrowserDevelopmentRun`
- iOS: open `app/iosApp` in Xcode and run

Requirements are an Android SDK matching the `android-compileSdk` value in
`gradle/libs.versions.toml`, and Xcode with its license accepted for the iOS
target.

## Next steps

- Record the demo using [the demo video beat sheet](./DEMO_VIDEO_BEAT_SHEET.md).
- Confirm the sandbox purchase flow on a physical Android device and an iOS
  simulator before recording.
