package com.zayedmd.anteroom.export

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberPdfSharer(): PdfSharer {
    val context = LocalContext.current
    return remember(context) { PdfSharer { pdf -> context.sharePdf(pdf) } }
}

/**
 * Writes the PDF to the app's cache and offers it through a share sheet.
 *
 * It goes through FileProvider rather than a `file://` Uri because passing a
 * raw file path to another app has thrown `FileUriExposedException` since
 * Android 7, and the receiving app is granted read access for this Intent
 * only — the brief is not left readable to everything on the device.
 */
private fun Context.sharePdf(pdf: BriefPdf) {
    val dir = File(cacheDir, "exports").apply { mkdirs() }
    // One file per brief, overwritten on re-export, so the cache does not grow
    // without bound with copies of medical documents.
    val file = File(dir, pdf.filename).apply { writeBytes(pdf.bytes) }

    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, pdf.filename)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(
        Intent.createChooser(intent, "Share brief").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
