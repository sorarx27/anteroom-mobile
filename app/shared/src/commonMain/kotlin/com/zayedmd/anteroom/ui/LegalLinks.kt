package com.zayedmd.anteroom.ui

/**
 * The two URLs App Review checks for.
 *
 * App Store Review Guideline 3.1.2 requires an auto-renewable subscription to
 * show, in the binary and next to the purchase, functional links to the terms
 * of use and the privacy policy — not in a settings screen, and not only in
 * the App Store listing. Missing links are one of the most common rejections
 * for subscription apps, and the check is mechanical.
 *
 * These point at Firebase Hosting on the app's own project rather than at the
 * source repository, which is private, and rather than at a domain we do not
 * yet own. `firebase deploy --only hosting` publishes `hosting/`.
 */
object LegalLinks {
    const val SITE = "https://anteroom-d2e72.web.app"
    const val TERMS = "$SITE/terms"
    const val PRIVACY = "$SITE/privacy"
    const val SUPPORT = "$SITE/support"
}
