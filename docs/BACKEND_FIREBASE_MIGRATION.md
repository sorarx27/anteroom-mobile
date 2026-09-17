# Backend migration: Emergent stack to Firebase

This is a review of the FastAPI backend on the `main` branch and a
recommendation for moving it onto Firebase. It covers how the current server
and client talk to each other, which parts genuinely need server code, what to
do about auth, storage, and hosting, and which AI model to use for document
processing.

The short version: most of the current API doesn't need to be rebuilt. The
Kotlin client on the `kotlin` branch already reads and writes Firestore
directly, so roughly 80% of the FastAPI surface becomes client code plus
security rules. Only four things genuinely need to run on a server.

## How the current backend works

The `main` branch pairs a Python FastAPI server with an Expo/React Native
frontend. Everything the client needs goes through one REST surface.

- **Transport.** The client calls `${EXPO_PUBLIC_BACKEND_URL}/api` from
  `frontend/src/api/client.ts`. Every route is mounted under `/api` so the
  Kubernetes ingress forwards it to the single FastAPI process.
- **Auth.** Not Firebase. `backend/routers/auth.py` issues its own opaque
  session token, stores it in a MongoDB `user_sessions` collection with a
  7-day TTL index, and `deps.get_current_user` looks it up on every request.
  Passwords are bcrypt-hashed in `users`. There's also a Google path that
  calls an Emergent OAuth endpoint.
- **Database.** MongoDB through `motor`. Collections are `users`,
  `user_sessions`, `briefs`, and `profiles`, with indexes created on startup
  in `server.py`.
- **Storage.** Emergent Object Storage through `backend/storage.py`. The
  comment is explicit: "the mobile client NEVER talks to storage directly."
  Photos are uploaded as multipart to the API, which forwards them, and read
  back out through `GET /api/briefs/{id}/photos/{id}/file`.
- **AI.** Three server-side calls, all Gemini through the
  `emergentintegrations` wrapper and a single `EMERGENT_LLM_KEY`.
- **PDF.** `backend/briefpdf.py` renders a one-page A4 PDF with `reportlab`
  and draws a QR code with `qrcode`.
- **Public sharing.** `backend/services/share_html.py` renders an
  unauthenticated HTML page at `/api/public/briefs/{share_token}`.

### The endpoints

There are 20 routes across four routers.

| Router | Routes |
| --- | --- |
| `auth` | `register`, `login`, `session`, `me`, `logout`, `profile` |
| `profiles` | list, create, patch, delete |
| `briefs` | create, list, get, patch, delete, upload photo, delete photo, get photo file, `detect-doc-type`, `generate`, `translate`, `pdf` |
| `public` | `public/briefs/{share_token}` |

### The three AI calls

These are the "functions needed to process documents" and they're the real
substance of the backend.

1. **`docclassifier.classify_image`** sends one photo and asks for
   `{"doc_type", "confidence"}`. It falls back to `other`/`low` on any
   failure, which is the right default.
2. **`briefgen.generate_brief_content`** sends up to 10 page images and asks
   for the full `BriefContent` schema. The system prompt is the strongest
   asset in the whole repo: it forbids diagnosing, inferring, normalising
   units, and translating, and it requires unreadable high-risk fields to go
   into `flagged_items` rather than being guessed.
3. **`brieftranslator.translate_brief_content`** translates an existing
   extracted brief between English and Spanish.

All three parse the model's reply with a regex that hunts for the first
`{...}` block, then hand it to a `_sanitize` function that drops anything off
-schema.

## The finding that changes the plan

The Kotlin app on the `kotlin` branch doesn't call this REST API at all. It
already talks to Firestore directly through GitLive:

- `BriefsRepository` reads and writes `collection("briefs")`.
- `ProfilesRepository` reads `users/{userId}/profiles`.
- `FirebaseService` already wires up `auth`, `firestore`, `storage`, and
  `functions`.

So the migration isn't "port FastAPI to Cloud Functions." Most of the API is
plain CRUD that the client can do itself against Firestore, with security
rules doing the authorization that `get_current_user` does today. Porting it
into Functions would keep a proxy layer that Firebase makes unnecessary, and
it would cost you an invocation per read.

## What moves where

This table maps each piece of the current backend to its Firebase equivalent.

