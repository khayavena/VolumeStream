package com.vditital.data.repository

import com.vditital.data.model.SessionStartResponse
import com.vditital.data.repository.state.ResultState

interface SessionRepository {
    /** Registers device once; safe to call on every launch (409 is ignored). */
    suspend fun ensureDeviceRegistered(): ResultState<Unit>

    /** Starts an authenticated playback session for [videoId]. */
    suspend fun startSession(
        jwt: String,
        videoId: String,
        offlinePlayback: Boolean = false,
        offlineLicenseSeconds: Long? = null
    ): ResultState<SessionStartResponse>

    /**
     * Returns the 16-byte AES-128 key that decrypts DASH segments for this session.
     * Call immediately after [startSession] succeeds, before the player starts buffering.
     */
    suspend fun fetchAesKey(mediaId: String, sessionId: String, sessionToken: String): ResultState<ByteArray>

    /** Persists playback progress for the current user/media in Stream Vault. */
    suspend fun saveRecentlyWatched(mediaId: String, playbackPosition: Long): ResultState<Unit>
    // endSession removed — session cleanup is handled server-side via TTL / 401 revocation.
}
