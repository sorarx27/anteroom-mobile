# Anteroom PRD

## Product
Mobile app that turns messy medical papers into a doctor-ready 1-page pre-visit brief in ~10 seconds. Free tier is watermarked; Pro (€2.99/mo or €29.99/yr) unlocks clean export, family profiles, and translation.

## Scope built so far
1. Welcome / splash on sage gradient
2. 2-slide onboarding carousel
3. Auth: Email/Password + Emergent-managed Google Sign-In
4. Profile setup: Name, DOB, Language (EN/ES), Country
5. Dashboard with brief list + horizontal family strip + hero + locked-feature chips (free) + PRO badge (paid) + "Create a brief" CTA
6. RevenueCat paywall (`/paywall`) — monthly (€2.99) + annual (€29.99), restore, `pro` entitlement gates
7. Document capture (`/capture`) — take photo OR pick from gallery, upload to Emergent Object Storage against the active profile's draft brief
8. Brief draft review (`/brief-draft?brief_id=…`) — photo grid + delete + doc-type chips + Save/Discard + Generate CTA
9. Doc-type auto-detect via Gemini 3.5 Flash with manual override + Detect button
10. AI Brief Generation via Gemini 3.1 Pro Preview — verbatim extraction only, no diagnosis; `/brief-view` scrollable page with QR share code; watermarked / clean 1-page PDF (RC entitlement client-side)
11. **Family profiles (Pro)** — each user gets a self profile auto-created on register/login; Pro users can add partner / child / parent / other from `/profile-add`. Dashboard shows a horizontal strip with You + members + `+ Add`. Active profile persists between sessions (AsyncStorage) and scopes brief listings + new brief creation. Free users see all profiles but only Self is tappable — everything else opens the paywall. Removing a non-self profile soft-archives it AND all its briefs.
12. **Brief translation (Pro)** — English ↔ Spanish translation of any generated brief via Gemini 3.1 Pro Preview. Chips on `/brief-draft` let the user pre-pick the output language before Generate (auto-translates after extraction). Chips on `/brief-view` toggle in-place, re-rendering from `content_translations[lang]` and downloading a Spanish-labelled PDF. Public QR share page respects `?lang=` and shows a language switcher. Free users tapping the non-source chip hit `/paywall?trigger=translate`. Strict guardrails: patient names, medication names, doses, frequencies, dates and IDs are copied verbatim; only referral reason, flagged items and allergy reactions are translated.

## Backend collections
- `profiles`: profile_id, user_id (owner), name, relationship ('self'|'partner'|'child'|'parent'|'other'), dob, sex, is_self, created_at, updated_at, deleted_at
- `briefs` now includes `profile_id`, `source_language` (locked at generate-time), `content_translations` map keyed by target language

## Backend endpoints (new this iteration)
- POST `/api/briefs/{id}/generate` — extract structured content with Gemini 3.1 Pro Preview, set status=complete, mint short-lived share_token (7 days), record `source_language` from the user profile
- POST `/api/briefs/{id}/translate` {target_language: 'en'|'es'} — Gemini 3.1 Pro Preview translation; caches under `content_translations[target_language]`; idempotent when the language matches source or is already cached
- GET  `/api/briefs/{id}/pdf?watermark=…&lang=en|es&token=…` — server-rendered 1-page PDF via reportlab; localized section titles; falls back to source if the requested lang isn't cached
- GET  `/api/public/briefs/{share_token}?lang=en|es` — unauthenticated HTML share page targeted by the QR code (mounted under /api so Kubernetes ingress reaches it); localized titles + language switcher

## Non-goals (deferred)
- Family profiles UI beyond gating (done)
- Reorder gestures (backend supports it; UI still to come)

## Backend layout (post-refactor)
```
backend/
  server.py                 # 100-line FastAPI entry: mounts routers, indexes, backfill
  db.py                     # Mongo client + shared config constants
  deps.py                   # Auth deps (get_current_user, resolve_user_from_bearer_or_token, session helpers, model mappers)
  models.py                 # All Pydantic request/response models
  services/
    profiles.py             # ensure_self_profile, validate_dob, RELATIONSHIPS
    share_html.py           # Public share HTML rendering + i18n labels
  routers/
    auth.py                 # /auth/* — register, login, google session, me, logout, profile
    profiles.py             # /profiles CRUD
    briefs.py               # /briefs CRUD, photos, detect, generate, translate, pdf
    public.py               # /public/briefs/{token} share view
  briefgen.py               # Gemini 3.1 Pro Preview extraction (unchanged)
  brieftranslator.py        # Gemini 3.1 Pro Preview translation (unchanged)
  briefpdf.py               # ReportLab renderer (unchanged)
  docclassifier.py          # Gemini 3.5 Flash classifier (unchanged)
  storage.py                # Emergent Object Storage (unchanged)
```

## Tech
- Expo Router (SDK 57), React Native
- FastAPI + MongoDB (`users`, `user_sessions`, `briefs`)
- Auth: bcrypt + Emergent Google OAuth; 7-day session tokens in expo-secure-store
- Payments: `react-native-purchases` (RevenueCat), Emergent-managed via integration proxy
- Storage: **Emergent Object Storage** — client never talks to storage directly; backend uploads/downloads via `INTEGRATION_PROXY_URL` + `EMERGENT_LLM_KEY`. Path convention `anteroom/uploads/{user_id}/{photo_id}.{ext}`. Authenticated downloads via Bearer header (native) or `?token=` query (web `<img>`).
- Design: iOS-Native Clean, sage green palette (`brandPrimary #365F50`)

## Backend collections
- `users`: user_id, email, password_hash?, name, picture, dob, language, country, profile_completed, provider, created_at
- `user_sessions`: session_token, user_id, created_at, expires_at (TTL)
- `briefs`: brief_id, user_id, doc_type ('referral'|'med_list'|'lab_result'|'other'), doc_type_manual_override, detected_doc_type, detected_confidence ('low'|'medium'|'high'), status ('draft'|'complete'), photos[{photo_id, filename, content_type, size, storage_path, created_at, deleted_at}], created_at, updated_at, deleted_at (soft-delete)

## Backend endpoints (new this iteration)
- POST   `/api/briefs`                                    create draft
- GET    `/api/briefs`                                    list current user's briefs
- GET    `/api/briefs/{id}`                               fetch one
- PATCH  `/api/briefs/{id}`                               update doc_type / status / photo_order
- DELETE `/api/briefs/{id}`                               soft-delete
- POST   `/api/briefs/{id}/photos` (multipart)            upload photo (JPEG/PNG/WEBP, ≤8 MB) — kicks off async Gemini 3.5 Flash classification on the first photo
- DELETE `/api/briefs/{id}/photos/{pid}`                  soft-delete photo
- GET    `/api/briefs/{id}/photos/{pid}/file`             stream image bytes (Bearer OR ?token=)
- POST   `/api/briefs/{id}/detect-doc-type`               re-run classification, clear manual override

## Permissions
- iOS `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`, `NSPhotoLibraryAddUsageDescription`
- Android `CAMERA`, `READ_MEDIA_IMAGES`
- App requests permissions contextually on Take Photo / From Gallery buttons; blocked state offers "Open Settings"

## Going live with RevenueCat
Steps to enable real IAP on published store builds are in the "Publish → Payments" FAQ: upload App Store Connect + Play Console credentials, create matching product IDs, submit release build for review.