| Today | Target | Notes |
| --- | --- | --- |
| `user_sessions` + bcrypt | Firebase Auth | Delete the session table entirely |
| MongoDB `users` | Firestore `users/{uid}` | Key on the Firebase `uid` |
| MongoDB `profiles` | Firestore `users/{uid}/profiles/{profileId}` | Matches the Kotlin client |
| MongoDB `briefs` | Firestore `briefs/{briefId}` | Matches the Kotlin client |
| Emergent Object Storage | Firebase Storage | Client uploads directly |
| `POST /briefs` and CRUD | Direct Firestore writes | Security rules replace `get_current_user` |
| `POST /briefs/{id}/photos` | Direct Storage upload | Drops the multipart proxy |
| `GET .../photos/{id}/file` | Storage download URL | Drops the streaming proxy |
| `generate`, `translate`, `detect-doc-type` | Cloud Functions | Needs a server-held API key |
| `GET /briefs/{id}/pdf` | Cloud Function | Also fixes a paywall bug, below |
| `GET /public/briefs/{token}` | Hosting + Function | Or a public Firestore read |

### Only four things need to be a function

Everything else is client plus rules. Keep the server surface this small:

1. `generateBrief` — calls Gemini with the page images.
2. `translateBrief` — calls Gemini with the extracted content.
3. `classifyDocType` — calls Gemini with one image. You can fold this into
   `generateBrief` later to save a round trip.
4. `renderBriefPdf` — renders the PDF and decides the watermark.

> **Note:** Firebase Cloud Functions supports Python. That matters here.
> `briefgen.py`, `brieftranslator.py`, `docclassifier.py`, and `briefpdf.py`
> port almost verbatim, keeping the prompts and the `reportlab` layout you've
> already tested. Rewriting them in TypeScript would mean re-testing the
> extraction prompts from scratch, which is the last thing you want to redo.

## A paywall bug to fix during the move

Don't carry this one across. `GET /api/briefs/{id}/pdf` takes a
`watermark: bool = Query(default=True)` parameter, and the docstring says:

> The client (which is the source of truth for Pro entitlement via
> RevenueCat) passes `watermark=false` when the user is subscribed.

Anyone can call that endpoint with `?watermark=false` and get the clean PDF
without paying. The entitlement must be checked server side.

The clean pattern with Firebase is to let RevenueCat write the entitlement
into Firestore through its webhook, then have `renderBriefPdf` read
`users/{uid}.entitlements.anteroom_pro` before choosing the layout. The client
stops being consulted. This also gives your security rules something
trustworthy to gate Pro-only reads on.

## Auth

The migration here is unusually cheap because there's almost nothing to
migrate. The Firebase project currently has one user,
`ghomveld01@gmail.com`.

- Delete `user_sessions`, the bcrypt hashing, and the Emergent OAuth path.
- Keep a Firestore `users/{uid}` document for the app-level profile fields
  that Firebase Auth doesn't hold: `dob`, `language`, `country`,
  `profile_completed`.
- Callable functions get the verified `uid` from the request context, so
  `get_current_user` disappears rather than being ported.
- Create the `users/{uid}` document from an Auth `onCreate` trigger, and
  create the `is_self` profile in the same trigger. That replaces the startup
  backfill loop in `server.py`.

> **Warning:** Firebase Dynamic Links is shutting down, which the console is
> already warning about. That breaks email-link sign-in for mobile and
> Cordova OAuth for web. If you plan to use email-link sign-in, pick a
> different method now. Email and password, which the app already uses, is
> unaffected.

## Storage

Firebase Storage removes an entire layer rather than replacing it one-for-one.

Today every photo byte passes through the API twice: once on upload as
multipart, and once on read as a streamed response. With Firebase, the client
uploads straight to Storage and reads straight back, so you pay for neither
the invocations nor the egress through a function.

- Use a path like `users/{uid}/briefs/{briefId}/{photoId}.jpg`.
- Write rules that let only the owning `uid` read and write that prefix.
- Keep the existing limits: JPEG, PNG, or WebP only, 8 MB maximum. Enforce
  them in rules, not just in the client.
- The `generateBrief` function reads the images from Storage using the Admin
  SDK, so the images never transit the client a second time.

## Hosting and the Blaze question

Gerhard is right that Cloud Functions requires the Blaze plan. Blaze isn't the
cost risk here, though.

Blaze includes a free allowance of roughly two million function invocations a
month. At demo and early-user volume, the Firebase side of this bill is
realistically zero. The actual cost is the Gemini API, which you'd pay on any
hosting choice.

Two things to set up on day one:

1. Set a billing budget alert on the GCP project. Ahmed runs a hard cap on
   cloud spend, and Blaze is pay-as-you-go with no built-in ceiling.
2. Set `maxInstances` on every function. One runaway loop against a vision
   model is the realistic way to get a surprise bill.

