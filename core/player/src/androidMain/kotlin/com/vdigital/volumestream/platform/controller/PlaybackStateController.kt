package com.vdigital.volumestream.platform.controller

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.Listener
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
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

    actual fun setAuthHeaders(headers: Map<String, String>) {
        media3PlayerComponent.setDefaultHeaders(headers)
    }

    actual fun setAesKey(key: ByteArray) {
        media3PlayerComponent.setAesKey(key)
    }

    actual fun addItem(mediaItem: PlaybackMediaItem) {
        media3PlayerComponent.addMediaItem(mediaItem)
    }

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
        // Reset released flag so the onReady lambda below isn't skipped when
        // this controller is reused (single DI scope) after a previous release().
        released = false
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        progressRunnable = null
        media3PlayerComponent.initPlayer {
            if (released) return@initPlayer
            media3PlayerComponent.setControllerListener(PlaybackControllerListener(playbackState))
            val handler = Handler(Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    if (released) return   // player was released mid-playback
                    val exo = media3PlayerComponent.getExoPlayer()
                    // C.TIME_UNSET == Long.MIN_VALUE — normalise to 0 so the
                    // ViewModel can safely divide position / duration.
                    val dur = exo.duration.coerceAtLeast(0L)
                    val pos = exo.currentPosition.coerceAtLeast(0L)
                    // Always fire the callback so the seek bar reflects the
                    // true position after a seek, even while briefly paused or
                    // buffering between ticks.
                    callback(pos, dur)
                    when {
                        exo.isPlaying ->
                            playbackState(Playing)
                        exo.playbackState == Player.STATE_BUFFERING ->
                            playbackState(Buffering)
                        exo.playbackState == Player.STATE_ENDED ->
                            playbackState(PlaybackState.Ended)
                    }
                    handler.postDelayed(this, 500) // 500 ms ticks for a smooth slider
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
        try {
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
        } catch (e: Exception) {
            Log.e("VolumeStream", "setQuality failed, continuing with current quality", e)
        }
    }

    class PlaybackControllerListener(val playbackState: (PlaybackState) -> Unit) : Listener {
        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            // ERROR_CODE_AUTHENTICATION_EXPIRED = 4003 in Media3.
            // This happens when the DASH segment request returns 401 mid-playback
            // (e.g. the session was revoked server-side). Treat it the same way as
            // the Ktor HTTP interceptor: clear the JWT and signal re-login so the
            // player doesn't keep fetching segments with the dead token, which
            // caused the "Connection reset by peer" and the segment-gap in the logs.
            //
            // NOTE: ExoPlayer typically raises ERROR_CODE_IO_BAD_HTTP_STATUS (not
            // ERROR_CODE_AUTHENTICATION_EXPIRED) for 401 responses, so we also walk
            // the cause chain for an InvalidResponseCodeException with responseCode 401.
            if (error.errorCode == PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED || error.is401()) {
                com.vditital.data.security.SessionRevokedBus.emit()
                playbackState(PlaybackState.SessionExpired)
            } else {
                error.message?.let { playbackState(PlaybackState.Error(it)) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                this.playbackState(PlaybackState.Ended)
            }
        }
    }
}

/**
 * Walks the [Throwable] cause chain looking for an
 * [HttpDataSource.InvalidResponseCodeException] with HTTP 401.
 * ExoPlayer raises [PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS] for 401s
 * (not [PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED]), so the raw
 * response code must be checked in the cause chain.
 */
private fun PlaybackException.is401(): Boolean {
    var t: Throwable? = cause
    while (t != null) {
        if (t is HttpDataSource.InvalidResponseCodeException && t.responseCode == 401) return true
        t = t.cause
    }
    return false
}

