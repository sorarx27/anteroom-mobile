# RevenueCat — integrated (2026-09-15)
This file is memory for future interactions with the user's RevenueCat account via integration proxy.

## Identifiers (from /setup response — copy verbatim)
- rc_project_id: proj24fa2480
- apple_app_id: appaaac0b0a2f
- play_app_id: app5d0189dfda
- bundle_id: com.emergent.medicalbriefsetup.uhg3cv
- package_name: com.emergent.medicalbriefsetup.uhg3cv
- entitlement_lookup_key: pro
- offering_lookup_key: default
- Packages (package -> product_id, current price):
  - $rc_monthly -> prodec87ba39e8   (€2.99 / P1M, trial: none)
  - $rc_annual  -> prod2f62458a73   (€29.99 / P1Y, trial: none)
- Dashboard: https://app.revenuecat.com/projects/proj24fa2480

## Notes
- User originally asked for €9.99 lifetime, but non-consumable one-time purchases are OUT OF SCOPE for the RevenueCat playbook. Annual (€29.99/P1Y) provisioned instead. Lifetime would need Stripe or explicit playbook support.
- Pro entitlement gates: clean PDF export (no watermark), family profiles, translation.

## Status check
curl -sS -H "$AUTH" "$INTEGRATION_PROXY_URL/internal/revenuecat/projects/b13e2887-85cb-49c3-9803-4b12de4e391e/status"
→ {"connection_state":"connected","project_state":"...","rc_project_id":"..."}

## Later updates (integration proxy APIs ONLY — NEVER call the RevenueCat REST API)
- Change price/duration/trial OR add a package (upsert):
  POST $INTEGRATION_PROXY_URL/internal/revenuecat/projects/b13e2887-85cb-49c3-9803-4b12de4e391e/products
  body: {"products":[{"package":"$rc_monthly","price":2.99,"currency":"EUR","period":"P1M","prices":[{"amount_micros":2990000,"currency":"EUR"}]}]}
  (amount_micros = price × 1,000,000; omit "trial" for none)
- Remove a package:
  DELETE $INTEGRATION_PROXY_URL/internal/revenuecat/projects/b13e2887-85cb-49c3-9803-4b12de4e391e/products/%24rc_monthly

## Taking in-app purchases LIVE — store-side steps (USER does these — agent cannot verify)
Needed ONLY for REAL purchases in published store builds. Test Store (Expo Go / web preview / dev build) needs none of this.
- Step 1 — Upload App Store / Play Store credentials to the RevenueCat dashboard
  - iOS: In-app purchase key + App Store Connect API key
  - Android: Google Play service-account credentials JSON
- Step 2 — Set up payment profiles in App Store Connect and Play Console
- Step 3 — Create matching in-app purchase products in ASC and Google Play using the SAME product IDs shown in the RevenueCat dashboard
- Step 4 — Make a release build, test with TestFlight / Play internal testing, submit for review.

All the steps needed to integrate RevenueCat in the production app are also present in the FAQ section of the payments panel.
