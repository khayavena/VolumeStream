package com.vdigital.volumestream.platform.controller

import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.session.MediaController
import com.vdigital.volumestream.compnent.Media3PlayerComponent
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

    class PlaybackControllerListener(val playbackState: (PlaybackState) -> Unit) : Listener {
        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            error.message?.let { playbackState(PlaybackState.Error(it)) }
        }
    }
}
