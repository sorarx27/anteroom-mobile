# App Review and TestFlight notes

Everything App Store Connect asks for before external TestFlight and before the
App Store submission, in the order it asks for it. The prose in the quoted
blocks is written to be pasted straight into the form.

## Demo account

External TestFlight and App Review both stop dead without one: the app shows a
sign-in wall on first launch, and Guideline 2.1 treats "we could not get past
the login" as an incomplete submission.

| Field | Value |
| --- | --- |
| Email | `test1@anteroom.dev` |
| Password | `password123` |
| Sign-in required | Yes |

The account is seeded and verified against the live backend, so a reviewer sees
real content on the first screen rather than an empty state they have to fill
themselves:

- three completed briefs, each with 8 extracted medications and 1 flagged item
- two of them already translated to Spanish
- page images attached, so tapping a flagged item shows the source photograph
- the `anteroom_pro` entitlement is **inactive**, which is deliberate — the
  reviewer needs to meet the paywall in order to test the purchase

Re-check it before submitting with `python3 tools/verify_pipeline.py`. If a
reviewer has burned through the daily processing quota, that script is also the
fastest way to see it.

## Review notes

> Anteroom turns photographs of medical paperwork — referrals, prescriptions,
> medication lists, discharge summaries — into one structured brief a patient
> can hand to a doctor.
>
> Sign in with test1@anteroom.dev / password123. The dashboard already contains
> three generated briefs; open any of them to see the full output without
> waiting on processing.
>
> To exercise the pipeline end to end: tap the camera button, add one or more
> photographs of a document, and tap Generate. Extraction runs server-side and
> takes roughly 20–40 seconds per page. Any printed medical document works —
> there are sample pages in the repository if you would prefer not to use your
> own.
>
> Anteroom is an organisational tool, not a diagnostic one. It does not
> diagnose, does not recommend treatment, and does not alter doses. Where it
> cannot read a value it leaves the field empty and flags it for the user to
> confirm, rather than inferring a plausible one; the brief screen carries a
> standing reminder to check the output against the original document.
>
> Account deletion is on the home screen, below Sign out. It is immediate and
> permanent, and it erases the briefs, the page images, the profiles, the
> subscription record and the sign-in credential.
>
> Terms of Use and Privacy Policy are linked at the bottom of the purchase
> screen, beneath Restore purchases.

## Subscriptions

Anteroom Pro, subscription group **Anteroom Pro** (ID 22394775).

| Product | Apple ID | Price | Type |
| --- | --- | --- | --- |
| `monthly` | 6813462029 | $9.99 / month | Auto-renewable |
| `lifetime` | 6813464622 | $99.99 | Non-consumable |

Guideline 3.1.2 requires the binary itself to show the subscription title, its
length, its price, and functional links to the terms of use and the privacy
policy — a store listing does not satisfy it, and the check is mechanical.
`PaywallModal` carries all four: per-package title and price, "Renews monthly,
cancel anytime" on the package row, the renewal sentence above the footer, and
the two links below Restore purchases.

Prices come from StoreKit at runtime. `RevenueCatConfig.FALLBACK_MONTHLY` and
`FALLBACK_LIFETIME` exist only for the first paint and for offline, and are
pinned to the same $9.99 / $99.99. **Confirm on the TestFlight build that the
paywall shows store prices, not the fallback** — if the two ever drift, the app
advertises a price the store will not charge.

`IOS_USE_APP_STORE` is `true`, so the App Store key is in use. RevenueCat warns
that shipping the Test Store key is grounds for rejection.

## Account deletion — Guideline 5.1.1(v)

An app that creates accounts must delete them from inside the app; a support
address does not satisfy the rule. Home screen → **Delete account** →
**Delete permanently**.

The work is done by the `deleteAccount` Cloud Function rather than the client,
because security rules deny a client write access to the entitlement and usage
documents it would need to remove — the same rules that make the server-side
Pro check trustworthy also stop the client cleaning up after itself. Data is
purged first and the credential last: if the purge fails, the account still
exists and the user can retry, instead of the images outliving the only
identity that could reach them.

Verified against the live backend by `tools/verify_account_deletion.py`, which
builds a throwaway account holding a user document, a profile, a brief, a page
image in Cloud Storage and a usage counter, deletes it, and then checks each
one from the outside — including that the email can be registered again, which
distinguishes a deleted record from a disabled one. 17/17.

## Health data and App Privacy

Answer the App Privacy questionnaire as:

| Question | Answer |
| --- | --- |
| Health and Fitness → Health | Collected, linked to identity, app functionality. Not used for tracking. |
| Contact Info → Email address | Collected, linked to identity, app functionality. Not used for tracking. |
| User Content → Photos or Videos | Collected, linked to identity, app functionality. Not used for tracking. |
| User Content → Other | Collected, linked to identity, app functionality. Not used for tracking. |
| Purchases | Collected, linked to identity, app functionality. Not used for tracking. |
| Identifiers, Usage Data, Diagnostics, Location, Contacts | Not collected. |
| Tracking | No. |

No analytics, advertising, attribution or crash-reporting SDK is linked into
the app, on either platform, so there is nothing to disclose under Usage Data
or Diagnostics. Documents are processed by Google Vertex AI in `europe-west1`
under the Google Cloud Service Specific Terms.

## Export compliance

`ITSAppUsesNonExemptEncryption` is `false` in `Info.plist`. The app uses only
HTTPS and the platform's own TLS, which is exempt. Declaring it in the plist
stops App Store Connect asking on every upload, which is otherwise a manual
gate between an archive and TestFlight.

## URLs

| Field | URL |
| --- | --- |
| Privacy Policy | `https://anteroom-d2e72.web.app/privacy` |
| Terms of Use (EULA) | `https://anteroom-d2e72.web.app/terms` |
| Support URL | `https://anteroom-d2e72.web.app/support` |
| Marketing URL | `https://anteroom-d2e72.web.app` |

Served from Firebase Hosting on the app's own project. Publish with
`firebase deploy --only hosting` after editing anything in `hosting/`.

Apple's standard EULA covers the in-app purchase itself; `/terms` references it
and adds the medical disclaimer, which the standard EULA does not carry.

## Build numbers

`CURRENT_PROJECT_VERSION` is **2** in
`app/iosApp/Configuration/Config.xcconfig`. Build 1 is already processed on
TestFlight, and App Store Connect rejects a re-used build number at upload
rather than at submission. Bump it before every archive.

## Still open

- **Contact address.** The pages use `support@anteroom.app`. The Cloudflare
  Email Routing MX and SPF records for `anteroom.app` are live, so the routing
  rule forwarding that address to a real inbox must exist — a privacy policy
  for EU health data with an unreachable contact is worse than no address.
  Check it by sending a message to it.
- **`anteroom.app` serves a different product.** The apex currently returns an
  unrelated "AnteRoom — Meet Professionals" page, which is why the legal URLs
  point at `anteroom-d2e72.web.app` instead. Worth pointing the domain at
  Firebase Hosting before the App Store submission, so the support address and
  the support site share a name.
- **Governing law.** `/terms` names Spain. That follows the relocation, not the
  publisher entity — the signing certificate is
  `Apple Distribution: Ahmed Zayed (6F22G47URV)`, which reads as an individual
  account. Align the two before the App Store submission; TestFlight does not
  ask.
- **Google Play.** The Play products are not registered in the RevenueCat
  dashboard, so Android billing currently errors with "no Play Store products
  registered for your offerings". The Kotlin path is identical and works
  against StoreKit, but Android billing cannot be demonstrated until the
  products exist.
