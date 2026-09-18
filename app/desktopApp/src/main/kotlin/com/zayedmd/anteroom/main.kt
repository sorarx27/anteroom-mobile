package com.zayedmd.anteroom

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.zayedmd.anteroom.firebase.initializeFirebaseIfNeeded

fun main() {
    // Desktop has no google-services plugin and no FirebaseApp.configure(),
    // so nothing had configured Firebase here at all. Every call failed and
    // the old blanket catch turned that into an empty-looking account.
    initializeFirebaseIfNeeded()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Anteroom",
        ) {
            App()
        }
    }
}
