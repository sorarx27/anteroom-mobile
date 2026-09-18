package com.zayedmd.anteroom.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

@Composable
actual fun rememberPdfSharer(): PdfSharer = remember {
    PdfSharer { pdf ->
        val signed = pdf.bytes.unsafeCast<Int8Array>()
        val view = Uint8Array(signed.buffer, signed.byteOffset, signed.length)
        val blob = Blob(arrayOf(view), BlobPropertyBag(type = "application/pdf"))
        val url = URL.createObjectURL(blob)
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = pdf.filename
        anchor.click()
        // Without this the blob is pinned for the lifetime of the document —
        // and this blob is a patient's medication list.
        URL.revokeObjectURL(url)
    }
}
