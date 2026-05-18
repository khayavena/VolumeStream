package com.vditital.data.datasource

import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.model.CreateProfileRequest
import com.vditital.data.model.UserProfile
import com.vditital.data.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.path

class ProfileDataSourceImpl(
    private val httpClient: HttpClient,
    private val authHost: String,
    private val config: StreamVaultConfig = StreamVaultConfig(),
) : ProfileDataSource {

    private val protocol: URLProtocol
        get() = when {
            authHost.startsWith("https://", ignoreCase = true) -> URLProtocol.HTTPS
            authHost.startsWith("http://", ignoreCase = true) -> URLProtocol.HTTP
            config.authUseHttps -> URLProtocol.HTTPS
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

    override suspend fun createProfile(jwt: String, request: CreateProfileRequest): UserProfile {
        AppLogger.d("ProfileDS", "createProfile -> ${protocol.name.lowercase()}://$host:$port/profile/create")
        val response = httpClient.post {
            url {
                protocol = this@ProfileDataSourceImpl.protocol
                host = this@ProfileDataSourceImpl.host
                port = this@ProfileDataSourceImpl.port
                path("profile", "create")
            }
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response.status, "createProfile")
        return response.body()
    }

    override suspend fun viewProfileById(jwt: String, profileId: String): UserProfile {
        val response = httpClient.get {
            url {
                protocol = this@ProfileDataSourceImpl.protocol
                host = this@ProfileDataSourceImpl.host
                port = this@ProfileDataSourceImpl.port
                path("profile", "view")
            }
            bearerAuth(jwt)
            header("id", profileId)
        }
        ensureSuccess(response.status, "viewProfileById")
        return response.body()
    }

    override suspend fun viewProfileByUserId(jwt: String, userId: String): UserProfile {
        val response = httpClient.get {
            url {
                protocol = this@ProfileDataSourceImpl.protocol
                host = this@ProfileDataSourceImpl.host
                port = this@ProfileDataSourceImpl.port
                path("profile", "user")
            }
            bearerAuth(jwt)
            header("userId", userId)
        }
        ensureSuccess(response.status, "viewProfileByUserId")
        return response.body()
    }

    override suspend fun updateProfile(jwt: String, profile: UserProfile): UserProfile {
        val response = httpClient.put {
            url {
                protocol = this@ProfileDataSourceImpl.protocol
                host = this@ProfileDataSourceImpl.host
                port = this@ProfileDataSourceImpl.port
                path("profile", "update")
            }
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody(profile)
        }
        ensureSuccess(response.status, "updateProfile")
        return response.body()
    }

    override suspend fun upgradeAdmin(jwt: String): String {
        val response = httpClient.post {
            url {
                protocol = this@ProfileDataSourceImpl.protocol
                host = this@ProfileDataSourceImpl.host
                port = this@ProfileDataSourceImpl.port
                path("profile", "upgrade-admin")
            }
            bearerAuth(jwt)
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }
        ensureSuccess(response.status, "upgradeAdmin")
        return runCatching { response.body<String>() }
            .getOrElse { "Upgraded to admin" }
    }

    private fun ensureSuccess(status: HttpStatusCode, op: String) {
        if (status.value in 200..299) return
        val message = "HTTP ${status.value} ${status.description}"
        if (status == HttpStatusCode.NotFound) {
            throw NoSuchElementException(message)
        }
        AppLogger.e("ProfileDS", "$op failed - $message")
        throw IllegalStateException(message)
    }
}


