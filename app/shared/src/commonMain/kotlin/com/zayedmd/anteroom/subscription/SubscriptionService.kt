package com.zayedmd.anteroom.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SubscriptionManager {
    val isSubscribed: StateFlow<Boolean>
    fun setSubscribed(subscribed: Boolean)
}

/**
 * App-wide read model for `anteroom_pro`. Every paywall guard in the UI collects this flow, which
 * is why exactly one writer is allowed: [RevenueCatService], after it has verified the entitlement
 * against RevenueCat's customer info. Do not call [setSubscribed] from the UI
 * - an optimistic unlock here would survive a failed or refunded purchase.
 */
object SubscriptionService : SubscriptionManager {
    // Free tier until RevenueCat says otherwise.
    private val _isSubscribed = MutableStateFlow(false)
    override val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    override fun setSubscribed(subscribed: Boolean) {
        _isSubscribed.value = subscribed
    }
}
