package com.zayedmd.anteroom.export

import androidx.compose.runtime.Composable

/**
 * A rendered brief, exactly as `renderBriefPdf` produced it.
 *
 * [watermarked] is reported by the server, not chosen by the client. The old
 * design passed a watermark flag *into* the render call, which made the clean
 * export — one of the two things Pro sells — a boolean anyone could flip. It
 * is surfaced here only so the UI can say which one the user got.
 */
data class BriefPdf(
    val filename: String,
    val bytes: ByteArray,
    val watermarked: Boolean,
    val language: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as BriefPdf
        return filename == other.filename && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * filename.hashCode() + bytes.contentHashCode()
}

fun interface PdfSharer {
    /** Hands the PDF to the platform — a share sheet, a viewer, or a download. */
    fun share(pdf: BriefPdf)
}

@Composable
expect fun rememberPdfSharer(): PdfSharer
