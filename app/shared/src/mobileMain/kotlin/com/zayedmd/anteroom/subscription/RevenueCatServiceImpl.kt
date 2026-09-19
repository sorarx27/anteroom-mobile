package com.zayedmd.anteroom.subscription

import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.configure
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offering
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.PurchasesTransactionException
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreTransaction
import com.revenuecat.purchases.kmp.result.awaitCustomerInfoResult
import com.revenuecat.purchases.kmp.result.awaitOfferingsResult
import com.revenuecat.purchases.kmp.result.awaitPurchaseResult
import com.revenuecat.purchases.kmp.result.awaitRestoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.revenuecat.purchases.kmp.models.Package as RcPackage
import com.revenuecat.purchases.kmp.models.PackageType as RcPackageType

/**
 * This file is compiled into both `androidMain` and `iosMain` (see `app/shared/build.gradle.kts`).
 * `purchases-kmp` exposes one identical API across Google Play and StoreKit, so Anteroom's billing
 * logic is written once and the SDK does the per-store work underneath.
 */
actual fun createRevenueCatService(): RevenueCatService = RevenueCatServiceImpl()

class RevenueCatServiceImpl : RevenueCatService {

    private val _isSubscribed = MutableStateFlow(false)
    override val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _activeOffering = MutableStateFlow<SubscriptionOffering?>(RevenueCatConfig.FALLBACK_OFFERING)
    override val activeOffering: StateFlow<SubscriptionOffering?> = _activeOffering.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    override val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override val isSimulated: Boolean = false

    /** The live offering, kept so a [SubscriptionPackage] can be mapped back to a store product. */
    private var storeOffering: Offering? = null

    override fun initialize(apiKey: String, appUserId: String?) {
        val userId = appUserId

        if (!Purchases.isConfigured) {
            Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(apiKey) { this.appUserId = userId }
            attachDelegate()
            return
        }

        attachDelegate()

        // Already configured — a sign-in happened after launch, so alias the anonymous RevenueCat
        // user onto the Anteroom account instead of reconfiguring (which the SDK forbids).
        if (userId != null && userId != Purchases.sharedInstance.appUserID) {
            Purchases.sharedInstance.logIn(
                newAppUserID = userId,
                onError = { _lastError.value = it.readableMessage() },
                onSuccess = { customerInfo, _ -> applyEntitlement(customerInfo) }
            )
        }
    }

    override suspend fun fetchOfferings(): SubscriptionOffering? {
        if (!Purchases.isConfigured) {
            _activeOffering.value = RevenueCatConfig.FALLBACK_OFFERING
            return _activeOffering.value
        }

        Purchases.sharedInstance.awaitOfferingsResult().fold(
            onSuccess = { offerings ->
                val offering = offerings[RevenueCatConfig.OFFERING_DEFAULT] ?: offerings.current
                storeOffering = offering
                _activeOffering.value = offering?.toSubscriptionOffering()
                    ?: RevenueCatConfig.FALLBACK_OFFERING
            },
            onFailure = { throwable ->
                _lastError.value = throwable.readableMessage()
                // Keep the paywall renderable with dashboard pricing rather than an empty sheet.
                _activeOffering.value = RevenueCatConfig.FALLBACK_OFFERING
            }
        )

        return _activeOffering.value
    }

    override suspend fun purchasePackage(pkg: SubscriptionPackage): Result<Boolean> {
        _isProcessing.value = true
        _lastError.value = null
        try {
            if (!Purchases.isConfigured) {
                return fail("Purchases are not available yet. Reopen Anteroom and try again.")
            }

            // The offering may not have landed yet if the user opened the paywall immediately.
            val target = resolveStorePackage(pkg)
                ?: run { fetchOfferings(); resolveStorePackage(pkg) }
                ?: return fail("That plan is not available on this store right now.")

            return Purchases.sharedInstance.awaitPurchaseResult(packageToPurchase = target).fold(
                onSuccess = { purchase ->
                    val active = applyEntitlement(purchase.customerInfo)
                    if (!active) {
                        _lastError.value =
                            "Purchase went through but Anteroom Pro is not active yet. " +
                                "Try Restore purchases in a moment."
                    }
                    Result.success(active)
                },
                onFailure = { throwable ->
                    // A user backing out of the store sheet is not an error worth shouting about.
                    val cancelled = (throwable as? PurchasesTransactionException)?.userCancelled == true
                    if (!cancelled) _lastError.value = throwable.readableMessage()
                    Result.failure(throwable)
                }
            )
        } finally {
            _isProcessing.value = false
        }
    }

