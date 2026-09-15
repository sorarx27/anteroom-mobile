# Anteroom PRD

## Product
Mobile app that turns messy medical papers into a doctor-ready 1-page pre-visit brief in ~10 seconds. Free tier is watermarked; Pro (€2.99/mo or €29.99/yr) unlocks clean export, family profiles, and translation.

## Scope built so far
1. Welcome / splash (brand intro on sage gradient)
2. 2-slide onboarding carousel
3. Auth: Email/Password + Emergent-managed Google Sign-In
4. Profile setup: Name, DOB, Language (EN/ES), Country
5. Empty dashboard with "Create a brief" placeholder + Upgrade entry + locked-feature chips
6. **RevenueCat (Emergent-managed) paywall** — dedicated `/paywall` screen with monthly (€2.99) + annual (€29.99) plans, restore, and confirmation dialog on Test Store. `pro` entitlement gates clean export, family profiles, translation. PRO badge shown on dashboard when active.

## Non-goals (deferred)
- Document capture / OCR
- Brief generation, PDF export, QR
- Family profiles UI beyond gating
- Translation runtime

## Tech
- Expo Router (SDK 57), React Native
- FastAPI + MongoDB (users, user_sessions with TTL)
- Auth: bcrypt + Emergent Google OAuth; 7-day session tokens in expo-secure-store
- Payments: `react-native-purchases` (RevenueCat), Emergent-managed via integration proxy
  - Test Store used in Expo Go / web preview (test API key), real App Store / Play Store for production builds
  - Entitlement `pro` is the sole source of truth for Pro status — never mirrored to backend
- Design: iOS-Native Clean, sage green palette (`brandPrimary #365F50`)

## RevenueCat runtime
- SDK init: `initializeRevenueCat()` at module scope in `app/_layout.tsx`
- Identity: `bindIdentity(user_id)` called from AuthContext on every auth path; logIn on sign-in, logOut on sign-out
- Purchase blocked when `getAppUserID()` starts with `$RCAnonymousID:`
- `useSubscription()` hook exposes `isSubscribed`, `identityReady`, `offerings`, `purchase`, `restore`

## Users & Data (backend, unchanged)
- `users`: user_id, email, password_hash?, name, picture, dob, language, country, profile_completed, provider, created_at
- `user_sessions`: session_token, user_id, created_at, expires_at (TTL)

## Going live with RevenueCat (user tasks)
Steps to enable real IAP on published store builds are in the "Publish → Payments" FAQ section: upload App Store Connect + Play Console credentials, create matching product IDs, submit release build for review.
