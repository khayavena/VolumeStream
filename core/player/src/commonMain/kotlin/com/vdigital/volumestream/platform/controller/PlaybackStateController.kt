package com.vdigital.volumestream.platform.controller

import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vditital.data.model.PlaybackMediaItem

expect class PlaybackStateController {
    /** Attach arbitrary HTTP headers to every player request (manifest, key, segments). */
    fun setAuthHeaders(headers: Map<String, String>)
    /**
     * Supply the 16-byte AES-128 session key so DASH segments can be decrypted.
     * Must be called after [setAuthHeaders] and before [initPlayer].
     * No-op on iOS (AVPlayer handles HLS natively).
     */
    fun setAesKey(key: ByteArray)
    suspend fun prefetchForPlayback(mediaItem: PlaybackMediaItem): PlaybackMediaItem
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
