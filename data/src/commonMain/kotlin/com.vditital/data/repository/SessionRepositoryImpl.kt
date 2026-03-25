package com.vditital.data.repository

import com.vditital.data.datasource.SessionDataSource
import com.vditital.data.model.SessionStartResponse
import com.vditital.data.repository.state.ResultState
import com.vditital.data.security.TokenStore
import com.vditital.data.util.AppLogger

class SessionRepositoryImpl(
    private val sessionDataSource: SessionDataSource,
    private val tokenStore: TokenStore
) : SessionRepository {

    override suspend fun ensureDeviceRegistered(): ResultState<Unit> = runCatching {
        val jwt = tokenStore.getJwt() ?: error("Not authenticated")
        sessionDataSource.registerDevice(jwt)
    }.fold(
        onSuccess = { ResultState.Success(Unit) },
        onFailure = { e ->
            AppLogger.e("SessionRepo", "ensureDeviceRegistered failed", e as? Exception)
            ResultState.Error(e)
        }
    )

    override suspend fun startSession(jwt: String, videoId: String): ResultState<SessionStartResponse> =
        runCatching {
            sessionDataSource.startSession(jwt, videoId)
        }.fold(
            onSuccess = { ResultState.Success(it) },
            onFailure = { e ->
                AppLogger.e("SessionRepo", "startSession failed", e as? Exception)
                ResultState.Error(e)
            }
        )

    override suspend fun endSession(jwt: String, sessionId: String) {
        runCatching { sessionDataSource.endSession(jwt, sessionId) }
            .onFailure { AppLogger.e("SessionRepo", "endSession failed (ignored)", it as? Exception) }
    }

    override suspend fun fetchAesKey(
        mediaId: String,
        sessionId: String,
        sessionToken: String
    ): ResultState<ByteArray> = runCatching {
        sessionDataSource.fetchAesKey(mediaId, sessionId, sessionToken)
    }.fold(
        onSuccess = { ResultState.Success(it) },
        onFailure = { e ->
            AppLogger.e("SessionRepo", "fetchAesKey failed", e as? Exception)
            ResultState.Error(e)
        }
    )
}

