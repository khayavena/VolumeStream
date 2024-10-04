package com.vdigital.volumestream.compnent

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import com.vdigital.volumestream.cache.CachedPlaybackDataSourceFactory

import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vditital.data.model.PlaybackMediaItem

class Media3Media3PlayerComponentImpl(
    private val context: Application,
    private val cachedPlaybackDataSourceFactory: CachedPlaybackDataSourceFactory
) : Media3PlayerComponent {
    private lateinit var player: ExoPlayer
    private var mediaController: MediaController? = null
    private var mediaSession: MediaSession? = null
    override fun initPlayer() {
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(cachedPlaybackDataSourceFactory.buildCacheDataSourceFactory())
            .build()
        mediaSession =
            MediaSession.Builder(context, player).setCallback(MediaSessionCallback()).build()
        mediaController = mediaSession?.token?.let {
            MediaController.Builder(
                context,
                it
            ).buildAsync().get()
        }
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

        player.release()
        mediaSession?.release()
        mediaSession = null
        cachedPlaybackDataSourceFactory.clearCache()
    }

    override fun getMediaController(): MediaController? {
        return mediaController
    }

    override fun setControllerListener(playbackControllerListener: PlaybackStateController.PlaybackControllerListener) {
        mediaController?.addListener(playbackControllerListener)
    }

    override fun play() {
        mediaController?.play()
    }
}