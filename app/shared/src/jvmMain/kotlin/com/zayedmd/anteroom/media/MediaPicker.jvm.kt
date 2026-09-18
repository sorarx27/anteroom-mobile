package com.zayedmd.anteroom.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Desktop file chooser.
 *
 * Replaces a stub that fabricated a `CapturedPhoto` with no bytes on every
 * call, so the UI advanced as though a page had been added while nothing was
 * ever read.
 *
 * `java.awt.FileDialog` rather than Swing's `JFileChooser`: on macOS it is
 * the native panel, which handles sandboxed locations like Photos Library and
 * iCloud Drive that `JFileChooser` cannot see.
 *
 * There is no camera path. Desktop machines may have a webcam, but a webcam
 * pointed at paperwork produces something illegible, and offering a button
 * that reliably yields an unreadable page is worse than not offering it --
 * `launchCamera` opens the same chooser.
 */
@Composable
actual fun rememberMediaPicker(
    onPhotosPicked: (List<CapturedPhoto>) -> Unit,
    onPermissionDenied: ((PermissionResult) -> Unit)?
): MediaPicker {
    val currentOnPicked by rememberUpdatedState(onPhotosPicked)
    val scope = rememberCoroutineScope()

    return remember(scope) {
        object : MediaPicker {
            // No permission model on Desktop: choosing the file *is* the grant.
            override fun launchCamera() = launchGallery()

            override fun launchGallery() {
                scope.launch {
                    val photos = withContext(Dispatchers.IO) { chooseImages() }
                    if (photos.isNotEmpty()) currentOnPicked(photos)
                }
            }
        }
    }
}

private fun chooseImages(): List<CapturedPhoto> {
    val dialog = FileDialog(null as Frame?, "Add pages", FileDialog.LOAD).apply {
        isMultipleMode = true
        setFilenameFilter { _, name -> name.lowercase().substringAfterLast('.') in IMAGE_EXTENSIONS }
        isVisible = true
    }

    return dialog.files.orEmpty().mapNotNull { file -> file.toCapturedPhoto() }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

private fun File.toCapturedPhoto(): CapturedPhoto? {
    val extension = name.lowercase().substringAfterLast('.', "")
    if (extension !in IMAGE_EXTENSIONS) return null
    val bytes = runCatching { readBytes() }.getOrNull() ?: return null
    return CapturedPhoto(
        id = UUID.randomUUID().toString(),
        name = name,
        mimeType = when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        },
        bytes = bytes,
        size = bytes.size.toLong()
    )
}
