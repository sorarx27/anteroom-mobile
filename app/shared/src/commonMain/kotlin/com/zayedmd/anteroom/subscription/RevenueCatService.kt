package com.zayedmd.anteroom.subscription

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything RevenueCat-shaped that is not platform specific: the dashboard identifiers, the
 * public SDK keys, and the catalogue the paywall falls back to when the store cannot be reached
 * (offline, misconfigured, or a target the billing SDK simply does not ship for).
 */
object RevenueCatConfig {
    const val ENTITLEMENT_ID = "anteroom_pro"
    const val OFFERING_DEFAULT = "default"

    /** Package identifiers exactly as configured in the RevenueCat dashboard. */
    const val PACKAGE_MONTHLY = "\$rc_monthly"
    const val PACKAGE_LIFETIME = "\$rc_lifetime"

    /**
     * Public (client) SDK keys. These are safe to ship inside the binary — they can read
     * offerings and start purchases, but they cannot mutate entitlements server side.
     */
    const val ANDROID_API_KEY = "goog_GoVlNPjRvRsCPJZnSwbvxVLyIxL"

    /** RevenueCat Test Store key. Sandbox only, but valid on every platform. */
    const val TEST_API_KEY = "test_SdRJzVajoxUcFiSJfAajtpCNWvP"

    /** Paste the `appl_...` App Store key here the moment RevenueCat issues one. */
    const val IOS_APP_STORE_API_KEY = ""

    /** App Store key once it exists, Test Store key until then. */
    val iosApiKey: String
        get() = IOS_APP_STORE_API_KEY.ifBlank { TEST_API_KEY }

    val FALLBACK_MONTHLY = SubscriptionPackage(
        identifier = PACKAGE_MONTHLY,
        packageType = PackageType.MONTHLY,
        title = "Anteroom Pro Monthly",
        description = "Unlimited family profiles, clean doctor export & translation.",
        priceString = "$7.99/mo",
        priceMicros = 7_990_000L,
        isLifetime = false
    )

    val FALLBACK_LIFETIME = SubscriptionPackage(
        identifier = PACKAGE_LIFETIME,
        packageType = PackageType.LIFETIME,
        title = "Anteroom Pro Lifetime",
        description = "Permanent access. One-time unlock, all features forever.",
        priceString = "$129.99",
        priceMicros = 129_990_000L,
        isLifetime = true
    )

    val FALLBACK_OFFERING = SubscriptionOffering(
        identifier = OFFERING_DEFAULT,
        monthly = FALLBACK_MONTHLY,
        lifetime = FALLBACK_LIFETIME
    )
}

interface RevenueCatService {
    val isSubscribed: StateFlow<Boolean>
    val activeOffering: StateFlow<SubscriptionOffering?>
    val isProcessing: StateFlow<Boolean>
    val lastError: StateFlow<String?>

    /**
     * False when this build talks to a real store (Google Play / App Store), true on targets
     * where `purchases-kmp` has no artifact and the flow is demo-only. The paywall reads this so
     * it never claims a charge happened when none did.
     */
    val isSimulated: Boolean

    fun initialize(apiKey: String, appUserId: String?)
    suspend fun fetchOfferings(): SubscriptionOffering?
    suspend fun purchasePackage(pkg: SubscriptionPackage): Result<Boolean>
    suspend fun restorePurchases(): Result<Boolean>

    /** Re-reads `anteroom_pro` from RevenueCat. Returns true when the entitlement is active. */
    suspend fun refreshCustomerInfo(): Boolean

    fun clearError()
}

/**
 * `purchases-kmp` only publishes Android and Apple artifacts, so the store-backed implementation
 * cannot live in `commonMain`. Each target picks its own.
 */
expect fun createRevenueCatService(): RevenueCatService

/** The public SDK key for the store this target actually buys from. */
expect fun defaultRevenueCatApiKey(): String

/**
 * One purchase client for the whole app. The paywall, the dashboard lock icons and the export
 * screen all have to agree about `anteroom_pro`, which they cannot do if each composable builds
 * its own service.
 */
object AnteroomPurchases {
    val service: RevenueCatService by lazy { createRevenueCatService() }
}

/**
 * Fallback client for Desktop (JVM) and Web (JS), where no billing SDK exists. It keeps the
 * paywall fully navigable for reviewers on a laptop, and marks itself [isSimulated] so the UI can
 * say so out loud.
 */
class SimulatedRevenueCatService : RevenueCatService {

    private val _isSubscribed = MutableStateFlow(false)
    override val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _activeOffering = MutableStateFlow<SubscriptionOffering?>(RevenueCatConfig.FALLBACK_OFFERING)
    override val activeOffering: StateFlow<SubscriptionOffering?> = _activeOffering.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    override val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override val isSimulated: Boolean = true

    override fun initialize(apiKey: String, appUserId: String?) {
        _activeOffering.value = RevenueCatConfig.FALLBACK_OFFERING
    }

    override suspend fun fetchOfferings(): SubscriptionOffering? {
        _activeOffering.value = RevenueCatConfig.FALLBACK_OFFERING
        return _activeOffering.value
    }

    override suspend fun purchasePackage(pkg: SubscriptionPackage): Result<Boolean> {
        _isProcessing.value = true
        _lastError.value = null
        delay(900) // Stand in for the store sheet round trip.
        grant(true)
        _isProcessing.value = false
        return Result.success(true)
    }

    override suspend fun restorePurchases(): Result<Boolean> {
        _isProcessing.value = true
        _lastError.value = null
        delay(600)
        grant(true)
        _isProcessing.value = false
        return Result.success(true)
    }

    override suspend fun refreshCustomerInfo(): Boolean = _isSubscribed.value

    override fun clearError() {
        _lastError.value = null
    }

    private fun grant(active: Boolean) {
        _isSubscribed.value = active
        SubscriptionService.setSubscribed(active)
    }
}
