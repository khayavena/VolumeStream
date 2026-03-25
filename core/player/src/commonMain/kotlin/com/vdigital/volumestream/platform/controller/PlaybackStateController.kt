package com.vdigital.volumestream.platform.controller

import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vditital.data.model.PlaybackMediaItem

expect class PlaybackStateController {
    /** Attach arbitrary HTTP headers to every player request (manifest, key, segments).
     *  Called from common code before [initPlayer]; header names and values are
     *  supplied by the caller so the player layer stays protocol-agnostic. */
    fun setAuthHeaders(headers: Map<String, String>)
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
    fun setQuality(quality: PlaybackQuality)
}
