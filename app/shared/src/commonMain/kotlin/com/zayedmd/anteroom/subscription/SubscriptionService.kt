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
    /**
     * Every feature unlocked for every account, and nothing for sale.
     *
     * Anteroom ships free while its publisher's residence status does not
     * allow selling it. With this on, every Pro guard in the UI passes, the
     * Upgrade surfaces never render, and RevenueCat is never configured, so
     * no store product is requested. The server has the same switch
     * (`FREE_FOR_EVERYONE` in functions/anteroom/config.py) and it is the one
     * that matters for the watermark and translation; the two must agree.
     */
    const val FREE_FOR_EVERYONE = true

    private val _isSubscribed = MutableStateFlow(FREE_FOR_EVERYONE)
    override val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    override fun setSubscribed(subscribed: Boolean) {
        _isSubscribed.value = FREE_FOR_EVERYONE || subscribed
    }
}
