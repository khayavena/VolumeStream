package com.vditital.data.repository

import com.vditital.data.datasource.AuthDataSource
import com.vditital.data.repository.state.ResultState
import com.vditital.data.security.TokenStore
import com.vditital.data.util.AppLogger
import com.vditital.data.util.isJwtExpired

class AuthRepositoryImpl(
    private val authDataSource: AuthDataSource,
    private val tokenStore: TokenStore
) : AuthRepository {

    override suspend fun login(email: String, password: String): ResultState<Unit> = runCatching {
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

    override fun isLoggedIn(): Boolean = tokenStore.getJwt() != null

    override fun getCurrentUserEmail(): String? = tokenStore.getUserEmail()

    override fun logout() {
        tokenStore.clearAll()
    }

    override suspend fun ensureValidJwt(): String? {
        val jwt = tokenStore.getJwt() ?: return null
        return if (isJwtExpired(jwt)) {
            AppLogger.d("AuthRepo", "JWT expired — refreshing")
            runCatching {
                val refreshed = authDataSource.refreshToken(jwt)
                val rawToken = refreshed.token.removePrefix("Bearer ").trim()
                tokenStore.setJwt(rawToken)
                rawToken
            }.onFailure {
                AppLogger.e("AuthRepo", "Token refresh failed", it as? Exception)
            }.getOrNull()
        } else {
            jwt
        }
    }
}

