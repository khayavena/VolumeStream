package com.vditital.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import platform.posix.F_OK
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.remove

actual fun currentEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

@OptIn(ExperimentalForeignApi::class)
actual fun deviceTamperReason(): String? {
    val suspiciousPaths = listOf(
        "/Applications/Cydia.app",
        "/Library/MobileSubstrate/MobileSubstrate.dylib",
        "/bin/bash",
        "/usr/sbin/sshd",
        "/etc/apt"
    )
    if (suspiciousPaths.any { access(it, F_OK) == 0 }) {
        return "Device appears jailbroken (restricted path found)"
    }

    // Writing to /private is not allowed on stock devices.
    val probePath = "/private/vs_jb_probe.txt"
    val handle = fopen(probePath, "w")
    if (handle != null) {
        fclose(handle)
        remove(probePath)
        return "Device appears jailbroken (sandbox escape probe succeeded)"
    }

    return null
}
