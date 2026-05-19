package com.vditital.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String
)

@Serializable
data class RegisterRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    // Null means "do not send role"; backend defaults to ROLE_USER for public registration.
    @SerialName("role") val role: String? = null
)

/** Body for POST /api/refresh — server expects {"token":"Bearer <jwt>"} */
@Serializable
data class RefreshTokenRequest(
    @SerialName("token") val token: String
)

@Serializable
data class AuthResponse(
    @SerialName("token") val tokenFallback: String = "", 
    @SerialName("jwtToken") val jwtTokenFallback: String = "",
    @SerialName("accessToken") val accessTokenFallback: String = ""
) {
    val token: String 
        get() = tokenFallback.takeIf { it.isNotEmpty() } 
            ?: jwtTokenFallback.takeIf { it.isNotEmpty() } 
            ?: accessTokenFallback.takeIf { it.isNotEmpty() } 
            ?: ""
}

@Serializable
data class RefreshTokenResponse(
    @SerialName("token") val tokenFallback: String = "", 
    @SerialName("jwtToken") val jwtTokenFallback: String = "",
    @SerialName("accessToken") val accessTokenFallback: String = "",
    @SerialName("refreshTime") val refreshTime: Long = 0L
) {
    val token: String 
        get() = tokenFallback.takeIf { it.isNotEmpty() } 
            ?: jwtTokenFallback.takeIf { it.isNotEmpty() } 
            ?: accessTokenFallback.takeIf { it.isNotEmpty() } 
            ?: ""
}

@Serializable
data class DeviceRegisterRequest(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("publicKeyB64") val publicKeyB64: String
)

@Serializable
data class SessionStartRequest(
    @SerialName("videoId") val videoId: String,
    @SerialName("offlinePlayback") val offlinePlayback: Boolean? = null,
    @SerialName("offlineLicenseSeconds") val offlineLicenseSeconds: Long? = null
)

@Serializable
data class SessionStartResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("nonce") val nonce: String,
    @SerialName("sessionToken") val sessionToken: String,
    @SerialName("expiresAt") val expiresAt: Long,
    @SerialName("offlinePlayback") val offlinePlayback: Boolean = false,
    @SerialName("offlineLicenseExpiresAt") val offlineLicenseExpiresAt: Long = 0L,
    @SerialName("offlineSessionToken") val offlineSessionToken: String? = null
)

@Serializable
data class RecentlyWatchedRequest(
    @SerialName("userId") val userId: String,
    @SerialName("mediaId") val mediaId: String,
    @SerialName("playbackPosition") val playbackPosition: Long
)

@Serializable
data class ApiError(
    @SerialName("status") val status: Int = 0,
    @SerialName("code") val code: String = "",
    @SerialName("message") val message: String = ""
)
