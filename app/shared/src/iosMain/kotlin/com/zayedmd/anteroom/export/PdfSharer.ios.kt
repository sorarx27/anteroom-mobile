package com.zayedmd.anteroom.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.zayedmd.anteroom.platform.toNSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberPdfSharer(): PdfSharer = remember { PdfSharer { pdf -> presentShareSheet(pdf) } }

private fun presentShareSheet(pdf: BriefPdf) {
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + pdf.filename)
    // A file URL rather than raw NSData: the share sheet then offers Files,
    // Mail and AirDrop with the right filename and a PDF preview, instead of
    // treating it as an anonymous blob.
    if (!pdf.bytes.toNSData().writeToURL(url, atomically = true)) return

    val controller = UIActivityViewController(listOf(url), null)
    val presenter = topViewController() ?: return
    // iPad presents this as a popover and requires an anchor; without one it
    // throws rather than falling back to a sheet.
    controller.popoverPresentationController?.sourceView = presenter.view
    presenter.presentViewController(controller, animated = true, completion = null)
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}
