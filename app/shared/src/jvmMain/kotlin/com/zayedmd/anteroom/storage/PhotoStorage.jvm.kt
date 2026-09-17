package com.zayedmd.anteroom.storage

import dev.gitlive.firebase.storage.Data

/**
 * GitLive 2.1.0 has no JVM implementation of Storage — every member of the
 * JVM `Data`/`StorageReference` actual is `TODO()`. Callers must gate on
 * `FirebaseService.storageSupported`; this exists so the target compiles and
 * so a missed gate fails with a sentence instead of `NotImplementedError`.
 */
actual fun storageData(bytes: ByteArray): Data =
    throw UnsupportedOperationException(
        "Photo upload isn't available on Desktop yet. Use the Android or iOS app to add pages."
    )
