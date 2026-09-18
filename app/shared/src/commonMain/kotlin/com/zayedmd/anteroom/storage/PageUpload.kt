package com.zayedmd.anteroom.storage

/**
 * Uploads one page image and returns a URL the client can render.
 *
 * Exists because Storage is the one Firebase product GitLive 2.1.0 does not
 * implement for the JVM -- every member of the JVM actual is `TODO()`. Rather
 * than leave Desktop unable to add a page at all, this is the seam: Android,
 * Apple and the web go through the GitLive SDK, and Desktop talks to the
 * Storage REST API directly with the signed-in user's ID token. Both paths
 * are subject to the same `storage.rules`.
 */
expect suspend fun uploadPage(
    path: String,
    bytes: ByteArray,
    contentType: String
): String
