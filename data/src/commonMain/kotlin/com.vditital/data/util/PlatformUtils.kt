package com.vditital.data.util

/** Platform-specific current epoch time in milliseconds. */
expect fun currentEpochMillis(): Long

/**
 * Returns a non-null reason when the device appears rooted/jailbroken/tampered.
 * Null means no tamper signal was detected.
 */
expect fun deviceTamperReason(): String?
