package com.zayedmd.anteroom.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.storage.storage

/**
 * Entry points for the Firebase products the app uses.
 *
 * Every property is `by lazy` deliberately. GitLive 2.1.0 ships no JVM implementation for
 * Storage — `Storage_jvmKt.getStorage()` throws `NotImplementedError` — and with eager `val`s
 * that one unsupported product took the entire object down with `ExceptionInInitializerError`
 * on Desktop, dragging Auth, Firestore and Functions with it. Lazy initialisation keeps the
 * failure scoped to the property that is genuinely unavailable.
 */
object FirebaseService {

    /**
     * Must match `REGION` in `functions/anteroom/config.py`. Firestore for
     * this project is `eur3`, so the functions live in europe-west1 to keep
     * data in the EU and avoid a transatlantic hop per call. GitLive defaults
     * to us-central1, and a mismatch surfaces as an opaque NOT_FOUND rather
     * than anything that mentions regions.
     */
    const val FUNCTIONS_REGION = "europe-west1"

    val auth by lazy { Firebase.auth }
    val firestore by lazy { Firebase.firestore }
    val functions by lazy { Firebase.functions(FUNCTIONS_REGION) }
    val storage by lazy { Firebase.storage }

    /**
     * False on Desktop (JVM), where Storage has no implementation. Probed rather than hard-coded
     * per platform, so it corrects itself if GitLive fills the gap. `runCatching` catches
     * `Throwable`, which matters here: `NotImplementedError` is an `Error`, not an `Exception`.
     */
    val storageSupported: Boolean by lazy { runCatching { Firebase.storage }.isSuccess }
}
