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
    private var playerReleased = false
    private var controllerListener: PlaybackStateController.PlaybackControllerListener? = null

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(cachedPlaybackDataSourceFactory.buildCacheDataSourceFactory())
        .build()

    override fun initPlayer(onReady: () -> Unit) {
        controllerListener?.let { mediaController?.removeListener(it) }
        mediaController?.release()
        mediaController = null
        mediaSession?.release()
        if (playerReleased) {
            player = buildPlayer()
            playerReleased = false
        }
        player.stop()
        player.clearMediaItems()
        mediaSession = MediaSession.Builder(context, player).setCallback(MediaSessionCallback()).build()
        val future = MediaController.Builder(context, mediaSession!!.token).buildAsync()
        Futures.addCallback(future, object : FutureCallback<MediaController> {
            override fun onSuccess(result: MediaController) {
                mediaController = result
                onReady()
            }
            override fun onFailure(t: Throwable) { onReady() }
        }, context.mainExecutor)
    }

    override fun setMediaItem(mediaItem: PlaybackMediaItem) {
        player.setMediaItem(buildMediaItem(mediaItem))
    }

    override fun addMediaItem(mediaItem: PlaybackMediaItem) {
        player.addMediaItem(buildMediaItem(mediaItem))
    }

    override fun addAll(mediaItems: List<PlaybackMediaItem>) {
        mediaItems.forEach { player.addMediaItem(buildMediaItem(it)) }
        player.prepare()
        player.playWhenReady = true
    }

    override fun releasePlayer() {
        controllerListener?.let { mediaController?.removeListener(it) }
        controllerListener = null
        mediaController?.release()
        mediaController = null
        mediaSession?.release()
        mediaSession = null
        playerReleased = true
        player.release()
        cachedPlaybackDataSourceFactory.clearCache()
    }

    override fun getMediaController(): MediaController? = mediaController
    override fun getExoPlayer(): ExoPlayer = player

    override fun setControllerListener(listener: PlaybackStateController.PlaybackControllerListener) {
        controllerListener = listener
        mediaController?.addListener(listener)
    }

    override fun play() { mediaController?.play() }

    private fun buildMediaItem(item: PlaybackMediaItem): MediaItem =
        MediaItem.Builder()
            .setUri(item.streamUrl)
            .setMediaId(item.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist("Artist Name")
                    .build()
            )
            .build()
}
