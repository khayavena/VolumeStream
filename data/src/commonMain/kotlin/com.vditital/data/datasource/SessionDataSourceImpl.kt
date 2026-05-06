package com.vditital.data.datasource

import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.model.DeviceRegisterRequest
import com.vditital.data.model.SessionStartRequest
import com.vditital.data.model.SessionStartResponse
import com.vditital.data.security.DeviceCrypto
import com.vditital.data.security.TokenStore
import com.vditital.data.util.AppLogger
import com.vditital.data.util.currentEpochMillis
import com.vditital.data.util.extractUserIdFromJwt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType

class SessionDataSourceImpl(
    private val httpClient: HttpClient,
    private val apiHost: String,
    private val tokenStore: TokenStore,
    private val deviceCrypto: DeviceCrypto,
    private val config: StreamVaultConfig = StreamVaultConfig()
) : SessionDataSource {

    private val protocol get() = if (config.useHttps) URLProtocol.HTTPS else URLProtocol.HTTP
    private val port     get() = config.apiPort
    private val base     get() = config.apiBasePath

    override suspend fun registerDevice(jwt: String): Boolean {
        val deviceId     = tokenStore.getDeviceId()
        val publicKeyB64 = deviceCrypto.getOrCreatePublicKeyB64()
        AppLogger.d("SessionDS", "registerDevice deviceId=$deviceId")
        val response = httpClient.post("${protocol.name.lowercase()}://$apiHost:$port/$base/device/register") {
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody(DeviceRegisterRequest(deviceId, publicKeyB64))
        }
        val ok = response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK || response.status.value == 409
        AppLogger.d("SessionDS", "registerDevice status=${response.status} ok=$ok")
        return ok
    }

    override suspend fun startSession(jwt: String, videoId: String): SessionStartResponse {
        val userId   = extractUserIdFromJwt(jwt)
        check(userId.isNotBlank()) {
            "Unable to extract userId/sub/id claim from JWT; cannot build cert-pin payload"
        }
        val deviceId = tokenStore.getDeviceId()

        // Use server time to sign — guards against emulator/device clock drift
        // that would cause CERT_PIN_FAILED (stale timestamp) on the server.
        val certTimestamp = fetchServerEpochMillis()
        AppLogger.d("SessionDS", "certTimestamp=server:$certTimestamp  device:${currentEpochMillis()}  skew:${currentEpochMillis() - certTimestamp}ms")

        val payload       = "$userId|$videoId|$certTimestamp"
        val certSignature = deviceCrypto.signPayload(payload)
        check(certSignature.isNotBlank()) {
            "DeviceCrypto.signPayload returned empty — RSA signing failed on this device"
        }

        val sessionUrl = "${protocol.name.lowercase()}://$apiHost:$port/$base/session/start"
        AppLogger.i("SessionDS", "startSession ──────────────────────────────────────")
        AppLogger.i("SessionDS", "  POST $sessionUrl")
        AppLogger.i("SessionDS", "  ${config.headerDeviceId}: $deviceId")
        AppLogger.i("SessionDS", "  ${config.headerCertTimestamp}: $certTimestamp")
        AppLogger.i("SessionDS", "  ${config.headerCertSignature}: ${certSignature.take(20)}…")
        AppLogger.i("SessionDS", "  videoId=$videoId  userId=$userId")
        AppLogger.i("SessionDS", "────────────────────────────────────────────────────")

        return httpClient.post(sessionUrl) {
            bearerAuth(jwt)
            header(config.headerDeviceId,        deviceId)
            header(config.headerCertTimestamp,   certTimestamp.toString())
            header(config.headerCertSignature,   certSignature)
            contentType(ContentType.Application.Json)
            setBody(SessionStartRequest(videoId))
        }.body()
    }


    override suspend fun fetchAesKey(mediaId: String, sessionId: String, sessionToken: String): ByteArray {
        AppLogger.d("SessionDS", "fetchAesKey mediaId=$mediaId sid=$sessionId")
        val response = httpClient.get("${protocol.name.lowercase()}://$apiHost:$port/$base/manifest/$mediaId/key?sid=$sessionId&t=$sessionToken")
        val bytes = response.readRawBytes()
        check(bytes.size == 16) {
            "StreamVault key endpoint returned ${bytes.size} bytes; expected 16 (AES-128)"
        }
        AppLogger.d("SessionDS", "fetchAesKey OK — 16 bytes received")
        return bytes
    }

    /**
     * Fetches the server's current epoch millis from GET /api/v1/server/time (no auth required).
     * Falls back to the device clock if the request fails, so playback is not blocked
     * on a network error — though the server's skew window will still apply.
     */
    private suspend fun fetchServerEpochMillis(): Long {
        return try {
            val response = httpClient.get("${protocol.name.lowercase()}://$apiHost:$port/$base/server/time")
            val json = Json.parseToJsonElement(response.body<String>()).jsonObject
            json["epochMillis"]!!.jsonPrimitive.long
        } catch (e: Exception) {
            AppLogger.w("SessionDS", "fetchServerTime failed, falling back to device clock: ${e.message}")
            currentEpochMillis()
        }
    }
}
