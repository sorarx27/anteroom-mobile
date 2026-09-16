package com.zayedmd.anteroom.subscription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SubscriptionManager {
    val isSubscribed: StateFlow<Boolean>
    fun setSubscribed(subscribed: Boolean)
}

object SubscriptionService : SubscriptionManager {
    // Default to free tier for testing paywall guards
    private val _isSubscribed = MutableStateFlow(false)
    override val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    override fun setSubscribed(subscribed: Boolean) {
        _isSubscribed.value = subscribed
    }
}
