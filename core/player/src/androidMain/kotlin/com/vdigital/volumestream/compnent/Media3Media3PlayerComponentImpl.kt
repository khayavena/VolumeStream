package com.vdigital.volumestream.compnent

import android.app.Application
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
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
    private var controllerListener: PlaybackStateController.PlaybackControllerListener? = null

    @OptIn(UnstableApi::class)
    private fun buildPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                playerConfig.minBufferMs,
                playerConfig.maxBufferMs,
                playerConfig.bufferForPlaybackMs,
                playerConfig.bufferForPlaybackAfterRebufferMs
            )
            .build()

        // Prefer hardware codec extensions (e.g. MediaCodec VP9/AV1 hardware decoder).
        // Falls back to software if no hardware decoder is available.
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(cachedPlaybackDataSourceFactory.buildCacheDataSourceFactory())
            .setLoadControl(loadControl)
            .build()
            .also { player ->
                // Tell ExoPlayer this is movie/TV content so the system uses the
                // correct audio focus behaviour (AUDIOFOCUS_GAIN) and audio session.
                // handleAudioBecomingNoisy = true pauses playback when headphones
                // are unplugged, preventing audio from blasting from the speaker.
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                player.setHandleAudioBecomingNoisy(true)
                // NOTE: do NOT call setVideoScalingMode() here.
                // VIDEO_SCALING_MODE_SCALE_TO_FIT instructs the codec to scale
                // every output frame itself. On MediaTek hardware (c2.mtk.avc.decoder)
                // this triggers "stream data corrupt" telemetry on every decoded frame.
                // PlayerView + RESIZE_MODE_FIT already handles aspect-ratio scaling
                // at the SurfaceView layer without touching the codec pipeline.
                Log.d("VolumeStream", "ExoPlayer built with AudioAttributes + hardware-preferred renderer")
            }
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

        // The same ExoPlayer instance is always reused (never released while the
        // component is alive — see releasePlayer()).  Just stop and clear it so it
        // is ready for new media items without detaching the video surface.
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
        }, ContextCompat.getMainExecutor(context))
    }

    override fun setMediaItem(mediaItem: PlaybackMediaItem) {
        player.setMediaItem(buildMediaItem(mediaItem))
    }

    override fun addMediaItem(mediaItem: PlaybackMediaItem) {
        player.addMediaItem(buildMediaItem(mediaItem))
    }

    override fun addAll(mediaItems: List<PlaybackMediaItem>) {
        // stop() guarantees the player is in STATE_IDLE regardless of its current
        // state (PLAYING, READY, ENDED, etc.) before we reload media.  Without this,
        // clearMediaItems() on a STATE_ENDED player can leave it in a non-IDLE state
        // where the subsequent prepare() call silently fails to re-enable the video
        // renderer — producing audio-only playback on every track after the first.
        player.stop()
        player.clearMediaItems()
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
        // Stop the player but do NOT call player.release() and do NOT set playerReleased.
        // Media3PlayerComponent is a singleton (single DI scope), so the ExoPlayer
        // instance lives for the entire app session.  Releasing it forces a rebuild on
        // the next initPlayer() call.  The rebuilt player has no video surface until the
        // NEXT Compose recomposition runs the AndroidView update block — but addAll()
        // calls player.prepare() in the same coroutine tick, before that recomposition,
        // so the video decoder starts with no surface → audio-only for the second video.
        // By stopping (not releasing) we keep the same ExoPlayer instance alive and its
        // surface already registered with PlayerView, so the next initPlayer() reuses it
        // and video renders from the very first frame.
        player.stop()
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
            uri.contains("/proxy/dash/", ignoreCase = true)    -> MimeTypes.APPLICATION_MPD
            uri.endsWith(".mpd", ignoreCase = true)            -> MimeTypes.APPLICATION_MPD
            uri.endsWith(".mp4", ignoreCase = true)            -> null
            uri.endsWith(".mp3", ignoreCase = true)            -> null
            // HLS master playlist — covers:
            //   /manifest/hls/{id}           master playlist (multi-quality)
            //   /manifest/{id}               bare manifest URL from feed (HLS fallback)
            // ExoPlayer follows variant playlist URLs embedded in the master manifest,
            // fetching quality-specific segments at /proxy/{id}/{quality}/{seg}?t=… (new)
            // or the legacy flat path /proxy/{id}/{seg}?t=… automatically.
            // AES-128-CBC decryption is handled by ExoPlayer via EXT-X-KEY.
            uri.contains("/manifest/hls/", ignoreCase = true)  -> MimeTypes.APPLICATION_M3U8
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
