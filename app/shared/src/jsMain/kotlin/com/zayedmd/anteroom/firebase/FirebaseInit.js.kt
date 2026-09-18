package com.zayedmd.anteroom.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize

private var initialized = false

actual fun initializeFirebaseIfNeeded() {
    if (initialized) return
    initialized = true
    // The JS SDK takes no context; `initializeApp(options)` is the whole job.
    Firebase.initialize(context = null, options = ANTEROOM_FIREBASE_OPTIONS)
}
