package com.vdigital.volumestream.compnent

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.vdigital.volumestream.cache.CachedPlaybackDataSourceFactory
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vditital.data.model.PlaybackMediaItem

class Media3Media3PlayerComponentImpl(
    private val context: Application,
    private val cachedPlaybackDataSourceFactory: CachedPlaybackDataSourceFactory
) : Media3PlayerComponent {
    private var player: ExoPlayer = buildPlayer()
    private var mediaController: MediaController? = null
    private var mediaSession: MediaSession? = null
    // Track whether player.release() has been called so initPlayer() can rebuild it (#2 fix).
    private var playerReleased = false
    // Stored so it can be removed before the controller is torn down (#4 fix).
    private var controllerListener: PlaybackStateController.PlaybackControllerListener? = null

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(cachedPlaybackDataSourceFactory.buildCacheDataSourceFactory())
        .build()

    override fun initPlayer(onReady: () -> Unit) {
        // Remove the old listener and fully release the old controller/session first (#4 fix).
        controllerListener?.let { mediaController?.removeListener(it) }
        mediaController?.release()
        mediaController = null
        mediaSession?.release()
        // Rebuild ExoPlayer if it was previously released — calling stop() on a
        // released player throws IllegalStateException (#2 fix).
        if (playerReleased) {
            player = buildPlayer()
            playerReleased = false
        }
        // All ExoPlayer calls must be on the main thread.
        player.stop()
        player.clearMediaItems()
        mediaSession = MediaSession.Builder(context, player).setCallback(MediaSessionCallback()).build()
        // Use buildAsync() + callback instead of blocking .get() so the main
        // thread is never stalled (#1 fix).
        val future = MediaController.Builder(context, mediaSession!!.token).buildAsync()
        Futures.addCallback(future, object : FutureCallback<MediaController> {
            override fun onSuccess(result: MediaController) {
                mediaController = result
                onReady()
            }
            override fun onFailure(t: Throwable) {
                // Controller failed to build — surface via onReady so the caller
                // can still start the progress timer and show an error state.
                onReady()
            }
        }, context.mainExecutor)
    }


    override fun setMediaItem(mediaItem: PlaybackMediaItem) {
        val mediaItemWithMetadata = MediaItem.Builder()
            .setUri(mediaItem.streamUrl).setMediaId(mediaItem.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(mediaItem.title)
                    .setArtist("Artist Name")
                    .build()
            )
            .build()
        player.setMediaItem(mediaItemWithMetadata)
    }

    override fun addMediaItem(mediaItem: PlaybackMediaItem) {
        val mediaItemWithMetadata = MediaItem.Builder()
            .setUri(mediaItem.streamUrl).setMediaId(mediaItem.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(mediaItem.title)
                    .setArtist("Artist Name")
                    .build()
            )
            .build()
        player.addMediaItem(mediaItemWithMetadata)

    }

    override fun addAll(mediaItems: List<PlaybackMediaItem>) {
        val iterator = mediaItems.iterator()
        while (iterator.hasNext()) {
            val mediaItem = iterator.next()
            val mediaItemWithMetadata = MediaItem.Builder()
                .setUri(mediaItem.streamUrl).setMediaId(mediaItem.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(mediaItem.title)
                        .setArtist("Artist Name")
                        .build()
                )
                .build()
            player?.addMediaItem(mediaItemWithMetadata)
        }
        player?.prepare()
        player?.playWhenReady = true
    }

    override fun releasePlayer() {
        // Remove listener before releasing the controller (#4 fix).
        controllerListener?.let { mediaController?.removeListener(it) }
        controllerListener = null
        // Release the controller before the session it is bound to (#3 fix).
        mediaController?.release()
        mediaController = null
        mediaSession?.release()
        mediaSession = null
        playerReleased = true
        player.release()
        cachedPlaybackDataSourceFactory.clearCache()
    }

    override fun getMediaController(): MediaController? {
        return mediaController
    }

    override fun getExoPlayer(): ExoPlayer {
        return player
    }

    override fun setControllerListener(playbackControllerListener: PlaybackStateController.PlaybackControllerListener) {
        controllerListener = playbackControllerListener
        mediaController?.addListener(playbackControllerListener)
    }

    override fun play() {
        mediaController?.play()
    }
}