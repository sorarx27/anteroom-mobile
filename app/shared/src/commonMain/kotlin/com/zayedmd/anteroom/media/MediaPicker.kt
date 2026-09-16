package com.zayedmd.anteroom.media

import androidx.compose.runtime.Composable

data class CapturedPhoto(
    val id: String,
    val name: String,
    val mimeType: String = "image/jpeg",
    val uri: String? = null,
    val bytes: ByteArray? = null,
    val size: Long = bytes?.size?.toLong() ?: 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CapturedPhoto
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

enum class PermissionStatus {
    Granted,
    Denied,
    Blocked
}

data class PermissionResult(
    val status: PermissionStatus,
    val type: PermissionType
)

enum class PermissionType {
    Camera,
    Gallery
}

interface MediaPicker {
    fun launchCamera()
    fun launchGallery()
}

@Composable
expect fun rememberMediaPicker(
    onPhotosPicked: (List<CapturedPhoto>) -> Unit,
    onPermissionDenied: ((PermissionResult) -> Unit)? = null
): MediaPicker
