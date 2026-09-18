package com.zayedmd.anteroom

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.zayedmd.anteroom.firebase.initializeFirebaseIfNeeded

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // See the Desktop entry point: the browser build has no platform hook
    // that configures Firebase either.
    initializeFirebaseIfNeeded()

    ComposeViewport {
        App()
    }
}
