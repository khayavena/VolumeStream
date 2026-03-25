package com.vditital.data.datasource

import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.model.AuthResponse
import com.vditital.data.model.LoginRequest
import com.vditital.data.model.RefreshTokenResponse
import com.vditital.data.model.RegisterRequest
import com.vditital.data.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
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

    private val protocol get() = if (config.useHttps) URLProtocol.HTTPS else URLProtocol.HTTP
    private val port     get() = config.authPort
    private val base     get() = config.authBasePath

    override suspend fun login(email: String, password: String): AuthResponse {
        AppLogger.d("AuthDS", "login → $authHost:$port")
        return httpClient.post {
            url {
                protocol = this@AuthDataSourceImpl.protocol
                host     = authHost
                port     = this@AuthDataSourceImpl.port
                path("$base/login")
            }
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.body()
    }

    override suspend fun register(email: String, password: String, name: String): AuthResponse {
        AppLogger.d("AuthDS", "register → $authHost:$port")
        return httpClient.post {
            url {
                protocol = this@AuthDataSourceImpl.protocol
                host     = authHost
                port     = this@AuthDataSourceImpl.port
                path("$base/register")
            }
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, password, name))
        }.body()
    }

    override suspend fun refreshToken(jwt: String): RefreshTokenResponse {
        return httpClient.post {
            url {
                protocol = this@AuthDataSourceImpl.protocol
                host     = authHost
                port     = this@AuthDataSourceImpl.port
                path("$base/refresh")
            }
            bearerAuth(jwt)
        }.body()
    }
}
