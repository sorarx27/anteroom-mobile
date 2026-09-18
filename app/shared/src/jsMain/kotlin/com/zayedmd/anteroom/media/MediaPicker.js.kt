package com.zayedmd.anteroom.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * Browser file picker.
 *
 * Replaces a stub that fabricated a `CapturedPhoto` with no bytes. A hidden
 * `<input type="file">` is the only way to open the OS file dialog from the
 * web: it has to be clicked from a real user gesture, which the Compose
 * button click provides.
 *
 * `capture="environment"` on the camera path is what makes a phone browser
 * open the camera straight away; on desktop browsers it is ignored and the
 * normal chooser appears.
 */
@Composable
actual fun rememberMediaPicker(
    onPhotosPicked: (List<CapturedPhoto>) -> Unit,
    onPermissionDenied: ((PermissionResult) -> Unit)?
): MediaPicker {
    val currentOnPicked by rememberUpdatedState(onPhotosPicked)

    return remember {
        object : MediaPicker {
            override fun launchCamera() = openPicker(useCamera = true) { currentOnPicked(it) }
            override fun launchGallery() = openPicker(useCamera = false) { currentOnPicked(it) }
        }
    }
}

private fun openPicker(useCamera: Boolean, onPicked: (List<CapturedPhoto>) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = "image/jpeg,image/png,image/webp"
    input.multiple = !useCamera
    if (useCamera) input.setAttribute("capture", "environment")
    input.style.display = "none"

    input.onchange = {
        val files = input.files
        val count = files?.length ?: 0
        val collected = mutableListOf<CapturedPhoto>()
        var remaining = count

        if (count == 0) {
            input.remove()
        } else {
            for (index in 0 until count) {
                val file = files!![index]
                if (file == null) {
                    remaining -= 1
                    continue
                }
                readFile(file) { photo ->
                    if (photo != null) collected.add(photo)
                    remaining -= 1
                    // FileReader is asynchronous and the files resolve in any
                    // order, so deliver one batch when the last one lands
                    // rather than a callback per file racing the uploads.
                    if (remaining == 0) {
                        input.remove()
                        if (collected.isNotEmpty()) onPicked(collected)
                    }
                }
            }
        }
        Unit
    }

    document.body?.appendChild(input)
    input.click()
}

private fun readFile(file: File, onDone: (CapturedPhoto?) -> Unit) {
    val reader = FileReader()
    reader.onload = {
        val buffer = reader.result as? ArrayBuffer
        val bytes = buffer?.let { Int8Array(it).unsafeCast<ByteArray>() }
        onDone(
            if (bytes == null || bytes.isEmpty()) null
            else CapturedPhoto(
                id = randomId(),
                name = file.name,
                mimeType = file.type.ifBlank { "image/jpeg" },
                bytes = bytes,
                size = bytes.size.toLong()
            )
        )
        Unit
    }
    reader.onerror = { onDone(null); Unit }
    reader.readAsArrayBuffer(file)
}

/** `crypto.randomUUID()` is not available on every browser we care about. */
private fun randomId(): String =
    (1..32).joinToString("") { ((0..15).random()).toString(16) }