    override suspend fun restorePurchases(): Result<Boolean> {
        _isProcessing.value = true
        _lastError.value = null
        try {
            if (!Purchases.isConfigured) {
                return fail("Purchases are not available yet. Reopen Anteroom and try again.")
            }

            return Purchases.sharedInstance.awaitRestoreResult().fold(
                onSuccess = { customerInfo ->
                    val active = applyEntitlement(customerInfo)
                    if (!active) {
                        _lastError.value =
                            "No previous Anteroom Pro purchase found on this store account."
                    }
                    Result.success(active)
                },
                onFailure = { throwable ->
                    _lastError.value = throwable.readableMessage()
                    Result.failure(throwable)
                }
            )
        } finally {
            _isProcessing.value = false
        }
    }

    override suspend fun refreshCustomerInfo(): Boolean {
        if (!Purchases.isConfigured) return _isSubscribed.value

        return Purchases.sharedInstance.awaitCustomerInfoResult().fold(
            onSuccess = { applyEntitlement(it) },
            onFailure = { _isSubscribed.value } // Offline: keep the last known entitlement.
        )
    }

    override fun clearError() {
        _lastError.value = null
    }

    /**
     * RevenueCat pushes a fresh [CustomerInfo] whenever a subscription renews, lapses or is
     * restored on another device, so entitlement state stays live without the UI polling.
     */
    private fun attachDelegate() {
        if (!Purchases.isConfigured) return

        Purchases.sharedInstance.delegate = object : PurchasesDelegate {
            override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
                applyEntitlement(customerInfo)
            }

            override fun onPurchasePromoProduct(
                product: StoreProduct,
                startPurchase: (
                    onError: (error: PurchasesError, userCancelled: Boolean) -> Unit,
                    onSuccess: (storeTransaction: StoreTransaction, customerInfo: CustomerInfo) -> Unit
                ) -> Unit
            ) {
                // App Store promoted purchase: the user already chose to buy, so skip the paywall.
                startPurchase(
                    { error, userCancelled -> if (!userCancelled) _lastError.value = error.message },
                    { _, customerInfo -> applyEntitlement(customerInfo) }
                )
            }
        }
    }

    /** The single place `anteroom_pro` is read. Returns whether the entitlement is active. */
    private fun applyEntitlement(customerInfo: CustomerInfo): Boolean {
        val active = customerInfo.entitlements[RevenueCatConfig.ENTITLEMENT_ID]?.isActive == true
        _isSubscribed.value = active
        SubscriptionService.setSubscribed(active)
        return active
    }

    private fun resolveStorePackage(pkg: SubscriptionPackage): RcPackage? {
        val offering = storeOffering ?: return null
        return offering.availablePackages.firstOrNull { it.identifier == pkg.identifier }
            ?: if (pkg.isLifetime) offering.lifetime else offering.monthly
    }

    private fun fail(message: String): Result<Boolean> {
        _lastError.value = message
        return Result.failure(IllegalStateException(message))
    }

    private fun Offering.toSubscriptionOffering(): SubscriptionOffering {
        val monthlyPkg = monthly ?: availablePackages
            .firstOrNull { it.packageType == RcPackageType.MONTHLY }
        val lifetimePkg = lifetime ?: availablePackages
            .firstOrNull { it.packageType == RcPackageType.LIFETIME }

        val mappedMonthly = monthlyPkg?.toSubscriptionPackage(RevenueCatConfig.FALLBACK_MONTHLY)
        val mappedLifetime = lifetimePkg?.toSubscriptionPackage(RevenueCatConfig.FALLBACK_LIFETIME)

        return SubscriptionOffering(
            identifier = identifier,
            monthly = mappedMonthly,
            lifetime = mappedLifetime,
            availablePackages = listOfNotNull(mappedMonthly, mappedLifetime)
        )
    }

    /**
     * Store metadata wins for anything the user is charged on (price, currency); [fallback]
     * supplies the marketing copy, which Google Play and StoreKit both mangle.
     */
    private fun RcPackage.toSubscriptionPackage(fallback: SubscriptionPackage): SubscriptionPackage {
        val product = storeProduct
        val lifetime = packageType == RcPackageType.LIFETIME

        return SubscriptionPackage(
            identifier = identifier,
            packageType = if (lifetime) PackageType.LIFETIME else PackageType.MONTHLY,
            title = product.title.ifBlank { fallback.title },
            description = product.localizedDescription?.takeIf { it.isNotBlank() }
                ?: fallback.description,
            priceString = product.price.formatted,
            priceMicros = product.price.amountMicros,
            currencyCode = product.price.currencyCode,
            isLifetime = lifetime
        )
    }
}

