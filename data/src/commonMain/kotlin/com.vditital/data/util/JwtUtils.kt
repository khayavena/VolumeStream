package com.vditital.data.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Decode the middle segment of a JWT and return the userId claim. */
@OptIn(ExperimentalEncodingApi::class)
fun extractUserIdFromJwt(token: String): String {
    val payload = parseJwtPayload(token) ?: return ""
    return payload.primitiveText("userId")
        ?: payload.primitiveText("id")
        ?: payload.primitiveText("sub")
        ?: ""
}

/** Returns true if the JWT's "exp" claim is in the past (expired). */
@OptIn(ExperimentalEncodingApi::class)
fun isJwtExpired(token: String): Boolean {
    val payload = parseJwtPayload(token) ?: return true
    val exp = payload.primitiveText("exp")?.toLongOrNull() ?: return false
    return exp < (currentEpochMillis() / 1000L)
}

@OptIn(ExperimentalEncodingApi::class)
private fun parseJwtPayload(token: String): JsonObject? {
    return try {
        val payloadB64 = token.split(".").getOrNull(1) ?: return null
        val padded = payloadB64.padEnd((payloadB64.length + 3) / 4 * 4, '=')
            .replace('-', '+')
            .replace('_', '/')
        val decoded = Base64.decode(padded).decodeToString()
        Json.parseToJsonElement(decoded).jsonObject
    } catch (_: Exception) {
        null
    }
}

private fun JsonObject.primitiveText(key: String): String? =
    this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

