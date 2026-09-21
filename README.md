# Anteroom

Anteroom turns photographs of a patient's own medical paperwork — referral letters,
medication lists, lab reports, discharge summaries — into a single structured one-page
brief they can hand to a clinician.

The design rule throughout is the one that governs a good clinical handover: say what
you know, and say plainly what you do not. Text is extracted verbatim; anything
illegible is listed as a flagged item rather than inferred.

**Anteroom is not a medical device.** It does not diagnose, does not recommend or alter
treatment, and does not assess urgency.

## Repository layout

```
backend/     FastAPI + MongoDB. Auth, profiles, briefs, PDF rendering, public share pages.
frontend/    Expo Router (SDK 57) / React Native client. iOS, Android and web.
memory/      PRD and integration notes.
tests/       Cross-cutting tests.
```

Two branches carry two different clients:

| Branch | Client | Status |
| --- | --- | --- |
| `main` | Expo / React Native | the build registered with App Store Connect |
| `kotlin` | Kotlin Multiplatform (Compose) | parallel exploration, not submitted |

## Running it

**Backend** — needs `MONGO_URL`, `DB_NAME`, `EMERGENT_LLM_KEY` and `INTEGRATION_PROXY_URL` in `backend/.env`:

```bash
cd backend
pip install -r requirements.txt
uvicorn server:app --reload
```

**Frontend** — needs `EXPO_PUBLIC_BACKEND_URL` and the RevenueCat public keys
(`EXPO_PUBLIC_REVENUECAT_IOS_API_KEY`, `..._ANDROID_API_KEY`, `..._TEST_API_KEY`):

```bash
cd frontend
yarn install
yarn ios      # or: yarn android / yarn web
```

## Tests

```bash
cd backend && python -m pytest tests -q
cd frontend && npx expo-doctor && npx expo lint
```

The backend suite is integration-level and runs against a live `EXPO_PUBLIC_BACKEND_URL`.

## Stack

Expo Router · React Native 0.86 · FastAPI · MongoDB · Gemini (document classification,
extraction, translation) · RevenueCat (subscriptions) · ReportLab (PDF)

## Subscriptions

Free briefs are watermarked and cover one person. Anteroom Pro (€2.99/month, €29.99/year)
unlocks unwatermarked export, family profiles, and English↔Spanish translation, gated on
the `pro` entitlement.

## Legal

[Privacy Policy](https://drahmed7887.github.io/anteroom-site/privacy.html) ·
[Terms of Use](https://drahmed7887.github.io/anteroom-site/terms.html) ·
[Support](https://drahmed7887.github.io/anteroom-site/support.html)
