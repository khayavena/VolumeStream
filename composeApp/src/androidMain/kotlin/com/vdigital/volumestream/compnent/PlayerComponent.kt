package com.vdigital.volumestream.compnent

import androidx.media3.session.MediaController
import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.platform.controller.PlaybackStateController

interface PlayerComponent {
    fun initPlayer()
    fun setMediaItem(mediaItem: PlaybackMediaItem)
    fun addMediaItem(mediaItem: PlaybackMediaItem)
    fun addAll(mediaItems: List<PlaybackMediaItem>)
    fun releasePlayer()
    fun getMediaController(): MediaController?
    fun setControllerListener(playbackControllerListener: PlaybackStateController.PlaybackControllerListener)
    fun play()
}