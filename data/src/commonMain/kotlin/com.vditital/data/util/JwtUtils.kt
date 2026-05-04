package com.vditital.data.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Decode the middle segment of a JWT and return the userId claim. */
@OptIn(ExperimentalEncodingApi::class)
fun extractUserIdFromJwt(token: String): String {
    return try {
        val payloadB64 = token.split(".").getOrNull(1) ?: return ""
        // Pad to a multiple of 4, convert URL-safe chars to standard Base64
        val padded = payloadB64.padEnd((payloadB64.length + 3) / 4 * 4, '=')
            .replace('-', '+').replace('_', '/')
        val decoded = Base64.decode(padded).decodeToString()
        Regex(""""userId"\s*:\s*"([^"]+)"""").find(decoded)?.groupValues?.get(1)
            ?: Regex(""""id"\s*:\s*"([^"]+)"""").find(decoded)?.groupValues?.get(1)
            ?: Regex(""""sub"\s*:\s*"([^"]+)"""").find(decoded)?.groupValues?.get(1)
            ?: ""
    } catch (_: Exception) {
        ""
    }
}

/** Returns true if the JWT's "exp" claim is in the past (expired). */
@OptIn(ExperimentalEncodingApi::class)
fun isJwtExpired(token: String): Boolean {
    return try {
        val payloadB64 = token.split(".").getOrNull(1) ?: return true
        val padded = payloadB64.padEnd((payloadB64.length + 3) / 4 * 4, '=')
            .replace('-', '+').replace('_', '/')
        val decoded = Base64.decode(padded).decodeToString()
        val expMatch = Regex(""""exp"\s*:\s*(\d+)""").find(decoded) ?: return false
        val exp = expMatch.groupValues[1].toLongOrNull() ?: return false
        exp < (currentEpochMillis() / 1000L)
    } catch (_: Exception) {
        // Treat an undecodable / malformed JWT as expired so the caller will
        // attempt a refresh (and ultimately clear the token if refresh fails).
        true
    }
}
