package com.zayedmd.anteroom.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

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
                            id = "photo_${System.currentTimeMillis()}",
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
                            id = "photo_${System.currentTimeMillis()}",
                            name = "gallery_document.jpg",
                            mimeType = "image/jpeg"
                        )
                    )
                )
            }
        }
    }
}
