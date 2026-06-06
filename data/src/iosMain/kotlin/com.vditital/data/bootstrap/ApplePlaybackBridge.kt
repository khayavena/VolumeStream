package com.vditital.data.bootstrap

import com.vditital.data.repository.AuthRepository
import com.vditital.data.repository.SessionRepository
import com.vditital.data.repository.state.ResultState

private const val HEADER_AUTHORIZATION = "Authorization"
private const val HEADER_SESSION_TOKEN = "X-Session-Token"
private const val HEADER_DEVICE_ID = "X-Device-Id"

/**
 * Bridge helpers for Apple hosts that want to keep playback auth behavior aligned
 * with the existing Kotlin player flow.
 */
object ApplePlaybackBridge {
    private var authRepository: AuthRepository? = null
    private var sessionRepository: SessionRepository? = null

    internal fun bindRepositories(
        authRepository: AuthRepository,
        sessionRepository: SessionRepository,
    ) {
        this.authRepository = authRepository
        this.sessionRepository = sessionRepository
    }

    internal fun isBound(): Boolean =
        authRepository != null && sessionRepository != null

    /** Mirrors existing player URL normalization used by IosPlayerEngine. */
    fun normalizeManifestUrl(url: String): String =
        url.replace("/manifest/dash/", "/manifest/")

    /**
     * Creates playback headers using the same sequence as PlaybackViewModel:
     * ensure JWT -> ensure device registration -> start session.
     */
    suspend fun createPlaybackHeaders(mediaId: String): Map<String, String> {
        val authRepo = authRepository ?: return emptyMap()
        val sessionRepo = sessionRepository ?: return emptyMap()

        val jwt = authRepo.ensureValidJwt() ?: return emptyMap()
        sessionRepo.ensureDeviceRegistered()

        return when (val session = sessionRepo.startSession(jwt, mediaId)) {
            is ResultState.Success -> mapOf(
                HEADER_AUTHORIZATION to "Bearer $jwt",
                HEADER_SESSION_TOKEN to session.data.sessionToken,
                HEADER_DEVICE_ID to sessionRepo.getDeviceId(),
            )
            else -> emptyMap()
        }
    }
}
