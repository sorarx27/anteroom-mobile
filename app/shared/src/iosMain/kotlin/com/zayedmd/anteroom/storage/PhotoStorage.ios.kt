package com.zayedmd.anteroom.storage

import com.zayedmd.anteroom.platform.toNSData
import dev.gitlive.firebase.storage.Data

actual fun storageData(bytes: ByteArray): Data = Data(bytes.toNSData())
