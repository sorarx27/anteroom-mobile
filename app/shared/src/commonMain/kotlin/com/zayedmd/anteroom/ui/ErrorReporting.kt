package com.zayedmd.anteroom.ui

import com.zayedmd.anteroom.data.NotSignedInException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One place for "something went wrong" to end up.
 *
 * The repositories used to swallow every failure and return `MockData`, so
 * nothing ever needed to be reported. Now that they throw, an unhandled
 * exception inside a `scope.launch` takes the whole app down — so removing
 * the fallbacks without adding this would have traded a silent wrong answer
 * for a crash. Neither is acceptable during a recorded demo.
 */
object AnteroomErrors {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun report(throwable: Throwable) {
        _message.value = throwable.userMessage()
    }

    fun report(text: String) {
        _message.value = text
    }

    fun clear() {
        _message.value = null
    }
}

/**
 * Turns a Firebase failure into a sentence.
 *
 * The SDK's own messages are things like
 * `PERMISSION_DENIED: Missing or insufficient permissions.`, which tells a
 * patient nothing and tells a judge watching the demo something worse. The
 * matching is on substrings rather than exception types on purpose: GitLive
 * surfaces the Android, Apple and web errors as different classes with the
 * same wording.
 */
fun Throwable.userMessage(): String {
    if (this is NotSignedInException) return message ?: "You need to be signed in to do that."

    val raw = message.orEmpty()
    return when {
        raw.contains("PERMISSION_DENIED", ignoreCase = true) ||
            raw.contains("insufficient permissions", ignoreCase = true) ->
            "You don't have access to that. Try signing out and back in."

        raw.contains("UNAVAILABLE", ignoreCase = true) ||
            raw.contains("offline", ignoreCase = true) ||
            raw.contains("network", ignoreCase = true) ->
            "You seem to be offline. Check your connection and try again."

        raw.contains("DEADLINE_EXCEEDED", ignoreCase = true) ||
            raw.contains("timeout", ignoreCase = true) ->
            "That took longer than expected. Try again."

        raw.contains("UNAUTHENTICATED", ignoreCase = true) ->
            "Your session expired. Sign in again to continue."

        raw.isNotBlank() -> raw
        else -> "Something went wrong. Please try again."
    }
}

/**
 * `launch`, but a failure reports itself instead of reaching the default
 * handler — which on Android is a crash and on iOS is a silent no-op.
 *
 * Cancellation is rethrown: swallowing it would break structured concurrency
 * and leave a composable's scope alive after it leaves the composition.
 */
fun CoroutineScope.launchSafely(
    onError: (Throwable) -> Unit = AnteroomErrors::report,
    block: suspend CoroutineScope.() -> Unit
): Job = launch {
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        onError(throwable)
    }
}

/** The `LaunchedEffect` equivalent of [launchSafely]. */
suspend fun runSafely(
    onError: (Throwable) -> Unit = AnteroomErrors::report,
    block: suspend () -> Unit
) {
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        onError(throwable)
    }
}
