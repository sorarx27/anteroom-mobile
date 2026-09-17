package com.zayedmd.anteroom.ui.components

import androidx.compose.ui.window.DialogProperties

/** Android's `DialogProperties` has no `scrimColor`; the sheet already fills the screen there. */
actual fun paywallDialogProperties(): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = false
)
