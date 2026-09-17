package com.zayedmd.anteroom.ui.components

import androidx.compose.ui.window.DialogProperties
import com.zayedmd.anteroom.ui.theme.AnteroomColors

/**
 * Compiled into iosMain, jvmMain and jsMain - every target backed by skiko, where
 * `DialogProperties` carries `scrimColor`.
 */
actual fun paywallDialogProperties(): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    scrimColor = AnteroomColors.Surface
)
