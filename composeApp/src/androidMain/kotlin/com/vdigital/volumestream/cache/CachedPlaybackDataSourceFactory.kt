package com.vdigital.volumestream.cache

import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

interface CachedPlaybackDataSourceFactory {
    fun buildCacheDataSourceFactory(): DefaultMediaSourceFactory
}