/**
 * What the paywall shows the user.
 *
 * Deliberately not `underlyingErrorMessage`, which this used to prefer.
 * That field is RevenueCat's developer diagnostic, and on a build whose store
 * products are not registered yet it rendered six lines about the dashboard —
 * two URLs included — in red, inside the purchase sheet, where a customer or
 * an App Review screenshot would see it.
 *
 * The diagnostic is still worth having, so it goes to the log. The enum is the
 * stable thing to branch on; `message` is the fallback because RevenueCat
 * writes it for humans, unlike the underlying error.
 */
private fun PurchasesError.readableMessage(): String {
    underlyingErrorMessage?.takeIf { it.isNotBlank() }?.let {
        println("RevenueCat $code: $it")
    }
    return when (code) {
        PurchasesErrorCode.NetworkError,
        PurchasesErrorCode.OfflineConnectionError ->
            "Couldn't reach the store. Check your connection and try again."

        PurchasesErrorCode.StoreProblemError,
        PurchasesErrorCode.UnknownBackendError,
        PurchasesErrorCode.UnexpectedBackendResponseError ->
            "The store is having trouble right now. Try again in a moment."

        // ConfigurationError is the one behind the wall of red text: it is what
        // the SDK returns when the offering has no products on this store.
        PurchasesErrorCode.ConfigurationError,
        PurchasesErrorCode.ProductNotAvailableForPurchaseError,
        PurchasesErrorCode.UnsupportedError ->
            "Plans aren't available on this device right now."

        PurchasesErrorCode.PurchaseNotAllowedError,
        PurchasesErrorCode.InsufficientPermissionsError ->
            "This device isn't allowed to make purchases."

        PurchasesErrorCode.PurchaseInvalidError,
        PurchasesErrorCode.InvalidReceiptError ->
            "The store couldn't complete that purchase. Try again, or use another payment method."

        PurchasesErrorCode.ProductAlreadyPurchasedError ->
            "You already own this. Tap Restore purchases."

        PurchasesErrorCode.ReceiptAlreadyInUseError,
        PurchasesErrorCode.ReceiptInUseByOtherSubscriberError,
        PurchasesErrorCode.PurchaseBelongsToOtherUser ->
            "That purchase belongs to another account. Sign in with the account that bought it."

        PurchasesErrorCode.PaymentPendingError ->
            "The payment is still pending. Anteroom Pro unlocks as soon as it clears."

        else -> message.takeIf { it.isNotBlank() }
            ?: "Something went wrong talking to the store."
    }
}

private fun Throwable.readableMessage(): String = when (this) {
    is PurchasesException -> error.readableMessage()
    else -> message?.takeIf { it.isNotBlank() }
        ?: "Something went wrong talking to the store."
}
