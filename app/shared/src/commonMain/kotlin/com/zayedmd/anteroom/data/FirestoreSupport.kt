package com.zayedmd.anteroom.data

import com.zayedmd.anteroom.firebase.FirebaseService
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Thrown when a repository is called without a signed-in Firebase user. */
class NotSignedInException : IllegalStateException("You need to be signed in to do that.")

/** The signed-in Firebase uid, or throws. Every Firestore path and every
 *  security rule keys off this, so there is no sensible fallback. */
fun requireUid(): String =
    FirebaseService.auth.currentUser?.uid ?: throw NotSignedInException()

/**
 * Current UTC time as a fixed-width ISO-8601 string, for example
 * `2026-09-17T19:30:00.123Z`.
 *
 * Two deliberate choices. Timestamps are **strings, never Firestore
 * Timestamps**: the Kotlin models type them as `String`, and a real Timestamp
 * would fail to decode and make the document disappear behind a swallowed
 * exception. And the width is **fixed** — milliseconds are always present —
 * because `orderBy` on a string is lexicographic, and kotlinx-datetime's own
 * `Instant.toString()` drops trailing zeros, which would sort
 * `...:00.500Z` before `...:00Z`.
 *
 * The Python Cloud Functions must emit this exact shape.
 *
 * Note the clock comes from `kotlin.time`, not `kotlinx.datetime`:
 * kotlinx-datetime resolves to 0.7.x here (something upgrades it
 * transitively), and 0.7 moved `Clock` into the standard library.
 */
@OptIn(ExperimentalTime::class)
fun nowIso(): String {
    val dt = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    return buildString {
        append(dt.year.toString().padStart(4, '0'))
        append('-')
        append(dt.monthNumber.toString().padStart(2, '0'))
        append('-')
        append(dt.dayOfMonth.toString().padStart(2, '0'))
        append('T')
        append(dt.hour.toString().padStart(2, '0'))
        append(':')
        append(dt.minute.toString().padStart(2, '0'))
        append(':')
        append(dt.second.toString().padStart(2, '0'))
        append('.')
        append((dt.nanosecond / 1_000_000).toString().padStart(3, '0'))
        append('Z')
    }
}

/**
 * Document id of the user's own profile.
 *
 * Pinned to a constant rather than generated. Security rules cannot count
 * documents, so "exactly one self profile" is enforced structurally: the
 * collection physically cannot hold two documents with this id, and the rule
 * only has to check `is_self == (docId == "self")`. It also makes bootstrap
 * idempotent — a plain `set()` with no read-then-write race.
 */
const val SELF_PROFILE_ID: String = "self"
