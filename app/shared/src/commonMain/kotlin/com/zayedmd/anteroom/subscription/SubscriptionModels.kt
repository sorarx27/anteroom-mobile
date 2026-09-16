package com.zayedmd.anteroom.subscription

import kotlinx.serialization.Serializable

@Serializable
enum class PackageType {
    MONTHLY,
    LIFETIME
}

@Serializable
data class SubscriptionPackage(
    val identifier: String,
    val packageType: PackageType,
    val title: String,
    val description: String,
    val priceString: String,
    val priceMicros: Long,
    val currencyCode: String = "USD",
    val isLifetime: Boolean = false
)

@Serializable
data class SubscriptionOffering(
    val identifier: String,
    val monthly: SubscriptionPackage?,
    val lifetime: SubscriptionPackage?,
    val availablePackages: List<SubscriptionPackage> = listOfNotNull(monthly, lifetime)
)

@Serializable
data class CustomerEntitlementInfo(
    val entitlementId: String,
    val isActive: Boolean,
    val willRenew: Boolean = false,
    val expirationDate: String? = null,
    val latestPurchaseDate: String? = null
)
