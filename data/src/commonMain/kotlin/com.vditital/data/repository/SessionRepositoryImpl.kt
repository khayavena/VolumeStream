package com.vditital.data.repository

import com.vditital.data.datasource.SessionDataSource
import com.vditital.data.model.SessionStartResponse
import com.vditital.data.repository.state.ResultState
import com.vditital.data.security.TokenStore
import com.vditital.data.util.AppLogger
import com.vditital.data.util.deviceTamperReason
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionRepositoryImpl(
    private val sessionDataSource: SessionDataSource,
    private val tokenStore: TokenStore
) : SessionRepository {

    /**
     * Idempotency cache for device registration.
     *
     * Once the server returns 200/201/409 (all meaning "device is known"), we mark
     * the device as registered for the lifetime of this process and skip every
     * subsequent call without hitting the network.
     *
     * All reads/writes are guarded by [registrationMutex] which provides the
     * necessary memory-visibility happens-before guarantee on all platforms.
     */
    private var _deviceRegistered = false
    private val registrationMutex = Mutex()

    override suspend fun ensureDeviceRegistered(): ResultState<Unit> {
        val tamperReason = deviceTamperReason()
        if (tamperReason != null) {
            AppLogger.w("SessionRepo", "ensureDeviceRegistered blocked: $tamperReason")
            return ResultState.Error(
                IllegalStateException(
                    "Device registration blocked: this device appears tampered. Please use a secure device."
                )
            )
        }
        val jwt = tokenStore.getJwt()
        if (jwt.isNullOrBlank()) {
            AppLogger.d("SessionRepo", "ensureDeviceRegistered skipped (no JWT)")
            return ResultState.Success(Unit)
        }

        // One caller at a time; the inner check short-circuits all subsequent callers
        // once registration succeeds — no extra network calls on Compose recompositions.
        return registrationMutex.withLock {
            if (_deviceRegistered) {
                AppLogger.d("SessionRepo", "ensureDeviceRegistered skipped (already registered this session)")
                return@withLock ResultState.Success(Unit)
            }

            runCatching {
                sessionDataSource.registerDevice(jwt)
            }.fold(
                onSuccess = { registered ->
                    if (registered) {
                        _deviceRegistered = true
                        AppLogger.d("SessionRepo", "ensureDeviceRegistered OK — future calls will be no-ops")
                        ResultState.Success(Unit)
                    } else {
                        AppLogger.w("SessionRepo", "ensureDeviceRegistered returned false — will retry on next call")
                        ResultState.Error(IllegalStateException("Device registration was rejected by server"))
                    }
                },
                onFailure = { e ->
                    AppLogger.e("SessionRepo", "ensureDeviceRegistered failed", e as? Exception ?: Exception(e))
                    ResultState.Error(e)
                }
            )
        }
    }

    override suspend fun startSession(
        jwt: String,
        videoId: String,
        offlinePlayback: Boolean,
        offlineLicenseSeconds: Long?
    ): ResultState<SessionStartResponse> =
        runCatching {
            sessionDataSource.startSession(
                jwt = jwt,
                videoId = videoId,
                offlinePlayback = offlinePlayback,
                offlineLicenseSeconds = offlineLicenseSeconds
            )
        }.fold(
            onSuccess = { ResultState.Success(it) },
            onFailure = { e ->
                AppLogger.e("SessionRepo", "startSession failed", e as? Exception ?: Exception(e))
                ResultState.Error(e)
            }
        )


    override suspend fun fetchAesKey(
        mediaId: String,
        sessionId: String,
        sessionToken: String
    ): ResultState<ByteArray> = runCatching {
        sessionDataSource.fetchAesKey(mediaId, sessionId, sessionToken)
    }.fold(
        onSuccess = { ResultState.Success(it) },
        onFailure = { e ->
            AppLogger.e("SessionRepo", "fetchAesKey failed", e as? Exception ?: Exception(e))
            ResultState.Error(e)
        }
    )

    override suspend fun saveRecentlyWatched(mediaId: String, playbackPosition: Long): ResultState<Unit> {
        val jwt = tokenStore.getJwt().orEmpty()
        if (jwt.isBlank()) {
            return ResultState.Error(IllegalStateException("No JWT available for recently watched sync"))
        }
        return runCatching {
            sessionDataSource.saveRecentlyWatched(
                jwt = jwt,
                mediaId = mediaId,
                playbackPosition = playbackPosition
            )
        }.fold(
            onSuccess = { ResultState.Success(Unit) },
            onFailure = { e ->
                AppLogger.e("SessionRepo", "saveRecentlyWatched failed", e as? Exception ?: Exception(e))
                ResultState.Error(e)
            }
        )
    }

    override fun getDeviceId(): String = tokenStore.getDeviceId()
}
