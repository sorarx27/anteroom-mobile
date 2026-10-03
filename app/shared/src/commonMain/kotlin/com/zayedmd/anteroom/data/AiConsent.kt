package com.zayedmd.anteroom.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's permission to send their documents to a third-party AI.
 *
 * App Store Review Guideline 5.1.2(i) requires an app to say clearly, inside
 * the app, when personal data goes to a third-party AI and to get explicit
 * permission before it does. Anteroom's pages are medical paperwork and every
 * brief is written by Gemini on Vertex AI, so this is not a formality: App
 * Review asked about it directly when rejecting 1.0 (4).
 *
 * The check lives here rather than in each screen because there are four
 * separate ways a page reaches Gemini (classify on draft load, re-detect,
 * generate, translate) and a gate on only some of them is no gate at all.
 * [BriefsRepositoryImpl] calls [require] before every model call; the snap
 * button calls [ensure] so the question comes before the camera, not after.
 *
 * Stored per account and per device. The key carries a version so that a
 * change of AI provider can ask again rather than lean on an old answer.
 */
object AiConsent {
    private const val KEY_PREFIX = "ai_consent_v1_"

    private val settings: Settings by lazy { Settings() }
    private var userId: String? = null

    private val _granted = MutableStateFlow(false)
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    /** Non-null while the consent dialog should be on screen. */
    private val _request = MutableStateFlow<CompletableDeferred<Boolean>?>(null)
    val request: StateFlow<CompletableDeferred<Boolean>?> = _request.asStateFlow()

    fun bind(userId: String?) {
        // A question asked of the previous account is not one this account answered.
        _request.value?.complete(false)
        _request.value = null
        this.userId = userId
        _granted.value = userId != null && settings.getBoolean(KEY_PREFIX + userId, false)
    }

    /** True if the user has agreed, asking first if they have not been asked. */
    suspend fun ensure(): Boolean {
        if (_granted.value) return true
        val pending = _request.value ?: CompletableDeferred<Boolean>().also { _request.value = it }
        return pending.await()
    }

    /**
     * [ensure], but a refusal ends the calling coroutine quietly. A
     * [CancellationException] passes straight through `launchSafely` and
     * `runSafely`, so declining shows no error banner.
     */
    suspend fun require() {
        if (!ensure()) throw AiConsentDeclined()
    }

    fun answer(agreed: Boolean) {
        if (agreed) persist(true)
        val pending = _request.value
        _request.value = null
        pending?.complete(agreed)
    }

    fun withdraw() = persist(false)

    private fun persist(value: Boolean) {
        _granted.value = value
        userId?.let { settings.putBoolean(KEY_PREFIX + it, value) }
    }
}

class AiConsentDeclined : CancellationException("The user did not agree to AI processing")
