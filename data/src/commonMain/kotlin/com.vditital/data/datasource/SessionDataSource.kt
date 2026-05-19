package com.vditital.data.datasource

import com.vditital.data.model.SessionStartResponse

interface SessionDataSource {
    /** Registers the device's RSA public key. Returns false only on non-409 errors. */
    suspend fun registerDevice(jwt: String): Boolean

    /** Signs and starts a playback session; returns the session token envelope. */
    suspend fun startSession(
        jwt: String,
        videoId: String,
        offlinePlayback: Boolean = false,
        offlineLicenseSeconds: Long? = null
    ): SessionStartResponse

    /**
     * Fetches the 16-byte AES-128 session key from
     * GET /api/v1/manifest/{mediaId}/key?sid=…&t=…
     * Required to decrypt AES-128-GCM DASH segments.
     */
    suspend fun fetchAesKey(mediaId: String, sessionId: String, sessionToken: String): ByteArray

    /** Writes playback progress for the active user and media item. */
    suspend fun saveRecentlyWatched(jwt: String, mediaId: String, playbackPosition: Long)
    // endSession removed — session cleanup is handled server-side via TTL / 401 revocation.
}
