package com.zayedmd.anteroom.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.io.File

@Composable
actual fun rememberPdfSharer(): PdfSharer = remember {
    PdfSharer { pdf ->
        // Desktop has no share sheet; writing to Downloads and opening it in
        // the system viewer is the closest equivalent.
        val downloads = File(System.getProperty("user.home"), "Downloads")
            .takeIf { it.isDirectory } ?: File(System.getProperty("java.io.tmpdir"))
        val file = File(downloads, pdf.filename).apply { writeBytes(pdf.bytes) }
        runCatching {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
        }
    }
}
