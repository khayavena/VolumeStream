package com.vditital.data.repository

import com.vditital.data.model.SessionStartResponse
import com.vditital.data.repository.state.ResultState

interface SessionRepository {
    /** Registers device once; safe to call on every launch (409 is ignored). */
    suspend fun ensureDeviceRegistered(): ResultState<Unit>
    /** Starts an authenticated playback session for [videoId]. */
    suspend fun startSession(jwt: String, videoId: String): ResultState<SessionStartResponse>
    /** Ends the session; errors are swallowed. */
    suspend fun endSession(jwt: String, sessionId: String)
    /**
     * Returns the 16-byte AES-128 key that decrypts DASH segments for this session.
     * Call immediately after [startSession] succeeds, before the player starts buffering.
     */
    suspend fun fetchAesKey(mediaId: String, sessionId: String, sessionToken: String): ResultState<ByteArray>
}

