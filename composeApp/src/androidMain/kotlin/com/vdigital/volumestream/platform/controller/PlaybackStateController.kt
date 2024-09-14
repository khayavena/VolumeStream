package com.vdigital.volumestream.platform.controller


import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.session.MediaController
import com.vdigital.volumestream.compnent.PlayerComponent
import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.bufferig
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.playing


actual class PlaybackStateController(private val playerComponent: PlayerComponent) {


    actual fun addItem(mediaItem: PlaybackMediaItem) {

    }

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
        playerComponent.initPlayer()
        playerComponent.setControllerListener(PlaybackControllerListener(playbackState))
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (playerComponent.getMediaController() != null && playerComponent.getMediaController()?.isPlaying == true) {
                    callback(currentPosition(), duration())
                    playbackState(playing)
                }
                val state = playerComponent.getMediaController()?.playbackState
                if (state == Player.STATE_BUFFERING) {
                    playbackState(bufferig)
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
    }

    actual fun pause(playbackState: (PlaybackState) -> Unit) {
        playerComponent.getMediaController()?.pause()
        playbackState(PlaybackState.paused)
    }

    actual fun release() {
        playerComponent.releasePlayer()
    }

    actual fun resume() {
        playerComponent.play()
    }

    actual fun isPlaying(): Boolean {
        return playerComponent.getMediaController()?.isPlaying == true
    }

    actual fun duration(): Long {
        if (playerComponent.getMediaController()?.duration != null) {
            return playerComponent.getMediaController()?.duration!!
        }
        return 0L
    }

    actual fun currentPosition(): Long {
        if (playerComponent.getMediaController()?.currentPosition != null) {
            return playerComponent.getMediaController()?.currentPosition!!
        }
        return 0L
    }

    actual fun seekTo(position: Long) {
        playerComponent.getMediaController()?.seekTo(position)
    }

    actual fun play(playbackState: (PlaybackState) -> Unit) {
        playerComponent.getMediaController()?.play()
        playbackState(playing)
    }

    internal fun getController(): MediaController? {
        return playerComponent.getMediaController()
    }

    actual fun addItemItems(items: List<PlaybackMediaItem>) {
        playerComponent.addAll(items)
    }

    class PlaybackControllerListener(val playbackState: (PlaybackState) -> Unit) : Listener {
        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            error.message?.let { PlaybackState.error(errorMessage = it) }
                ?.let { playbackState(it) }
        }
    }

}

