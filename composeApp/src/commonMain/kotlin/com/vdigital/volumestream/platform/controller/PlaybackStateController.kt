package com.vdigital.volumestream.platform.controller


import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState

expect class PlaybackStateController {
    fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit)
    fun addItem(mediaItem: PlaybackMediaItem)
    fun pause(playbackState: (PlaybackState) -> Unit)
    fun play(playbackState: (PlaybackState) -> Unit)
    fun resume()
    fun release()
    fun isPlaying(): Boolean
    fun duration(): Long
    fun currentPosition(): Long
    fun seekTo(position: Long)
    fun addItemItems(items: List<PlaybackMediaItem>)
    fun downloadDashManifest(playbackItem: PlaybackMediaItem)
}
