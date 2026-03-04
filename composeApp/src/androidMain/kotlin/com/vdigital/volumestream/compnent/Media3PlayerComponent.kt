package com.vdigital.volumestream.compnent

import androidx.media3.session.MediaController

import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vditital.data.model.PlaybackMediaItem

interface Media3PlayerComponent {
    fun initPlayer(onReady: () -> Unit = {})
    fun setMediaItem(mediaItem: PlaybackMediaItem)
    fun addMediaItem(mediaItem: PlaybackMediaItem)
    fun addAll(mediaItems: List<PlaybackMediaItem>)
    fun releasePlayer()
    fun getMediaController(): MediaController?
    fun getExoPlayer(): androidx.media3.exoplayer.ExoPlayer
    fun setControllerListener(playbackControllerListener: PlaybackStateController.PlaybackControllerListener)
    fun play()
}