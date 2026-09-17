package com.zayedmd.anteroom.storage

import dev.gitlive.firebase.storage.Data

actual fun storageData(bytes: ByteArray): Data = Data(bytes)
