package com.vditital.data.repository

import com.vditital.data.datasource.AuthDataSource
import com.vditital.data.repository.state.ResultState
import com.vditital.data.security.TokenStore
import com.vditital.data.util.AppLogger
import com.vditital.data.util.deviceTamperReason
import com.vditital.data.util.isJwtExpired

class AuthRepositoryImpl(
    private val authDataSource: AuthDataSource,
    private val tokenStore: TokenStore
) : AuthRepository {

    override suspend fun login(email: String, password: String): ResultState<Unit> = runCatching {
        val tamperReason = deviceTamperReason()
        if (tamperReason != null) {
            throw IllegalStateException(
                "Login blocked: this device appears tampered. Please use a secure device."
            )
        }
        AppLogger.d("AuthRepo", "login email=$email")
        val response = authDataSource.login(email, password)
        val rawToken = response.token.removePrefix("Bearer ").trim()
        tokenStore.setJwt(rawToken)
        tokenStore.setUserEmail(email)
    }.fold(
        onSuccess = { ResultState.Success(Unit) },
        onFailure = { ResultState.Error(it) }
    )

    override suspend fun register(email: String, password: String): ResultState<Unit> = runCatching {
        val tamperReason = deviceTamperReason()
        if (tamperReason != null) {
            throw IllegalStateException(
                "Registration blocked: this device appears tampered. Please use a secure device."
            )
        }
        AppLogger.d("AuthRepo", "register email=$email")
        authDataSource.register(email, password)
        val loginResponse = authDataSource.login(email, password)
        val rawToken = loginResponse.token.removePrefix("Bearer ").trim()
        tokenStore.setJwt(rawToken)
        tokenStore.setUserEmail(email)
    }.fold(
        onSuccess = { ResultState.Success(Unit) },
        onFailure = { ResultState.Error(it) }
    )

    override fun isLoggedIn(): Boolean =
        tokenStore.getJwt().isNullOrBlank().not()

    override fun getCurrentUserEmail(): String? = tokenStore.getUserEmail()

    override fun logout() {
        tokenStore.clearAll()
    }

    override suspend fun ensureValidJwt(): String? {
        val current = tokenStore.getJwt()?.takeIf { it.isNotBlank() } ?: return null
        if (!isJwtExpired(current)) return current

        AppLogger.d("AuthRepo", "JWT expired; attempting refresh")
        return forceRefreshJwt()
    }

    override suspend fun forceRefreshJwt(): String? {
        val current = tokenStore.getJwt()?.takeIf { it.isNotBlank() } ?: return null

        AppLogger.d("AuthRepo", "Forcing JWT refresh")
        return runCatching {
            val refreshed = authDataSource.refreshToken(current)
                .token
                .removePrefix("Bearer ")
                .trim()
            require(refreshed.isNotBlank()) { "Refresh endpoint returned an empty token" }
            tokenStore.setJwt(refreshed)
            refreshed
        }.getOrElse { e ->
            AppLogger.e("AuthRepo", "JWT refresh failed; clearing stored JWT", e as? Exception ?: Exception(e))
            tokenStore.clearJwt()
            null
        }
    }
}
