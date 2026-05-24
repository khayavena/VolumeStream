package com.vditital.data.datasource

import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.model.RefreshTokenRequest
import com.vditital.data.model.RefreshTokenResponse
import com.vditital.data.security.SessionRevokedBus
import com.vditital.data.security.TokenStore
import com.vditital.data.util.AppLogger
import com.vditital.data.util.isJwtExpired
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path

class AuthRefreshRetryInterceptor(
    private val tokenStore: TokenStore,
    authHost: String,
    private val config: StreamVaultConfig,
) {

    private companion object {
        const val LOG_TAG = "AuthRetry"
        const val RETRY_HEADER = "X-Auth-Retry"
        const val SKIP_REFRESH_HEADER = "X-Skip-Refresh"
        const val BEARER_PREFIX = "Bearer "
    }

    private val cleanedAuthHost: String = authHost.trim().removeSuffix("/")

    private val authProtocol: URLProtocol = if (config.authUseHttps) URLProtocol.HTTPS else URLProtocol.HTTP

    private val normalizedAuthAuthority: String = cleanedAuthHost
        .substringAfter("://", missingDelimiterValue = cleanedAuthHost)
        .substringBefore("/")

    private val authHostName: String = normalizedAuthAuthority.substringBefore(":")
    private val authPort: Int = normalizedAuthAuthority.substringAfterLast(":", "").toIntOrNull() ?: config.authPort

    suspend fun intercept(
        request: HttpRequestBuilder,
        execute: suspend (HttpRequestBuilder) -> HttpClientCall,
    ): HttpClientCall {
        val requestUrl = request.url.toString()
        val call = execute(request)
        val hasJwt = request.headers[HttpHeaders.Authorization]?.startsWith(BEARER_PREFIX) == true

        AppLogger.d(
            LOG_TAG,
            "intercept status=${call.response.status.value} method=${request.method.value} url=$requestUrl hasJwt=$hasJwt retry=${isRetryRequest(request)} refresh=${isRefreshRequest(request)}",
        )

        if (!hasJwt || isRetryRequest(request) || isRefreshRequest(request)) {
            return call
        }

        val currentJwt = extractBearerToken(request.headers[HttpHeaders.Authorization])
            ?: tokenStore.getJwt()?.takeIf { it.isNotBlank() }

        if (!isRefreshableAuthFailure(call, currentJwt)) {
            AppLogger.d(LOG_TAG, "no refresh needed status=${call.response.status.value} url=$requestUrl")
            return call
        }

        val refreshedJwt = currentJwt?.let { refreshToken(it, execute) }
        if (refreshedJwt.isNullOrBlank()) {
            AppLogger.w(LOG_TAG, "refresh failed status=${call.response.status.value}; signalling session revoked")
            SessionRevokedBus.emit()
            return call
        }

        tokenStore.setJwt(refreshedJwt)

        val retryRequest = HttpRequestBuilder().apply {
            takeFrom(request)
            headers.remove(HttpHeaders.Authorization)
            headers.remove(RETRY_HEADER)
            bearerAuth(refreshedJwt)
            headers.append(RETRY_HEADER, "1")
        }

        val retryCall = execute(retryRequest)
        AppLogger.d(LOG_TAG, "retry status=${retryCall.response.status.value} url=$requestUrl")
        if (isRefreshableAuthFailure(retryCall, refreshedJwt)) {
            AppLogger.w(LOG_TAG, "Retry also returned ${retryCall.response.status.value}; signalling session revoked")
            SessionRevokedBus.emit()
        }
        return retryCall
    }

    suspend fun refreshToken(
        jwt: String,
        execute: suspend (HttpRequestBuilder) -> HttpClientCall,
    ): String? {
        val refreshRequest = HttpRequestBuilder().apply {
            method = HttpMethod.Post
            url {
                protocol = authProtocol
                host = authHostName
                port = authPort
                path(config.authBasePath, "refresh")
            }
            headers.append(SKIP_REFRESH_HEADER, "1")
            headers.append(HttpHeaders.ContentType, "application/json")
            setBody(RefreshTokenRequest("$BEARER_PREFIX$jwt"))
        }

        return runCatching {
            AppLogger.d(LOG_TAG, "calling refresh endpoint host=$authHostName port=$authPort")
            val refreshCall = execute(refreshRequest)
            AppLogger.d(LOG_TAG, "refresh response status=${refreshCall.response.status.value}")
            if (refreshCall.response.status.value !in 200..299) {
                null
            } else {
                refreshCall.body<RefreshTokenResponse>()
                    .token
                    .let(::extractBearerToken)
            }
        }.onFailure {
            AppLogger.w(LOG_TAG, "Refresh call failed: ${it.message}")
        }.getOrNull()
    }

    fun extractBearerToken(headerValue: String?): String? =
        headerValue
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

     fun isRefreshableAuthFailure(call: HttpClientCall, jwt: String?): Boolean {
        val status = call.response.status
        if (status == HttpStatusCode.Unauthorized) return true
        if (status != HttpStatusCode.Forbidden) return false

        // 403 should refresh only when the server indicates an invalid token.
        val wwwAuth = call.response.headers[HttpHeaders.WWWAuthenticate]?.lowercase().orEmpty()
        val errorCode = call.response.headers["X-Error-Code"]?.lowercase().orEmpty()
        val hasInvalidTokenHint =
            wwwAuth.contains("invalid_token") ||
            wwwAuth.contains("token_invalid") ||
            wwwAuth.contains("token_expired") ||
            errorCode.contains("token_invalid") ||
            errorCode.contains("token_expired")

        if (hasInvalidTokenHint) return true
        return jwt?.let(::isJwtExpired) == true
    }

     fun isRetryRequest(request: HttpRequestBuilder): Boolean =
        request.headers[RETRY_HEADER] == "1" || request.headers[SKIP_REFRESH_HEADER] == "1"

     fun isRefreshRequest(request: HttpRequestBuilder): Boolean =
        request.url.toString().contains("/${config.authBasePath.trim('/')}/refresh") ||
            request.url.toString().contains("/refresh")
}
