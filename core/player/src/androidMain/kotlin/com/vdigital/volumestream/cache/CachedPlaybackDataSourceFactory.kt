package com.vdigital.volumestream.cache

import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

interface CachedPlaybackDataSourceFactory {
    /** Set HTTP headers that will be included on every media request (manifest, key, segment). */
    fun setDefaultHeaders(headers: Map<String, String>)
    fun buildCacheDataSourceFactory(): DefaultMediaSourceFactory
    fun clearCache()
}
