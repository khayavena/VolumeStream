package com.vditital.data.datasource

import com.vditital.data.model.SessionStartResponse

interface SessionDataSource {
    /** Registers the device's RSA public key. Returns false only on non-409 errors. */
    suspend fun registerDevice(jwt: String): Boolean
    /** Signs and starts a playback session; returns the session token envelope. */
    suspend fun startSession(jwt: String, videoId: String): SessionStartResponse
    /** Revokes the session immediately. */
    suspend fun endSession(jwt: String, sessionId: String)
}

