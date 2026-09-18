package com.zayedmd.anteroom.firebase

import android.app.Application
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize

private var initialized = false

/**
 * Desktop needs two things Android and iOS get for free.
 *
 * GitLive's JVM target runs on `firebase-java-sdk`, a port of the Android SDK
 * that ships stub `android.*` classes. Two things follow, and each one fails
 * late and unhelpfully if you get it wrong.
 *
 * The context cannot be null. `Firebase.initialize` casts it to
 * `android.content.Context` and null-checks it, so passing `null` -- what the
 * Web target does -- leaves the default app uninitialised, and the first
 * `FirebaseService.auth` call reports "Default FirebaseApp is not initialized
 * in this process" from somewhere else entirely.
 *
 * And it must be an `Application`, not a bare `Context`. Auth is happy with
 * either; Firestore casts to `android.app.Application` internally and a
 * `Context` gets you "Internal error in Cloud Firestore" wrapping a
 * ClassCastException, on its own async queue, after sign-in has already
 * succeeded.
 *
 * `FirebasePlatform` is the port's replacement for Android's Context-backed
 * SharedPreferences and Log.
 */
actual fun initializeFirebaseIfNeeded() {
    if (initialized) return
    initialized = true

    FirebasePlatform.initializeFirebasePlatform(DesktopFirebasePlatform)
    Firebase.initialize(context = Application(), options = ANTEROOM_FIREBASE_OPTIONS)
}

/**
 * In-memory token storage, deliberately.
 *
 * The obvious alternative is a file under the user's home, which is what the
 * sample implementations do -- but the value being stored is a Firebase
 * refresh token, and this account holds photographs of medical documents.
 * Writing a long-lived credential to the disk unencrypted would leave
 * standing access to that data on any machine the app is opened on.
 *
 * The cost is that Desktop asks for sign-in each launch. For a companion
 * target to the phone apps that is the right side of the trade.
 */
private object DesktopFirebasePlatform : FirebasePlatform() {
    private val values = mutableMapOf<String, String>()

    override fun store(key: String, value: String) {
        values[key] = value
    }

    override fun retrieve(key: String): String? = values[key]

    override fun clear(key: String) {
        values.remove(key)
    }

    override fun log(msg: String) {
        println("[firebase] $msg")
    }
}