If you'd rather not enable Blaze yet, there's a legitimate middle path: keep
the existing FastAPI app running on a free container host such as Render,
Railway, or Fly.io, and use Firebase only for Auth, Firestore, and Storage.
The functions verify Firebase ID tokens with the Admin SDK. That's the least
work of any option because the FastAPI code already exists and has tests. The
tradeoff is that you keep a second deployment target and a cold-start-prone
free tier.

For the public share page, Firebase Hosting plus a rewrite to a function
replaces `/api/public/briefs/{token}` directly, and gives you a real domain
for the QR codes instead of an ingress URL.

## Which AI to use

Stay on Gemini. The recommendation is to keep the model family you already
have and change how you call it.

### Why Gemini rather than switching

- The prompts in `briefgen.py` are tuned and tested against these exact
  document types. Changing model families means re-validating extraction
  accuracy on dosages, which is the highest-risk field in the product.
- `google-genai` is already in `requirements.txt`, so moving off the
  `emergentintegrations` wrapper to the official SDK is a small change.
- Vision plus long context plus a genuinely cheap tier is the combination this
  workload needs, and Gemini is strong on all three.

Keep the two-model split. Classification is high-volume and low-stakes, so a
Flash-tier model is right. Extraction is low-volume and high-stakes, so a Pro
-tier model is right. Confirm the current model IDs in AI Studio before
wiring them up: the names in the code today, `gemini-3.5-flash` and
`gemini-3.1-pro-preview`, are Emergent proxy aliases and may not match
Google's public IDs.

### One upgrade worth making

Replace the regex JSON scraping with Gemini's structured output. Set
`response_mime_type` to `application/json` and pass your `BriefContent` shape
as `response_schema`. The model then returns schema-valid JSON directly.

This removes a whole class of silent failures. Right now, if the model wraps
its reply in prose the regex misses, `_extract_json` returns `None` and
`generate_brief_content` quietly returns an empty brief. Keep `_sanitize` as a
second line of defence, but stop depending on it.

### On OpenRouter free models

We don't recommend routing real documents through free-tier models, for three
reasons.

- **Different model, different results.** Free tiers are often quantized or
  rate-limited variants. You'd be validating extraction quality against
  something other than what runs in production, which defeats the point of the
  test.
- **Rate limits will slow you down.** Free tiers throttle hard, and this
  workload sends several megabytes of images per call.
- **It's patient data.** This is the blocking reason. Routing medical
  documents through a free aggregator that forwards to whichever provider has
  capacity is not defensible under GDPR, and Anteroom's whole pitch rests on
  handling clinical data carefully.

For cheap development, use the Gemini API free tier in AI Studio with
synthetic or your own test documents instead. Before any real patient data
goes through, move to a paid tier: Google uses free-tier prompts to improve
its products, and paid usage is excluded from that. Verify the current terms
before launch, and consider Vertex AI if you need a data processing agreement
and EU data residency.

## Suggested order

Work in this sequence so the app keeps running throughout.

1. Point the Kotlin client's auth at Firebase Auth and create the
   `users/{uid}` document from an `onCreate` trigger.
2. Write Firestore and Storage security rules for `briefs`, `profiles`, and
   the photo prefix. Test them with the emulator before anything else.
3. Move photo upload to direct Storage writes and drop the two proxy
   endpoints.
4. Port `briefgen.py` and `docclassifier.py` to a Python callable function,
   switching to the official `google-genai` SDK and structured output.
5. Port `brieftranslator.py` the same way.
6. Port `briefpdf.py`, and make the watermark decision read the entitlement
   from Firestore rather than from a query parameter.
7. Wire the RevenueCat webhook to write entitlements into `users/{uid}`.
8. Move the public share page to Hosting with a rewrite to a function.
9. Decommission MongoDB and Emergent Object Storage.

## Open questions for Gerhard

These need a decision before step 1.

- Is there production data in MongoDB to migrate, or is the single Firebase
  user the real state? If it's the latter, skip data migration entirely.
- Blaze now, or FastAPI on a free container host with Firebase for auth,
  database, and storage until launch?
- Who owns the Gemini API key and billing account, given the app publisher and
  the clinical advisor are separate entities?
- Does the Expo frontend on `main` stay alive, or does the Kotlin
  Multiplatform app on `kotlin` replace it? If it's being retired, the REST
  layer can be deleted rather than migrated, which removes most of this work.

## Next steps

Confirm the open questions above, then start with security rules. They're the
piece that everything else depends on, and they're the part that a direct
-to-Firestore client architecture gets wrong most easily.
