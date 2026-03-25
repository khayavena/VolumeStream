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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.path

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
        val response = httpClient.post {
            url {
                protocol = this@SessionDataSourceImpl.protocol
                host     = apiHost
                port     = this@SessionDataSourceImpl.port
                path("$base/device/register")
            }
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody(DeviceRegisterRequest(deviceId, publicKeyB64))
        }
        val ok = response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK || response.status.value == 409
        AppLogger.d("SessionDS", "registerDevice status=${response.status} ok=$ok")
        return ok
    }

    override suspend fun startSession(jwt: String, videoId: String): SessionStartResponse {
        val userId        = extractUserIdFromJwt(jwt)
        val deviceId      = tokenStore.getDeviceId()
        val certTimestamp = currentEpochMillis()
        val payload       = "$userId|$videoId|$certTimestamp"
        val certSignature = deviceCrypto.signPayload(payload)

        val sessionUrl = "${protocol.name.lowercase()}://$apiHost:$port/$base/session/start"
        AppLogger.i("SessionDS", "startSession ──────────────────────────────────────")
        AppLogger.i("SessionDS", "  POST $sessionUrl")
        AppLogger.i("SessionDS", "  ${config.headerDeviceId}: $deviceId")
        AppLogger.i("SessionDS", "  ${config.headerCertTimestamp}: $certTimestamp")
        AppLogger.i("SessionDS", "  ${config.headerCertSignature}: ${certSignature.take(20)}…")
        AppLogger.i("SessionDS", "  videoId=$videoId  userId=$userId")
        AppLogger.i("SessionDS", "────────────────────────────────────────────────────")

        return httpClient.post {
            url {
                protocol = this@SessionDataSourceImpl.protocol
                host     = apiHost
                port     = this@SessionDataSourceImpl.port
                path("$base/session/start")
            }
            bearerAuth(jwt)
            header(config.headerDeviceId,        deviceId)
            header(config.headerCertTimestamp,   certTimestamp.toString())
            header(config.headerCertSignature,   certSignature)
            contentType(ContentType.Application.Json)
            setBody(SessionStartRequest(videoId))
        }.body()
    }

    override suspend fun endSession(jwt: String, sessionId: String) {
        AppLogger.d("SessionDS", "endSession sessionId=$sessionId")
        runCatching {
            httpClient.delete {
                url {
                    protocol = this@SessionDataSourceImpl.protocol
                    host     = apiHost
                    port     = this@SessionDataSourceImpl.port
                    path("$base/session/$sessionId")
                }
                bearerAuth(jwt)
            }
        }
    }
}
