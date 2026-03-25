package com.vditital.data.util

import platform.Foundation.*

actual fun currentEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
