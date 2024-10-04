package com.vdigital.volumestream.platform.controller

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
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class)
actual class PlaybackStateController {

    val avPlayer: AVQueuePlayer = AVQueuePlayer()
    actual fun addItem(mediaItem: PlaybackMediaItem) {
        val nsUrl = NSURL.URLWithString(mediaItem.streamUrl)
        val playerItem = nsUrl?.let { AVPlayerItem(it) }
        if (playerItem != null) {
            avPlayer.insertItem(playerItem, null)
        }
    }

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
        NSTimer.scheduledTimerWithTimeInterval(1.0, true) {
            if (isPlaying()) {
                callback(currentPosition(), duration())
                playbackState(playerState())
            }
            if (avPlayer.currentItem?.isPlaybackBufferEmpty() == true) {
                playbackState(Buffering)
            }
        }
    }

    actual fun pause(playbackState: (PlaybackState) -> Unit) {
        (avPlayer as AVPlayer).pause()
        playbackState(Paused)
    }

    actual fun release() {
        avPlayer.removeAllItems()
    }

    actual fun resume() {
        avPlayer.play()
    }

    actual fun isPlaying(): Boolean {
        return avPlayer.rate.toLong().toInt() != 0 && avPlayer.error == null
    }

    actual fun duration(): Long {
        val duration = avPlayer.currentItem?.let { CMTimeGetSeconds(it.duration) }
        if (duration != null) {
            return duration.toLong()
        }
        return 0L
    }

    actual fun currentPosition(): Long {
        val currentTime = avPlayer.currentItem?.let { CMTimeGetSeconds(it.currentTime()) }
        if (currentTime != null) {
            return currentTime.toLong()
        }
        return 0L
    }


    actual fun seekTo(position: Long) {
        val seekPosition = CMTimeMake(position, 1)
        avPlayer.seekToTime(seekPosition)
    }

    actual fun play(playbackState: (PlaybackState) -> Unit) {
        avPlayer.play()
        playbackState(Playing)
    }

    actual fun addItemItems(items: List<PlaybackMediaItem>) {
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            addItem(iterator.next())
        }
    }

    private fun playerState(): PlaybackState {
        val item = avPlayer.currentItem
        return if (item?.isPlaybackLikelyToKeepUp() == true || item?.isPlaybackBufferFull() == true) {
            Playing
        } else if (item?.isPlaybackBufferEmpty() == true) {
            Buffering
        } else {
            if (avPlayer.error != null) {
                Error(avPlayer.error!!.code().toString() + "Something went wrong")
            } else {
                Buffering
            }
        }
    }

    actual fun downloadDashManifest(playbackItem: PlaybackMediaItem) {
        dispatch_async(dispatch_get_main_queue()) {
            //
        }
    }
}