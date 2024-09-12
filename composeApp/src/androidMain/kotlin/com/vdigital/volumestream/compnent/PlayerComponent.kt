package com.vdigital.volumestream.compnent

import androidx.media3.session.MediaController
import com.vdigital.volumestream.model.PlaybackMediaItem

interface PlayerComponent {
    fun initPlayer()
    fun setMediaItem(mediaItem: PlaybackMediaItem)
    fun addMediaItem(mediaItem: PlaybackMediaItem)
    fun addAll(mediaItem: List<PlaybackMediaItem>)
    fun releasePlayer()
    fun getMediaController(): MediaController?
}