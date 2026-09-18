package com.zayedmd.anteroom.storage

import com.zayedmd.anteroom.firebase.FirebaseService
import dev.gitlive.firebase.storage.FirebaseStorageMetadata

/**
 * Android, Apple and web: the SDK does the work.
 *
 * The content type is set explicitly. Without it the object lands as
 * application/octet-stream and the `contentType.matches('image/...')` check in
 * storage.rules rejects the write with a bare PERMISSION_DENIED that names
 * nothing.
 */
actual suspend fun uploadPage(
    path: String,
    bytes: ByteArray,
    contentType: String
): String {
    val ref = FirebaseService.storage.reference(path)
    ref.putData(storageData(bytes), FirebaseStorageMetadata(contentType = contentType))
    return ref.getDownloadUrl()
}
