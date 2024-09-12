package com.vdigital.volumestream.platform.controller


import androidx.media3.session.MediaController
import com.vdigital.volumestream.compnent.PlayerComponent
import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState


actual class PlaybackStateController(private val playerComponent: PlayerComponent) {


    actual fun addItem(mediaItem: PlaybackMediaItem) {

    }

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
        playerComponent.initPlayer()
    }

    actual fun pause(playbackState: (PlaybackState) -> Unit) {
    }

    actual fun release() {
    }

    actual fun resume() {
    }

    actual fun isPlaying(): Boolean {
        return playerComponent.getMediaController()?.isPlaying!!
    }

    actual fun duration(): Long {
        return playerComponent.getMediaController()?.duration!!
    }

    actual fun currentPosition(): Long {
        return playerComponent.getMediaController()?.currentPosition!!
    }

    actual fun seekTo(position: Long) {
    }

    actual fun play(playbackState: (PlaybackState) -> Unit) {
    }

    internal fun getController(): MediaController? {
        return playerComponent.getMediaController()
    }

    actual fun addItemItems(items: List<PlaybackMediaItem>) {
        playerComponent.addAll(items)
    }
}

