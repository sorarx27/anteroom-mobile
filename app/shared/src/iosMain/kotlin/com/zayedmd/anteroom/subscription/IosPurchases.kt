package com.zayedmd.anteroom.subscription

/**
 * iOS launch hook for billing, called from `iOSApp.init()` as `IosPurchasesKt.startRevenueCat()`.
 *
 * Android gets its Context (and therefore its configure point) from the `purchases-kmp`
 * androidx.startup provider; iOS has no equivalent, so StoreKit is configured here instead. Doing
 * it at launch rather than at first composition gives RevenueCat time to warm the offerings cache
 * before the user can reach the paywall.
 *
 * Safe to call before `AnteroomApp` also calls `initialize` — that later call sees an already
 * configured SDK and only re-attaches the delegate and aliases the signed-in user.
 */
fun startRevenueCat() {
    AnteroomPurchases.service.initialize(
        apiKey = defaultRevenueCatApiKey(),
        appUserId = null // Anonymous until Firebase auth resolves; AnteroomApp logs the user in.
    )
}
