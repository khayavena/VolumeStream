package com.vdigital.volumestream.platform.controller

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaController
import com.vdigital.volumestream.compnent.Media3PlayerComponent
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Buffering
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Playing
import com.vditital.data.model.PlaybackMediaItem

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class PlaybackStateController(private val media3PlayerComponent: Media3PlayerComponent) {

    private var released = false
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    actual fun addItem(mediaItem: PlaybackMediaItem) {
        media3PlayerComponent.addMediaItem(mediaItem)
    }

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        progressRunnable = null
        media3PlayerComponent.initPlayer {
            if (released) return@initPlayer
            media3PlayerComponent.setControllerListener(PlaybackControllerListener(playbackState))
            val handler = Handler(Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    if (media3PlayerComponent.getMediaController()?.isPlaying == true) {
                        callback(currentPosition(), duration())
                        playbackState(Playing)
                    }
                    if (media3PlayerComponent.getMediaController()?.playbackState == Player.STATE_BUFFERING) {
                        playbackState(Buffering)
                    }
                    handler.postDelayed(this, 1000)
                }
            }
            progressHandler = handler
            progressRunnable = runnable
            handler.post(runnable)
        }
    }

    actual fun pause(playbackState: (PlaybackState) -> Unit) {
        media3PlayerComponent.getMediaController()?.pause()
        playbackState(PlaybackState.Paused)
    }

    actual fun release() {
        if (released) return
        released = true
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        progressRunnable = null
        media3PlayerComponent.releasePlayer()
    }

    actual fun resume() { media3PlayerComponent.play() }

    actual fun isPlaying(): Boolean =
        media3PlayerComponent.getMediaController()?.isPlaying == true

    actual fun duration(): Long =
        media3PlayerComponent.getMediaController()?.duration ?: 0L

    actual fun currentPosition(): Long =
        media3PlayerComponent.getMediaController()?.currentPosition ?: 0L

    actual fun seekTo(position: Long) {
        media3PlayerComponent.getMediaController()?.seekTo(position)
    }

    actual fun play(playbackState: (PlaybackState) -> Unit) {
        media3PlayerComponent.getMediaController()?.play()
        playbackState(Playing)
    }

    internal fun getController(): MediaController? = media3PlayerComponent.getMediaController()

    internal fun getExoPlayer(): androidx.media3.exoplayer.ExoPlayer =
        media3PlayerComponent.getExoPlayer()

    actual fun addItemItems(items: List<PlaybackMediaItem>) {
        media3PlayerComponent.addAll(items)
    }

    actual fun downloadDashManifest(playbackItem: PlaybackMediaItem) {}

    @OptIn(UnstableApi::class)
    actual fun setQuality(quality: PlaybackQuality) {
        val selector = media3PlayerComponent.getExoPlayer().trackSelector as? DefaultTrackSelector ?: run {
            Log.w("VolumeStream", "setQuality: trackSelector is not DefaultTrackSelector")
            return
        }
        val params = selector.buildUponParameters()
        if (quality == PlaybackQuality.Auto) {
            params.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                  .setMaxVideoBitrate(Int.MAX_VALUE)
            Log.d("VolumeStream", "setQuality -> Auto (no constraints)")
        } else {
            params.setMaxVideoSize(Int.MAX_VALUE, quality.maxHeight)
                  .setMaxVideoBitrate(quality.maxBitrate.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            Log.d("VolumeStream", "setQuality -> ${quality.label} maxH=${quality.maxHeight} maxBitrate=${quality.maxBitrate}")
        }
        selector.setParameters(params)
    }

    class PlaybackControllerListener(val playbackState: (PlaybackState) -> Unit) : Listener {
        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            error.message?.let { playbackState(PlaybackState.Error(it)) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                this.playbackState(PlaybackState.Ended)
            }
        }
    }
}
