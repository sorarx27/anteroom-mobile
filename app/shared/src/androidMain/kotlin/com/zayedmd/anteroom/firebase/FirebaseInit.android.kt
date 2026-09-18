package com.zayedmd.anteroom.firebase

/** The google-services Gradle plugin has already done this by the time any
 *  Kotlin runs; calling `Firebase.initialize` again would throw. */
actual fun initializeFirebaseIfNeeded() = Unit
