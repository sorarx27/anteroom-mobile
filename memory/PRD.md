# Anteroom PRD

## Product
Mobile app that turns messy medical papers into a doctor-ready 1-page pre-visit brief in ~10 seconds. Free tier is watermarked; Pro (€2.99/mo or €29.99/yr) unlocks clean export, family profiles, and translation.

## Scope built so far
1. Welcome / splash on sage gradient
2. 2-slide onboarding carousel
3. Auth: Email/Password + Emergent-managed Google Sign-In
4. Profile setup: Name, DOB, Language (EN/ES), Country
5. **Dashboard** with brief list + hero + locked-feature chips (free) + PRO badge (paid) + "Create a brief" CTA
6. **RevenueCat paywall** (`/paywall`) — monthly (€2.99) + annual (€29.99), restore, `pro` entitlement gates
7. **Document capture** (`/capture`) — take photo OR pick from gallery (multi-select), upload each to Emergent Object Storage against the user's draft brief
8. **Brief draft review** (`/brief-draft?brief_id=…`) — grid of uploaded photos with per-photo delete, doc-type chips (Referral / Med list / Lab result / Other), Save draft + Discard actions

## Non-goals (deferred)
- Brief generation, PDF export, QR
- Family profiles UI beyond gating
- Translation runtime
- Reorder gestures (backend supports it; UI still to come)

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
- `briefs`: brief_id, user_id, doc_type ('referral'|'med_list'|'lab_result'|'other'), status ('draft'|'complete'), photos[{photo_id, filename, content_type, size, storage_path, created_at, deleted_at}], created_at, updated_at, deleted_at (soft-delete)

## Backend endpoints (new this iteration)
- POST   `/api/briefs`                                    create draft
- GET    `/api/briefs`                                    list current user's briefs
- GET    `/api/briefs/{id}`                               fetch one
- PATCH  `/api/briefs/{id}`                               update doc_type / status / photo_order
- DELETE `/api/briefs/{id}`                               soft-delete
- POST   `/api/briefs/{id}/photos` (multipart)            upload photo (JPEG/PNG/WEBP, ≤8 MB)
- DELETE `/api/briefs/{id}/photos/{pid}`                  soft-delete photo
- GET    `/api/briefs/{id}/photos/{pid}/file`             stream image bytes (Bearer OR ?token=)

## Permissions
- iOS `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`, `NSPhotoLibraryAddUsageDescription`
- Android `CAMERA`, `READ_MEDIA_IMAGES`
- App requests permissions contextually on Take Photo / From Gallery buttons; blocked state offers "Open Settings"

## Going live with RevenueCat
Steps to enable real IAP on published store builds are in the "Publish → Payments" FAQ: upload App Store Connect + Play Console credentials, create matching product IDs, submit release build for review.
