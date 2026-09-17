package com.zayedmd.anteroom.storage

import dev.gitlive.firebase.storage.Data
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/**
 * Kotlin/JS compiles `ByteArray` to `Int8Array`, so this is a view over the
 * same buffer rather than a copy — which matters for multi-megabyte pages.
 */
actual fun storageData(bytes: ByteArray): Data {
    val signed = bytes.unsafeCast<Int8Array>()
    return Data(Uint8Array(signed.buffer, signed.byteOffset, signed.length))
}
