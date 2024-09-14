package com.vdigital.volumestream.platform.controller


import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.session.MediaController
import com.vdigital.volumestream.compnent.PlayerComponent
import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState


actual class PlaybackStateController(private val playerComponent: PlayerComponent) {


    actual fun addItem(mediaItem: PlaybackMediaItem) {

    }

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
        playerComponent.initPlayer()
        playerComponent.setControllerLister(PlaybackControllerListener(playbackState))
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
        return playerComponent.getMediaController()?.isPlaying==true
    }

    actual fun duration(): Long {
        if( playerComponent.getMediaController()?.duration != null){
            return  playerComponent.getMediaController()?.duration!!
        }
        return 0L
    }

    actual fun currentPosition(): Long {
        if(playerComponent.getMediaController()?.currentPosition!= null){
           return playerComponent.getMediaController()?.currentPosition!!
        }
        return 0L
    }

    actual fun seekTo(position: Long) {
        playerComponent.getMediaController()?.seekTo(position)
    }

    actual fun play(playbackState: (PlaybackState) -> Unit) {
        playerComponent.getMediaController()?.play()
        playbackState(PlaybackState.playing)
    }

    internal fun getController(): MediaController? {
        return playerComponent.getMediaController()
    }

    actual fun addItemItems(items: List<PlaybackMediaItem>) {
        playerComponent.addAll(items)
    }

    class PlaybackControllerListener(val playbackState: (PlaybackState) -> Unit) : Listener {
        private var currentPlayer: Player? = null
        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            error.message?.let { PlaybackState.error(errorMessage = it) }
                ?.let { playbackState(it) }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)
            currentPlayer = player
//            if (player.isLoading) {
//                playbackState(PlaybackState.bufferig)
//            } else if (player.isPlaying) {
//                playbackState(PlaybackState.playing)
//            } else {
//                playbackState(PlaybackState.paused)
//            }
            playbackState(PlaybackState.playing)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
//            if (playbackState == Player.STATE_READY && currentPlayer?.playWhenReady == false) {
//                playbackState(PlaybackState.paused)
//            } else if (playbackState == Player.STATE_READY) {
//                playbackState(PlaybackState.playing)
//            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
//            if (currentPlayer?.playbackState == Player.STATE_READY && currentPlayer?.playWhenReady == false) {
//                playbackState(PlaybackState.paused)
//            } else {
//                playbackState(PlaybackState.playing)
//            }
        }
    }
}

