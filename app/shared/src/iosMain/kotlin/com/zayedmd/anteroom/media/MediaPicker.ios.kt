package com.zayedmd.anteroom.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.zayedmd.anteroom.platform.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Long edge the image is downscaled to before upload.
 *
 * Matches `IMAGE_LONG_EDGE` in `functions/anteroom/config.py`, where the
 * server does the same thing before handing pages to the model. Doing it here
 * as well is not redundant: it is the difference between uploading a 4 MB HEIC
 * over cellular and uploading ~250 KB, and it keeps every page comfortably
 * under the 8 MB ceiling in `storage.rules`.
 */
private const val MAX_LONG_EDGE = 1536.0
private const val JPEG_QUALITY = 0.85

/** Everything is re-encoded, so the content type is known rather than guessed. */
private const val JPEG_TYPE = "image/jpeg"

/**
 * The photo library picker and the camera.
 *
 * This replaced a stub that fabricated a `CapturedPhoto` with no bytes and no
 * URI on every call — the UI advanced as if a page had been added and nothing
 * had been read from disk.
 *
 * Gallery uses `PHPickerViewController`, which runs out of process: it needs
 * no photo-library permission, shows no prompt, and works on the Simulator.
 * Camera uses `UIImagePickerController`, which does need `NSCameraUsageDescription`
 * and real hardware.
 */
@Composable
actual fun rememberMediaPicker(
    onPhotosPicked: (List<CapturedPhoto>) -> Unit,
    onPermissionDenied: ((PermissionResult) -> Unit)?
): MediaPicker {
    // The Objective-C delegates outlive any single composition, so they read
    // the callbacks through an updated state rather than capturing the
    // instance they were created with.
    val currentOnPicked by rememberUpdatedState(onPhotosPicked)

    val scope = remember { CoroutineScope(Dispatchers.Main) }
    DisposableEffect(Unit) { onDispose { scope.cancel() } }

    // UIKit holds delegates weakly. Without a strong reference on this side
    // they are collected as soon as the presenting call returns and the picker
    // finishes into nothing.
    val galleryDelegate = remember { GalleryPickerDelegate(scope) }
    val cameraDelegate = remember { CameraPickerDelegate() }

    return remember(scope) {
        object : MediaPicker {
            override fun launchCamera() {
                if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceTypeCamera)) {
                    // The Simulator has no camera, and VisionKit's scanner
                    // doesn't exist there either. Falling through to the
                    // library keeps the flow exercisable for the iOS parity
                    // shot instead of dead-ending on a device that genuinely
                    // has no camera to grant access to.
                    launchGallery()
                    return
                }
                cameraDelegate.onPhotos = { currentOnPicked(it) }
                val controller = UIImagePickerController().apply {
                    setSourceType(UIImagePickerControllerSourceTypeCamera)
                    setAllowsEditing(false)
                    setDelegate(cameraDelegate)
                }
                topViewController()?.presentViewController(controller, animated = true, completion = null)
            }

            override fun launchGallery() {
                galleryDelegate.onPhotos = { currentOnPicked(it) }
                val configuration = PHPickerConfiguration().apply {
                    setFilter(PHPickerFilter.imagesFilter)
                    // One short of the brief's page cap is not the goal here;
                    // the cap is enforced in PhotoUploadService against what
                    // the brief already holds, which this screen can't see.
                    setSelectionLimit(MAX_SELECTION.toLong())
                }
                val controller = PHPickerViewController(configuration).apply {
                    setDelegate(galleryDelegate)
                }
                topViewController()?.presentViewController(controller, animated = true, completion = null)
            }
        }
    }
}

private const val MAX_SELECTION = 6

private class GalleryPickerDelegate(
    private val scope: CoroutineScope
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    var onPhotos: (List<CapturedPhoto>) -> Unit = {}

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty()) return

        // Each item loads asynchronously and the handlers come back on
        // arbitrary queues. Collecting them in a coroutine keeps the ordering
        // the user chose and delivers one batch, rather than a callback per
        // photo racing the Firestore writes against each other.
        scope.launch {
            val photos = results.mapNotNull { result ->
                result.itemProvider().loadImageBytes()?.let { bytes ->
                    CapturedPhoto(
                        id = NSUUID().UUIDString(),
                        name = "page_${NSUUID().UUIDString().take(8)}.jpg",
                        mimeType = JPEG_TYPE,
                        bytes = bytes,
                        size = bytes.size.toLong()
                    )
                }
            }
            if (photos.isNotEmpty()) onPhotos(photos)
        }
    }
}

private class CameraPickerDelegate :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    var onPhotos: (List<CapturedPhoto>) -> Unit = {}

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        picker.dismissViewControllerAnimated(true, null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        val bytes = image.toJpegBytes() ?: return
        onPhotos(
            listOf(
                CapturedPhoto(
                    id = NSUUID().UUIDString(),
                    name = "capture_${NSUUID().UUIDString().take(8)}.jpg",
                    mimeType = JPEG_TYPE,
                    bytes = bytes,
                    size = bytes.size.toLong()
                )
            )
        )
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
    }
}

/**
 * Loads the item and re-encodes it as a downscaled JPEG.
 *
 * The library hands back whatever the camera wrote, which on any recent iPhone
 * is HEIC — a format `storage.rules` rejects and Pillow on the function side
 * can't open without an extra dependency. Re-encoding removes that whole class
 * of problem at the point where the format is still known.
 */
private suspend fun NSItemProvider.loadImageBytes(): ByteArray? =
    suspendCancellableCoroutine { continuation ->
        // "public.image" is the UTI every still-image format conforms to.
        loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            continuation.resume(data?.let { UIImage.imageWithData(it)?.toJpegBytes() })
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toJpegBytes(): ByteArray? {
    val scaled = downscaled(MAX_LONG_EDGE)
    val jpeg: NSData = UIImageJPEGRepresentation(scaled, JPEG_QUALITY) ?: return null
    return jpeg.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.downscaled(maxLongEdge: Double): UIImage {
    val width = size.useContents { width }
    val height = size.useContents { height }
    val longEdge = maxOf(width, height)
    if (longEdge <= maxLongEdge || longEdge <= 0.0) return this

    val ratio = maxLongEdge / longEdge
    val targetWidth = width * ratio
    val targetHeight = height * ratio

    // scale = 1.0, so the pixel dimensions are exactly the ones asked for
    // rather than multiplied by the device's scale factor.
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), true, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    val resized = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return resized ?: this
}

/**
 * The controller a modal should be presented from.
 *
 * Compose Multiplatform runs in a single UIWindow, so the key window's root is
 * the right starting point; the walk up the presentation chain matters because
 * presenting from a controller that is already covered silently does nothing.
 */
private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}
