package com.vdigital.volumestream.compnent

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.vdigital.volumestream.cache.CachedPlaybackDataSourceFactory
import com.vdigital.volumestream.config.PlayerConfig
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vditital.data.model.PlaybackMediaItem

class Media3Media3PlayerComponentImpl(
    private val context: Application,
    private val cachedPlaybackDataSourceFactory: CachedPlaybackDataSourceFactory,
    private val playerConfig: PlayerConfig = PlayerConfig()
) : Media3PlayerComponent {

    private var player: ExoPlayer = buildPlayer()
    private var mediaController: MediaController? = null
    private var mediaSession: MediaSession? = null
    private var playerReleased = false
    private var controllerListener: PlaybackStateController.PlaybackControllerListener? = null

    private fun buildPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                playerConfig.minBufferMs,
                playerConfig.maxBufferMs,
                playerConfig.bufferForPlaybackMs,
                playerConfig.bufferForPlaybackAfterRebufferMs
            )
            .build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(cachedPlaybackDataSourceFactory.buildCacheDataSourceFactory())
            .setLoadControl(loadControl)
            .build()
    }

    override fun setDefaultHeaders(headers: Map<String, String>) {
        cachedPlaybackDataSourceFactory.setDefaultHeaders(headers)
    }

    override fun setAesKey(key: ByteArray) {
        cachedPlaybackDataSourceFactory.setAesKey(key)
    }

    override fun initPlayer(onReady: () -> Unit) {
        controllerListener?.let { mediaController?.removeListener(it) }
        mediaController?.release()
        mediaController = null
        mediaSession?.release()
        mediaSession = null

        // IMPORTANT: do NOT release + rebuild the ExoPlayer here.
        // Rebuilding detaches the player from PlayerView's video surface, producing
        // black video with audio-only playback. Instead, stop/clear the existing
        // player so it is ready for a fresh media item.
        // The httpFactory's request properties (auth headers) are updated live via
        // setDefaultHeaders() — no ExoPlayer rebuild is needed to pick them up.
        // We only rebuild if the player was explicitly released via releasePlayer().
        if (playerReleased) {
            player = buildPlayer()
            playerReleased = false
        } else {
            player.stop()
            player.clearMediaItems()
        }

        mediaSession = MediaSession.Builder(context, player).setCallback(MediaSessionCallback()).build()
        val future = MediaController.Builder(context, mediaSession!!.token).buildAsync()
        Futures.addCallback(future, object : FutureCallback<MediaController> {
            override fun onSuccess(result: MediaController) {
                mediaController = result
                onReady()
            }
            override fun onFailure(t: Throwable) { onReady() }
        }, ContextCompat.getMainExecutor(context))
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

    private fun buildMediaItem(item: PlaybackMediaItem): MediaItem {
        val uri = item.streamUrl
        val mimeType = when {
            uri.contains("/manifest/dash/", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            uri.endsWith(".mpd", ignoreCase = true)            -> MimeTypes.APPLICATION_MPD
            uri.endsWith(".mp4", ignoreCase = true)            -> null
            uri.endsWith(".mp3", ignoreCase = true)            -> null
            // /manifest/{id} — HLS (.m3u8) served by the StreamVault manifest endpoint
            uri.contains("/manifest/", ignoreCase = true)      -> MimeTypes.APPLICATION_M3U8
            uri.startsWith("http")                             -> MimeTypes.APPLICATION_M3U8
            else                                               -> null
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri)
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist("Artist Name")
                    .build()
            )
            .build()
    }
}
