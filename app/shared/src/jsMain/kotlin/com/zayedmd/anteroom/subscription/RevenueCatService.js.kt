package com.zayedmd.anteroom.subscription

/** Web (JS) mirrors the desktop fallback: demoable paywall, no billing SDK behind it. */
actual fun createRevenueCatService(): RevenueCatService = SimulatedRevenueCatService()

actual fun defaultRevenueCatApiKey(): String = RevenueCatConfig.TEST_API_KEY
