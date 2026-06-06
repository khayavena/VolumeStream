package com.vditital.data.util

import android.os.Build
import java.io.File

actual fun currentEpochMillis(): Long = System.currentTimeMillis()

actual fun deviceTamperReason(): String? {
    if (Build.TAGS?.contains("test-keys", ignoreCase = true) == true) {
        return "Device appears rooted (test-keys build)"
    }

    val suspiciousPaths = listOf(
        "/system/app/Superuser.apk",
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su"
    )
    if (suspiciousPaths.any { File(it).exists() }) {
        return "Device appears rooted (su binary detected)"
    }

    return null
}
