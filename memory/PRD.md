# Anteroom PRD

## Product
Mobile app that turns messy medical papers into a doctor-ready 1-page pre-visit brief in ~10 seconds. Free tier is watermarked; Pro (€2.99/mo or €9.99 lifetime) unlocks clean export, family profiles, and translation.

## Scope of this iteration — Onboarding to blank dashboard
1. Welcome / splash (brand intro on sage gradient)
2. 2-slide onboarding carousel explaining core value
3. Auth: Email/Password + Emergent-managed Google Sign-In
4. Profile setup: Name, DOB, Language (EN/ES), Country
5. Empty dashboard (post-onboarding landing) with a placeholder "Create a brief" action

## Non-goals (deferred)
- Document capture / OCR
- Brief generation, PDF export, QR
- Pro subscription / paywall
- Family profiles, translation

## Tech
- Expo Router (SDK 57), React Native
- FastAPI + MongoDB (users, user_sessions with TTL)
- Auth: bcrypt password hashing + Emergent Google OAuth; 7-day session tokens stored in expo-secure-store
- Design: iOS-Native Clean, sage green palette (`brandPrimary #365F50`)

## Users & Data
- `users`: user_id (custom), email (unique), password_hash?, name, picture, dob, language, country, profile_completed, provider, created_at
- `user_sessions`: session_token (unique), user_id, created_at, expires_at (TTL)

## Auth flow (mobile)
- Register/Login → session_token in secure store, applied as Bearer header
- Google: WebBrowser.openAuthSessionAsync → session_id in redirect → POST /api/auth/session → session_token
- Root layout gate routes: unauth → welcome; auth + !profile_completed → profile-setup; auth + profile_completed → dashboard
