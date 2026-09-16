package com.zayedmd.anteroom.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

@Composable
actual fun rememberMediaPicker(
    onPhotosPicked: (List<CapturedPhoto>) -> Unit,
    onPermissionDenied: ((PermissionResult) -> Unit)?
): MediaPicker {
    val context = LocalContext.current
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAction by remember { mutableStateOf<PermissionType?>(null) }

    // Take Picture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = tempCameraUri
        if (success && uri != null) {
            val photo = CapturedPhoto(
                id = UUID.randomUUID().toString(),
                name = "photo_${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg",
                uri = uri.toString(),
                bytes = readBytesFromUri(context, uri)
            )
            onPhotosPicked(listOf(photo))
        }
    }

    // Gallery Launcher (Multiple)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            val photos = uris.map { uri ->
                CapturedPhoto(
                    id = UUID.randomUUID().toString(),
                    name = "doc_${System.currentTimeMillis()}.jpg",
                    mimeType = context.contentResolver.getType(uri) ?: "image/jpeg",
                    uri = uri.toString(),
                    bytes = readBytesFromUri(context, uri)
                )
            }
            onPhotosPicked(photos)
        }
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val action = pendingAction
        pendingAction = null
        if (isGranted) {
            when (action) {
                PermissionType.Camera -> {
                    val uri = createTempImageUri(context)
                    tempCameraUri = uri
                    cameraLauncher.launch(uri)
                }
                PermissionType.Gallery -> {
                    galleryLauncher.launch("image/*")
                }
                null -> {}
            }
        } else if (action != null) {
            onPermissionDenied?.invoke(
                PermissionResult(
                    status = PermissionStatus.Denied,
                    type = action
                )
            )
        }
    }

    return remember(context) {
        object : MediaPicker {
            override fun launchCamera() {
                val hasCamera = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (hasCamera) {
                    val uri = createTempImageUri(context)
                    tempCameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    pendingAction = PermissionType.Camera
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            override fun launchGallery() {
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val hasStorage = ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED

                // On newer Android versions (PhotoPicker / GetMultipleContents) permission is often not required,
                // but checking ensures standard compatibility
                if (hasStorage || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    galleryLauncher.launch("image/*")
                } else {
                    pendingAction = PermissionType.Gallery
                    permissionLauncher.launch(permission)
                }
            }
        }
    }
}

private fun createTempImageUri(context: Context): Uri {
    val tempDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    val tempFile = File.createTempFile("capture_", ".jpg", tempDir)
    val authority = "${context.packageName}.fileprovider"
    return try {
        FileProvider.getUriForFile(context, authority, tempFile)
    } catch (_: Exception) {
        Uri.fromFile(tempFile)
    }
}

private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}
