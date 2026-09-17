package com.zayedmd.anteroom.subscription

/**
 * Google Play public SDK key. The Android Context the SDK needs is supplied automatically by
 * `purchases-kmp`'s androidx.startup initializer, so no Application subclass is required.
 */
actual fun defaultRevenueCatApiKey(): String = RevenueCatConfig.ANDROID_API_KEY
