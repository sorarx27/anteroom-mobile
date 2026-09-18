package com.zayedmd.anteroom.firebase

import dev.gitlive.firebase.FirebaseOptions

/**
 * Client configuration for the Firebase project.
 *
 * Not secrets. These identify the project and grant nothing on their own,
 * exactly like the `google-services.json` and `GoogleService-Info.plist`
 * already committed beside them; security rules are what stand between this
 * and the data.
 *
 * Android and iOS never read this. Android's google-services Gradle plugin
 * and iOS's `FirebaseApp.configure()` in `iOSApp.swift` both run before any
 * Kotlin does. Desktop and Web have no such hook, which is why they were
 * quietly unconfigured: every Firebase call on those targets failed, and the
 * old blanket `catch { }` rendered that as an empty account rather than a
 * broken one.
 */
internal val ANTEROOM_FIREBASE_OPTIONS = FirebaseOptions(
    applicationId = "1:458778941992:web:447c2a9f74f31962af331d",
    apiKey = "AIzaSyCIcfExcXOAni-urkMpX7DKUFtkiedHyPg",
    projectId = "anteroom-d2e72",
    storageBucket = "anteroom-d2e72.firebasestorage.app",
    gcmSenderId = "458778941992",
    authDomain = "anteroom-d2e72.firebaseapp.com"
)

/**
 * Brings up the default Firebase app where the platform hasn't already.
 *
 * Call once, before anything touches [FirebaseService]. A no-op on Android
 * and iOS. Idempotent everywhere.
 */
expect fun initializeFirebaseIfNeeded()
