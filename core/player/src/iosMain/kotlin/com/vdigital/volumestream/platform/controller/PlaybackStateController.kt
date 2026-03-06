package com.vdigital.volumestream.platform.controller

import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Buffering
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Error
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Paused
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Playing
import com.vditital.data.model.PlaybackMediaItem
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.isPlaybackBufferEmpty
import platform.AVFoundation.isPlaybackBufferFull
import platform.AVFoundation.isPlaybackLikelyToKeepUp
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSTimer
import platform.Foundation.NSURL

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class)
actual class PlaybackStateController {

    val avPlayer: AVQueuePlayer = AVQueuePlayer()
    private var progressTimer: NSTimer? = null
    private var released = false

    /**
     * Build an NSURL from a media item's streamUrl.
     *
     * iOS stores local paths as plain POSIX paths ("/var/mobile/.../file.mp4").
     * Android stores them as file:// URIs ("file:///data/.../file.mp4").
     * Handle both so that downloaded-item playback works on both platforms.
     */
    private fun nsUrlFor(streamUrl: String): NSURL? = when {
        streamUrl.startsWith("file://") ->
            NSURL.URLWithString(streamUrl)           // already a valid file URL string
        streamUrl.startsWith("/") ->
            NSURL.fileURLWithPath(streamUrl)         // plain POSIX path → file URL
        else ->
            NSURL.URLWithString(streamUrl)           // http/https remote URL
    }

    actual fun addItem(mediaItem: PlaybackMediaItem) {
        // AVFoundation must be accessed on the Main thread.
        val url = nsUrlFor(mediaItem.streamUrl) ?: return
        val playerItem = AVPlayerItem(url)
        avPlayer.insertItem(playerItem, afterItem = null)
    }

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
        progressTimer?.invalidate()
        progressTimer = null

        // NSTimer must be added to the Main run loop — scheduledTimerWithTimeInterval
        // only works correctly when called on Main. We schedule explicitly here.
        val timer = NSTimer.timerWithTimeInterval(1.0, repeats = true) {
            if (isPlaying()) {
                callback(currentPosition(), duration())
                playbackState(playerState())
            }
            if (avPlayer.currentItem?.isPlaybackBufferEmpty() == true) playbackState(Buffering)
            if (avPlayer.currentItem == null && !released) playbackState(PlaybackState.Ended)
        }
        NSRunLoop.mainRunLoop.addTimer(timer, forMode = NSRunLoopCommonModes)
        progressTimer = timer
    }

    actual fun pause(playbackState: (PlaybackState) -> Unit) {
        (avPlayer as AVPlayer).pause()
        playbackState(Paused)
    }

    actual fun release() {
        if (released) return
        released = true
        (avPlayer as AVPlayer).pause()
        progressTimer?.invalidate()
        progressTimer = null
        avPlayer.removeAllItems()
    }

    actual fun resume() { avPlayer.play() }

    actual fun isPlaying(): Boolean =
        avPlayer.rate.toLong().toInt() != 0 && avPlayer.error == null

    actual fun duration(): Long {
        val sec = avPlayer.currentItem?.let { CMTimeGetSeconds(it.duration) }
        return if (sec != null && !sec.isNaN() && sec > 0) (sec * 1000).toLong() else 0L
    }

    actual fun currentPosition(): Long {
        val sec = avPlayer.currentItem?.let { CMTimeGetSeconds(it.currentTime()) }
        return if (sec != null && !sec.isNaN()) (sec * 1000).toLong() else 0L
    }

    actual fun seekTo(position: Long) {
        avPlayer.seekToTime(CMTimeMake(position, 1000))
    }

    actual fun play(playbackState: (PlaybackState) -> Unit) {
        avPlayer.play()
        playbackState(Playing)
    }

    actual fun addItemItems(items: List<PlaybackMediaItem>) {
        avPlayer.removeAllItems()
        items.forEach { addItem(it) }
    }

    actual fun downloadDashManifest(playbackItem: PlaybackMediaItem) {}

    actual fun setQuality(quality: PlaybackQuality) {
        // AVFoundation selects the optimal HLS variant automatically.
    }

    private fun playerState(): PlaybackState {
        val item = avPlayer.currentItem
        return when {
            item?.isPlaybackLikelyToKeepUp() == true || item?.isPlaybackBufferFull() == true -> Playing
            item?.isPlaybackBufferEmpty() == true -> Buffering
            avPlayer.error != null -> Error(avPlayer.error!!.code().toString())
            else -> Buffering
        }
    }
}
