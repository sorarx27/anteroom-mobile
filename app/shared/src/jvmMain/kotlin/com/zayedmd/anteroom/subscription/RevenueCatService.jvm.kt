package com.zayedmd.anteroom.subscription

/**
 * Desktop (JVM) has no store to buy from — `purchases-kmp` publishes Android and Apple artifacts
 * only. The paywall still runs end to end so the flow can be reviewed on a laptop.
 */
actual fun createRevenueCatService(): RevenueCatService = SimulatedRevenueCatService()

actual fun defaultRevenueCatApiKey(): String = RevenueCatConfig.TEST_API_KEY
