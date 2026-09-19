# Anteroom — Devpost submission

Anteroom turns the pile of paper a patient carries into a doctor's office
into one structured, bilingual clinical brief. It's a Kotlin Multiplatform
app with a 100% Compose Multiplatform UI on iOS and Android, monetized end
to end with the official RevenueCat `purchases-kmp` SDK — live on iOS through
StoreKit, and running the same billing code on Android.

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

This is tested, not asserted. `tools/sample_page_illegible.jpg` is a
prescription whose Warfarin dose has been blurred and blotted out. A model
that guesses a plausible dose passes any test that only checks the readable
rows, so the end-to-end suite asserts the opposite: that the dose is
**absent** from `medications` and **present** in `flagged_items`. Against
the live backend it comes back as

```
medications: ... Warfarin (no dose) as directed
flagged:     Warfarin dose unreadable on page 2
```

and the brief screen renders Warfarin as the one medication in the list
with no dose line under it.

## How we built it

The whole product is one Kotlin Multiplatform codebase.

| Layer | Technology |
| --- | --- |
| UI | Compose Multiplatform 1.12, Material 3, shared across all targets |
| Shared logic | Kotlin Multiplatform 2.4.20 |
| Targets | Android, iOS (arm64 and simulator), Desktop (JVM), Web (JS) |
| Auth and data | Firebase Auth, Firestore, and Storage through GitLive |
| Backend | Firebase Cloud Functions, Python 3.12, `europe-west1` |
| Extraction | Gemini 2.5 Pro and 2.5 Flash on Vertex AI, `europe-west1` |
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
  implementation, zero duplication: the same Kotlin runs against StoreKit on
  iOS and Google Play Billing on Android, with no platform branches in the
  calling code.
- **What is live today:** in-app purchases are active on **iOS**, through
  StoreKit, on a public TestFlight build that anyone can install:
  <https://testflight.apple.com/join/EvbxqXGU>. The monthly and lifetime
  products exist in App Store Connect and the paywall resolves their prices
  from the store at runtime.
- **Android** runs the identical code from the identical source set and
  installs as an APK built from this repo with
  `./gradlew :app:androidApp:assembleDebug`. Its Play Store products are not
  registered yet, so purchases cannot complete there. A new Google Play
  Console account has to run a 14-day closed test with 20 testers before it
  can publish, which does not fit inside this hackathon — so Play
  registration is scheduled for after the beta rather than rushed. The app
  handles the unconfigured state rather than leaking it: the RevenueCat SDK
  returns `ConfigurationError`, and the paywall shows one sentence, falls back
  to the catalogue price, and logs the diagnostic.
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

### The extraction pipeline

Four callables and a webhook, deployed to `europe-west1`. The client never
writes a clinical field: `firestore.rules` rejects `content`, `status`,
`generated_at` and the share token from any client, and the Admin SDK inside
these functions is the only thing that may. "Nothing invented" is therefore
a property of the system rather than a promise about the UI.

| Function | Model | Owns |
| --- | --- | --- |
| `classifyDocType` | Gemini 2.5 Flash | `detected_doc_type`, `detected_confidence` |
| `generateBrief` | Gemini 2.5 Pro | `content`, `status`, `generated_at` |
| `translateBrief` | Gemini 2.5 Pro | `content_translations` |
| `renderBriefPdf` | none | nothing — returns bytes |
| `revenuecatWebhook` | none | `users/{uid}/billing/entitlement` |

**Everything stays in the EU.** Firestore is `eur3`, functions and Vertex AI
are both pinned to `europe-west1`. That costs us model capability and we
took the trade knowingly: Gemini 3.x exists on Vertex, but only on the
`global` endpoint, which routes a photograph of someone's referral letter to
whichever region Google picks. These are medical documents, so the residency
is worth more than the newer model.

**Translation cannot touch a dose.** `merge_translation` takes prose from
the model and every clinical field from the already-extracted content, so a
translation pass is structurally unable to alter a drug name, a dose, a
frequency, a patient's name or an ID. The test feeds it a deliberately
hostile response that corrupts all of those and asserts none of it lands.
Dosing frequency *is* localised for Spanish clinicians — by a fixed lookup
table, not the model, and anything the table doesn't recognise is left in
the source language rather than guessed at.

**The paywall moved server-side.** `renderBriefPdf` takes no `watermarked`
argument. The server derives it from the entitlement, so the clean export —
one of the two things Pro sells — stopped being a boolean the client could
flip. `translateBrief` returns `PERMISSION_DENIED` to a free caller. The
RevenueCat webhook is the only writer of that entitlement document.

**It spends real money, so it has a brake.** A per-user daily cap charged
transactionally *before* the model call, and a kill switch at
`config/runtime.ai_enabled` read on every call — flip it and spend stops
within seconds, with no deploy. Pages are downscaled to a 1536px long edge on
both the client and the server, because the server must not depend on a
client having behaved. A two-page brief costs about **$0.02** end to end.

## What runs today

Being precise about this matters more than the pitch does.

**Live and verified against the deployed project:**

- The complete Compose Multiplatform UI across Android, iOS, Desktop, and
  Web.
- RevenueCat billing through `purchases-kmp`: offerings fetch, package
  purchase, restore, customer info refresh, and delegate-driven entitlement
  updates, all verified against `anteroom_pro`. Purchases complete **on iOS**,
  through StoreKit, on a TestFlight build Apple has approved for external
  testers: <https://testflight.apple.com/join/EvbxqXGU>
