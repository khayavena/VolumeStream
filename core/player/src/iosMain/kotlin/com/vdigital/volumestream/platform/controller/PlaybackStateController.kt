package com.vdigital.volumestream.platform.controller

import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.platform.component.IosPlayerEngine
import com.vditital.data.model.PlaybackMediaItem
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVQueuePlayer

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual class PlaybackStateController(
    private val iosPlayerEngine: IosPlayerEngine
) {
    /** Expose the underlying AVQueuePlayer for callers (e.g. AVPlayerViewController). */
    val avPlayer: AVQueuePlayer get() = iosPlayerEngine.avPlayer

    actual fun setAuthHeaders(headers: Map<String, String>) =
        iosPlayerEngine.setAuthHeaders(headers)

    actual fun setAesKey(key: ByteArray) =
        iosPlayerEngine.setAesKey(key)

    actual suspend fun prefetchForPlayback(mediaItem: PlaybackMediaItem): PlaybackMediaItem =
        iosPlayerEngine.prefetchForPlayback(mediaItem)

    actual fun addItem(mediaItem: PlaybackMediaItem) =
        iosPlayerEngine.addItem(mediaItem)

    actual fun addItemItems(items: List<PlaybackMediaItem>) =
        iosPlayerEngine.addItemItems(items)

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) =
        iosPlayerEngine.initPlayer(callback, playbackState)

    actual fun play(playbackState: (PlaybackState) -> Unit) =
        iosPlayerEngine.play(playbackState)

    actual fun pause(playbackState: (PlaybackState) -> Unit) =
        iosPlayerEngine.pause(playbackState)

    actual fun resume() = iosPlayerEngine.resume()

    actual fun release() = iosPlayerEngine.release()

    actual fun isPlaying(): Boolean = iosPlayerEngine.isPlaying()

    actual fun currentPosition(): Long = iosPlayerEngine.currentPosition()

    actual fun duration(): Long = iosPlayerEngine.duration()

    actual fun seekTo(position: Long) = iosPlayerEngine.seekTo(position)

    actual fun setQuality(quality: PlaybackQuality) = iosPlayerEngine.setQuality(quality)

    actual fun downloadDashManifest(playbackItem: PlaybackMediaItem) =
        iosPlayerEngine.downloadDashManifest(playbackItem)
}
