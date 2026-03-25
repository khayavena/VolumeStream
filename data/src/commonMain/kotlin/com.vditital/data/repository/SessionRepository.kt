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
}

