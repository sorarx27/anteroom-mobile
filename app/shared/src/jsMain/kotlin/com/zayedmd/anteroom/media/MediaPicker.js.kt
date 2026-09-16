package com.zayedmd.anteroom.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.js.Date

@Composable
actual fun rememberMediaPicker(
    onPhotosPicked: (List<CapturedPhoto>) -> Unit,
    onPermissionDenied: ((PermissionResult) -> Unit)?
): MediaPicker {
    return remember {
        object : MediaPicker {
            override fun launchCamera() {
                onPhotosPicked(
                    listOf(
                        CapturedPhoto(
                            id = "photo_${Date.now().toLong()}",
                            name = "document_capture.jpg",
                            mimeType = "image/jpeg"
                        )
                    )
                )
            }

            override fun launchGallery() {
                onPhotosPicked(
                    listOf(
                        CapturedPhoto(
                            id = "photo_${Date.now().toLong()}",
                            name = "gallery_document.jpg",
                            mimeType = "image/jpeg"
                        )
                    )
                )
            }
        }
    }
}
