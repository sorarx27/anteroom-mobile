package com.zayedmd.anteroom.storage

import dev.gitlive.firebase.storage.Data

/**
 * Wraps raw bytes in the platform's Firebase Storage payload type.
 *
 * GitLive models the upload payload as an `expect class Data` with a
 * platform-specific constructor — `ByteArray` on Android, `NSData` on Apple,
 * `Uint8Array` on the web — and deliberately offers no common factory, so the
 * conversion has to live here.
 */
expect fun storageData(bytes: ByteArray): Data

/**
 * Object paths inside the Storage bucket.
 *
 * These strings are load-bearing in three places at once: `storage.rules`
 * matches on them, the Admin SDK in `generateBrief` reads bytes from them, and
 * the client writes them. They are built in one place so those three can't
 * drift apart.
 */
object StoragePaths {

    /** Mirrors `MAX_PAGES` in `functions/anteroom/config.py`. */
    const val MAX_PAGES = 6

    /** Mirrors the ceiling in `storage.rules`. */
    const val MAX_PHOTO_BYTES = 8L * 1024 * 1024

    /**
     * Content types we accept, mapped to the file extension used on the
     * object. Both halves are checked by the storage rule, so an entry added
     * here without the matching rule change fails at upload rather than
     * silently storing something the functions can't decode.
     */
    private val EXTENSIONS = mapOf(
        "image/jpeg" to "jpg",
        "image/jpg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp"
    )

    /** Normalised content type, or null if we don't accept it. */
    fun contentTypeOrNull(raw: String?): String? {
        val base = raw?.substringBefore(';')?.trim()?.lowercase() ?: return null
        if (base !in EXTENSIONS) return null
        // "image/jpg" is not a real IANA type; the rule regex only allows the
        // three canonical ones, so normalise before it reaches the server.
        return if (base == "image/jpg") "image/jpeg" else base
    }

    fun extensionFor(contentType: String): String = EXTENSIONS[contentType] ?: "jpg"

    /**
     * Path for one page image.
     *
     * The filename is `photo_<id>.<ext>`, and [photoId] is stripped to
     * alphanumerics because the storage rule pins the filename to
     * `photo_[A-Za-z0-9]+\.(jpg|png|webp)` — a raw UUID's hyphens would be
     * rejected with a bare `PERMISSION_DENIED` that says nothing about why.
     */
    fun pagePath(uid: String, briefId: String, photoId: String, contentType: String): String {
        val slug = photoId.filter { it.isLetterOrDigit() }.ifEmpty { "page" }
        return "users/$uid/briefs/$briefId/pages/photo_$slug.${extensionFor(contentType)}"
    }
}