- Firebase authentication, Firestore persistence, and Storage photo upload —
  including on Desktop, which uploads over the Storage REST API because
  GitLive ships no JVM implementation.
- Real extraction: a photographed referral and a photographed prescription
  become a populated `BriefContent` in about 13 seconds, with the illegible
  dose flagged rather than guessed.
- Translation into Spanish, and PDF export whose watermark is decided by the
  server.
- Family profile management and the clinical brief view with flagged-item
  banners and photo verification against the original page.

**Honest limits:**

- **Android purchases do not complete yet.** The Play products are not
  registered, so the store returns no offering and the paywall falls back to
  the catalogue price with a plain "Plans aren't available on this device
  right now." A new Google Play Console account must run a 14-day closed test
  with 20 testers before it can publish, which cannot finish before the
  deadline — so Android ships as a direct APK this cycle and Play
  registration follows. The billing code itself is the same source set that
  is live on iOS; what is missing is a store listing, not an implementation.
- Two languages, English and Spanish.
- Six pages per brief. The cap is the spend ceiling as much as a product
  limit, and it is enforced in the security rules, not just the client.
- The public share page was cut. An earlier build advertised a QR code
  pointing at a guessable `/s/{brief_id}` URL; the page, the QR and the
  claim were removed together.
- Desktop and Web run `SimulatedRevenueCatService` for billing and label
  themselves as a simulation on screen. Everything else on those targets is
  the real backend.
- The Ktor server module is a scaffold and is unused.

### How we know

Four suites, all runnable against the live project rather than a mock:

| Suite | Checks | Proves |
| --- | --- | --- |
| `tools/verify_rules.py` | 21 | Cross-user denial, server-only clinical fields, storage limits |
| `tools/verify_pipeline.py` | 43 | The whole pipeline, both paywall gates, entitlement grant and revoke |
| `tools/verify_account_deletion.py` | 17 | That deleting an account really erases it, checked from outside |
| `functions/tests/test_pipeline.py` | 56 | Translation safety boundary, PDF, frequency lookup |

The rules suite earned its keep immediately. It found that our brief-create
rule could never have passed: it required `status == 'draft'` while also
requiring the key `status` to be absent, and the Kotlin client serialises
defaults, so every nullable field arrives as an explicit null. A test
written from the rules would have agreed with the rules. Only a test built
from the real client payload caught it.

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
- **A security rule that no client could satisfy.** Described above. The
  general lesson: a test written from your rules will agree with your rules.
  Build the payload the way the client actually builds it.
- **Deleting the fallbacks was the risky part.** The repositories used to
  swallow every failure and return mock data, so a permission error, an
  offline device and an empty account were indistinguishable — from each
  other and from success. Removing that turned silent wrong answers into
  crashes until there was somewhere for failures to go, so the error banner
  and `try/finally` on every long call had to land in the same change.
- **Firebase on Desktop fails twice, late, and somewhere else.**
  `Firebase.initialize(context = null)` is correct on Web and leaves the
  JVM app uninitialised — which surfaces at the *first auth call* as
  "Default FirebaseApp is not initialized". Passing a `Context` gets
  further: auth succeeds, and Firestore then dies on its own async queue
  because it casts to `android.app.Application`. Writing the integration
  test before the implementation is the only reason both were found.

## Accomplishments we're proud of

- One `RevenueCatService` implementation drives billing on iOS and Android
  with no platform branches in the calling code — live against StoreKit
  today, and pointed at Google Play Billing by the same source set the moment
  the Play products exist.
- A paywall that reacts to entitlement state rather than to button taps, so
  it behaves correctly when a subscription changes somewhere else.
- A clinical data model that treats "we don't know" as information worth
  rendering — and a test that proves the model honours it, by handing it a
  dose it cannot read and checking that the field stays empty.
- Both Pro gates enforced on the server. The clean export is not a flag the
  client can set.
- A pipeline that spends real money behind a daily per-user cap and a kill
  switch that stops it in seconds without a deploy.

## What we learned

`purchases-kmp` collapses two native billing integrations into one Kotlin
surface, and the remaining work is almost entirely about state, not stores.
The lesson that transferred best from clinical practice: the value of an
intake document is not how much it asserts, it's how clearly it marks what
it can't confirm.

## What's next

- Validate the brief format with clinicians, as a student research
  project. The question we want answered is not "is it accurate"
  but "does the flagged-items section change what you ask the patient".
- Expand language coverage beyond English and Spanish. The frequency lookup
  table is per-language and the extraction prompt is language-agnostic, so
  the work is mostly clinical review of the wording.
- Google Play registration, then the 14-day closed test, then Play Billing
  live against the same `RevenueCatServiceImpl` that already runs on
  StoreKit.
- On-device classification, so the doc-type triage costs nothing and works
  offline.
- A clinic handoff that is safe by construction — the previous design was a
  guessable URL, and we would rather ship nothing than ship that again.

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
  practice. Currently enrolled in a Master's program at RWTH Aachen
  University.
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

The backend deploys with `firebase deploy --only functions`, and
[docs/PIPELINE_OPERATIONS.md](./PIPELINE_OPERATIONS.md) covers the parts that
aren't obvious from the code: the region trade, the absence of any
service-account key, the spend levers, and how to run the three suites.
