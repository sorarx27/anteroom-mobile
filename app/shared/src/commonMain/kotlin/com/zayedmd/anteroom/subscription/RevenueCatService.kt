package com.zayedmd.anteroom.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface RevenueCatService {
    val isSubscribed: StateFlow<Boolean>
    val activeOffering: StateFlow<SubscriptionOffering?>
    val isProcessing: StateFlow<Boolean>
    val lastError: StateFlow<String?>

    fun initialize(apiKey: String, appUserId: String?)
    suspend fun fetchOfferings(): SubscriptionOffering?
    suspend fun purchasePackage(pkg: SubscriptionPackage): Result<Boolean>
    suspend fun restorePurchases(): Result<Boolean>
    fun clearError()
}

class RevenueCatServiceImpl : RevenueCatService {

    companion object {
        const val ENTITLEMENT_ID = "anteroom_pro"
        const val OFFERING_DEFAULT = "default"
        const val PACKAGE_MONTHLY = "monthly"
        const val PACKAGE_LIFETIME = "lifetime"

        val SANDBOX_MONTHLY = SubscriptionPackage(
            identifier = PACKAGE_MONTHLY,
            packageType = PackageType.MONTHLY,
            title = "Anteroom Pro Monthly",
            description = "Unlimited family profiles, clean doctor export & translation.",
            priceString = "$7.99/mo",
            priceMicros = 7990000L,
            isLifetime = false
        )

        val SANDBOX_LIFETIME = SubscriptionPackage(
            identifier = PACKAGE_LIFETIME,
            packageType = PackageType.LIFETIME,
            title = "Anteroom Pro Lifetime",
            description = "Permanent access. One-time unlock, all features forever.",
            priceString = "$129.99",
            priceMicros = 129990000L,
            isLifetime = true
        )

        val SANDBOX_OFFERING = SubscriptionOffering(
            identifier = OFFERING_DEFAULT,
            monthly = SANDBOX_MONTHLY,
            lifetime = SANDBOX_LIFETIME
        )
    }

    private val _isSubscribed = MutableStateFlow(false)
    override val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _activeOffering = MutableStateFlow<SubscriptionOffering?>(SANDBOX_OFFERING)
    override val activeOffering: StateFlow<SubscriptionOffering?> = _activeOffering.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    override val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override fun initialize(apiKey: String, appUserId: String?) {
        // Will be augmented with native SDK bindings
    }

    override suspend fun fetchOfferings(): SubscriptionOffering? {
        _activeOffering.value = SANDBOX_OFFERING
        return SANDBOX_OFFERING
    }

    override suspend fun purchasePackage(pkg: SubscriptionPackage): Result<Boolean> {
        _isProcessing.value = true
        _lastError.value = null
        kotlinx.coroutines.delay(1200) // Simulate sandbox store transaction
        _isSubscribed.value = true
        SubscriptionService.setSubscribed(true)
        _isProcessing.value = false
        return Result.success(true)
    }

    override suspend fun restorePurchases(): Result<Boolean> {
        _isProcessing.value = true
        _lastError.value = null
        kotlinx.coroutines.delay(1000)
        _isSubscribed.value = true
        SubscriptionService.setSubscribed(true)
        _isProcessing.value = false
        return Result.success(true)
    }

    override fun clearError() {
        _lastError.value = null
    }
}
