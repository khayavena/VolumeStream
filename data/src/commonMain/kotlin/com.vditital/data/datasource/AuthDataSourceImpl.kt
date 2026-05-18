package com.vditital.data.datasource

import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.model.AuthResponse
import com.vditital.data.model.LoginRequest
import com.vditital.data.model.RefreshTokenRequest
import com.vditital.data.model.RefreshTokenResponse
import com.vditital.data.model.RegisterRequest
import com.vditital.data.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.path

class AuthDataSourceImpl(
    private val httpClient: HttpClient,
    private val authHost: String,
    private val config: StreamVaultConfig = StreamVaultConfig()
) : AuthDataSource {

    private val protocol: URLProtocol
        get() = when {
            authHost.startsWith("https://", ignoreCase = true) -> URLProtocol.HTTPS
            authHost.startsWith("http://", ignoreCase = true) -> URLProtocol.HTTP
            config.useHttps -> URLProtocol.HTTPS
            else -> URLProtocol.HTTP
        }

    private val normalizedAuthority: String
        get() = authHost
            .trim()
            .removeSuffix("/")
            .substringAfter("://", missingDelimiterValue = authHost.trim().removeSuffix("/"))
            .substringBefore("/")

    private val host: String
        get() = normalizedAuthority.substringBefore(":")

    private val port: Int
        get() = normalizedAuthority.substringAfterLast(":", "")
            .toIntOrNull()
            ?: config.authPort

    private val base     get() = config.authBasePath

    override suspend fun login(email: String, password: String): AuthResponse {
        AppLogger.d("AuthDS", "login → $authHost:$port")
        val response = httpClient.post {
            url {
                protocol = this@AuthDataSourceImpl.protocol
                host     = this@AuthDataSourceImpl.host
                port     = this@AuthDataSourceImpl.port
                path(base, "login")
            }
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }

        if (response.status.value !in 200..299) {
            val body = runCatching { response.body<String>() }.getOrDefault("")
            val msg = "HTTP ${response.status.value} ${response.status.description}" +
                    (if (body.isNotBlank()) ": $body" else "")
            AppLogger.e("AuthDS", "login failed — $msg")
            throw IllegalStateException(msg)
        }

        return try {
            response.body()
        } catch (e: Exception) {
            val responseText = runCatching { response.body<String>() }.getOrDefault("")
            AppLogger.e("AuthDS", "FAILED TO SERIALIZE LOGIN RESPONSE: $responseText", e)
            throw e
        }
    }

    override suspend fun register(email: String, password: String): Unit {
        AppLogger.d("AuthDS", "register → $authHost:$port")
        val response = httpClient.post {
            url {
                protocol = this@AuthDataSourceImpl.protocol
                host     = this@AuthDataSourceImpl.host
                port     = this@AuthDataSourceImpl.port
                path(base, "register")
            }
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password))
        }

        if (response.status.value !in 200..299) {
            val body = runCatching { response.body<String>() }.getOrDefault("")
            val msg = "HTTP ${response.status.value} ${response.status.description}" +
                    (if (body.isNotBlank()) ": $body" else "")
            AppLogger.e("AuthDS", "register failed — $msg")
            throw IllegalStateException(msg)
        }

        // Backend returns plain text (e.g. "Registered") on success; status is enough here.
    }

    override suspend fun refreshToken(jwt: String): RefreshTokenResponse {
        // Server expects POST /api/refresh with body {"token":"Bearer <jwt>"}
        AppLogger.d("AuthDS", "refreshToken → $authHost:$port")
        val response = httpClient.post {
            url {
                protocol = this@AuthDataSourceImpl.protocol
                host     = this@AuthDataSourceImpl.host
                port     = this@AuthDataSourceImpl.port
                path(base, "refresh")
            }
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest("Bearer $jwt"))
        }

        if (response.status.value !in 200..299) {
            val body = runCatching { response.body<String>() }.getOrDefault("")
            val msg = "HTTP ${response.status.value} ${response.status.description}" +
                    (if (body.isNotBlank()) ": $body" else "")
            AppLogger.e("AuthDS", "refreshToken failed — $msg")
            throw IllegalStateException(msg)
        }

        return response.body()
    }
}
