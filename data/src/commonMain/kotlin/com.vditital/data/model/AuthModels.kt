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
    @SerialName("name") val name: String = ""
)

@Serializable
data class AuthResponse(
    @SerialName("jwtToken") val token: String
)

@Serializable
data class RefreshTokenResponse(
    @SerialName("jwtToken") val token: String,
    @SerialName("refreshTime") val refreshTime: Long = 0L
)

@Serializable
data class DeviceRegisterRequest(
    @SerialName("deviceId") val deviceId: String,
    @SerialName("publicKeyB64") val publicKeyB64: String
)

@Serializable
data class SessionStartRequest(
    @SerialName("videoId") val videoId: String
)

@Serializable
data class SessionStartResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("nonce") val nonce: String,
    @SerialName("sessionToken") val sessionToken: String,
    @SerialName("expiresAt") val expiresAt: Long
)

@Serializable
data class ApiError(
    @SerialName("status") val status: Int = 0,
    @SerialName("code") val code: String = "",
    @SerialName("message") val message: String = ""
)

