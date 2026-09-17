package com.zayedmd.anteroom.ui.components

import androidx.compose.ui.window.DialogProperties

/**
 * Compose Multiplatform's `DialogProperties` is an expect class, and its members differ per
 * platform: the skiko actual (iOS, Desktop, Web) exposes `scrimColor`, the Android actual does
 * not. The paywall needs the scrim painted in the sheet colour on iOS, where the Dialog window is
 * inset and the default dim would otherwise show as grey bands at the status bar and home
 * indicator. Android already renders the sheet full-bleed, so it keeps the default scrim.
 */
expect fun paywallDialogProperties(): DialogProperties
