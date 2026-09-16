package com.zayedmd.anteroom.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@Composable
actual fun rememberMediaPicker(
    onPhotosPicked: (List<CapturedPhoto>) -> Unit,
    onPermissionDenied: ((PermissionResult) -> Unit)?
): MediaPicker {
    return remember {
        object : MediaPicker {
            override fun launchCamera() {
                val timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                onPhotosPicked(
                    listOf(
                        CapturedPhoto(
                            id = "photo_$timestamp",
                            name = "document_capture.jpg",
                            mimeType = "image/jpeg"
                        )
                    )
                )
            }

            override fun launchGallery() {
                val timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                onPhotosPicked(
                    listOf(
                        CapturedPhoto(
                            id = "photo_$timestamp",
                            name = "gallery_document.jpg",
                            mimeType = "image/jpeg"
                        )
                    )
                )
            }
        }
    }
}
