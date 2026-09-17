package com.zayedmd.anteroom.storage

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class)
actual fun storageData(bytes: ByteArray): Data = Data(bytes.toNSData())

/**
 * `dataWithBytes:length:` copies, so the pinned region is only needed for the
 * duration of the call. The empty case is special-cased because `addressOf(0)`
 * on an empty array is out of bounds.
 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData()
    } else {
        usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
