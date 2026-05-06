package com.vdigital.volumestream.cache

import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

interface CachedPlaybackDataSourceFactory {
    /** Set HTTP headers that will be included on every media request (manifest, key, segment). */
    fun setDefaultHeaders(headers: Map<String, String>)
    /**
     * Provide the 16-byte AES-128 session key returned by
     * GET /api/v1/manifest/{mediaId}/key?sid=…&t=…
     *
     * Must be called before playback starts so that DASH segments routed through
     * /api/v1/proxy/dash/{mediaId}/{segmentIdx}?t=… are decrypted before ExoPlayer
     * parses them.  The path is matched via [PlayerConfig.dashProxyPathFragment].
     */
    fun setAesKey(key: ByteArray)
    fun buildCacheDataSourceFactory(): DefaultMediaSourceFactory
    fun clearCache()
}
