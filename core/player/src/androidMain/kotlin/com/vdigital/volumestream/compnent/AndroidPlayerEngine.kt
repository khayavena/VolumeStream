package com.vdigital.volumestream.compnent

import androidx.media3.session.MediaController
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vditital.data.model.PlaybackMediaItem

interface AndroidPlayerEngine {
    fun setDefaultHeaders(headers: Map<String, String>)
    /** Pass the 16-byte AES-128 session key so DASH segments can be decrypted. */
    fun setAesKey(key: ByteArray)
    fun initPlayer(onReady: () -> Unit)
    fun setMediaItem(mediaItem: PlaybackMediaItem)
    fun addMediaItem(mediaItem: PlaybackMediaItem)
    fun addAll(mediaItems: List<PlaybackMediaItem>)
    fun releasePlayer()
    fun getMediaController(): MediaController?
    fun getExoPlayer(): androidx.media3.exoplayer.ExoPlayer
    fun setControllerListener(listener: PlaybackStateController.PlaybackControllerListener)
    fun play()
}
