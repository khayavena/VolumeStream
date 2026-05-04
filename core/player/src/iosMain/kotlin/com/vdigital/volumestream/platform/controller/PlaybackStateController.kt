package com.vdigital.volumestream.platform.controller

import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Buffering
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Error
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Paused
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState.Playing
import com.vdigital.volumestream.config.PlayerConfig
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.isPlaybackBufferEmpty
import platform.AVFoundation.isPlaybackBufferFull
import platform.AVFoundation.isPlaybackLikelyToKeepUp
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.preferredPeakBitRate
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.*
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSTimer
import platform.Foundation.NSURL

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual class PlaybackStateController(
    private val playerConfig: PlayerConfig = PlayerConfig()
) {

    private val controllerId = hashCode()
    private var timerTick = 0
    private fun diag(msg: String) = AppLogger.d("Diag.Controller", "controller=$controllerId player=${avPlayer.hashCode()} $msg")

    private fun ByteArray.toNSData(): NSData = usePinned {
        NSData.create(bytes = it.addressOf(0), length = size.toULong())
    }

    val avPlayer: AVQueuePlayer = AVQueuePlayer()
    private var progressTimer: NSTimer? = null
    private var released = false
    // Set to true while addItemItems() is replacing the queue so the timer does
    // not misread the momentarily-empty queue as a stream-ended condition.
    private var isLoadingItems = false
    private var lastEmittedPlaybackState: PlaybackState? = null
    private var authHeaders: Map<String, String> = emptyMap()
    private var prefetchClient: HttpClient? = null

    private fun emitPlaybackStateIfChanged(
        next: PlaybackState,
        sink: (PlaybackState) -> Unit
    ) {
        if (lastEmittedPlaybackState != next) {
            lastEmittedPlaybackState = next
            sink(next)
        }
    }

    private fun ensurePrefetchClient(): HttpClient {
        val existing = prefetchClient
        if (existing != null) return existing
        return HttpClient(Darwin) {
            // Manifest prefetch runs on the Main thread on iOS — a hard timeout prevents
            // the UI from freezing if the server is slow or unreachable.
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis  = 10_000
            }
        }.also { prefetchClient = it }
    }

    actual fun setAuthHeaders(headers: Map<String, String>) {
        authHeaders = headers
    }

    /**
     * No-op on iOS — AVFoundation / HLS handles AES-128 key delivery natively
     * via the EXT-X-KEY URI in the playlist.  The key is fetched by AVFoundation
     * using the same HTTP headers injected via [setAuthHeaders].
     */
    actual fun setAesKey(key: ByteArray) {
        // No-op: AVFoundation decrypts HLS segments transparently.
    }

    actual suspend fun prefetchForPlayback(mediaItem: PlaybackMediaItem): PlaybackMediaItem {
        var manifestUrl = mediaItem.hlsStreamUrl.ifBlank { mediaItem.streamUrl }
        // Force HLS variant for iOS AVPlayer
        manifestUrl = manifestUrl.replace("/manifest/dash/", "/manifest/hls/")

        AppLogger.i("iOS.Prefetch", "▶ prefetchForPlayback mediaId=${mediaItem.id}")
        AppLogger.i("iOS.Prefetch", "  resolvedManifestUrl=$manifestUrl")

        if (!(manifestUrl.startsWith("http://") || manifestUrl.startsWith("https://"))) {
            AppLogger.d("iOS.Prefetch", "  → local/file URL, skipping prefetch")
            return mediaItem
        }

        val sessionToken = authHeaders["X-Session-Token"]
        AppLogger.i("iOS.Prefetch", "  X-Session-Token present=${sessionToken != null}")

        if (sessionToken == null) {
            AppLogger.e("iOS.Prefetch", "  ✗ No X-Session-Token — cannot validate manifest", null)
            return mediaItem
        }

        val authorizationHeader = authHeaders["Authorization"]
        val client = ensurePrefetchClient()

        return runCatching {
            AppLogger.i("iOS.Prefetch", "  → GET $manifestUrl (validation only)")
            val response = client.get(manifestUrl) {
                header("X-Session-Token", sessionToken)
                if (authorizationHeader != null) header("Authorization", authorizationHeader)
            }
            AppLogger.i("iOS.Prefetch", "  ← HTTP ${response.status.value} ${response.status.description}")

            if (response.status == HttpStatusCode.Unauthorized) {
                com.vditital.data.security.SessionRevokedBus.emit()
            }

            if (response.status != HttpStatusCode.OK) {
                AppLogger.e("iOS.Prefetch",
                    "  ✗ Manifest validation failed: HTTP ${response.status.value} for $manifestUrl", null)
                return@runCatching mediaItem
            }

            val manifest = response.bodyAsText()
            AppLogger.i("iOS.Prefetch", "  manifest size=${manifest.length} chars")
            AppLogger.d("iOS.Prefetch", "  manifest preview:\n${manifest.take(400)}")

            if (!manifest.trimStart().startsWith("#EXTM3U")) {
                AppLogger.e("iOS.Prefetch",
                    "  ✗ Not a valid M3U8 — server returned: ${manifest.take(200)}", null)
                return@runCatching mediaItem
            }

            // ── Key change ────────────────────────────────────────────────────────
            // Return the corrected HLS http:// URL directly.
            // Previously we wrote to a file:// temp path, but AVFoundation does NOT
            // propagate AVURLAssetHTTPHeaderFieldsKey when crossing from file:// to
            // http://, causing variant playlist requests to be blocked silently.
            // Giving AVPlayer the http:// URL directly means the X-Session-Token
            // header (set via AVURLAssetHTTPHeaderFieldsKey in addItem()) is included
            // in the master-manifest request, and downstream requests authenticate
            // via the ?sid=&t= query params already embedded in variant/segment URLs.
            AppLogger.i("iOS.Prefetch", "  ✓ Manifest valid — passing HTTP URL to AVPlayer: $manifestUrl")
            mediaItem.copy(hlsStreamUrl = manifestUrl, streamUrl = manifestUrl)
        }
            .onFailure { e ->
                AppLogger.e("iOS.Prefetch",
                    "  ✗ prefetchForPlayback exception: ${e.message}", e)
            }
            .getOrDefault(mediaItem)
    }

    /**
     * Build an NSURL from a media item's streamUrl.
     */
    private fun nsUrlFor(streamUrl: String): NSURL? = when {
        streamUrl.startsWith("file://") -> NSURL.URLWithString(streamUrl)
        streamUrl.startsWith("/")       -> NSURL.fileURLWithPath(streamUrl)
        else                            -> NSURL.URLWithString(streamUrl)
    }

    actual fun addItem(mediaItem: PlaybackMediaItem) {
        val resolved = mediaItem.hlsStreamUrl.ifBlank { mediaItem.streamUrl }
            .replace("/manifest/dash/", "/manifest/hls/")
            
        AppLogger.i("iOS.Player", "addItem mediaId=${mediaItem.id}")
        AppLogger.i("iOS.Player", "  hlsStreamUrl=${mediaItem.hlsStreamUrl}")
        AppLogger.i("iOS.Player", "  streamUrl=${mediaItem.streamUrl}")
        AppLogger.i("iOS.Player", "  → resolved URL: $resolved")

        if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
            AppLogger.i("iOS.Player",
                "  → Loading HLS from HTTP URL. X-Session-Token injected via AVURLAssetHTTPHeaderFieldsKey.")
        }

        val url = nsUrlFor(resolved)
        if (url == null) {
            AppLogger.e("iOS.Player", "  ✗ nsUrlFor returned null for: $resolved", null)
            return
        }
        AppLogger.i("iOS.Player", "  → AVPlayerItem(url=$resolved) with headers")
        val options: Map<Any?, *> = mapOf("AVURLAssetHTTPHeaderFieldsKey" to authHeaders)
        val asset = platform.AVFoundation.AVURLAsset(uRL = url, options = options)
        val playerItem = AVPlayerItem(asset)
        avPlayer.insertItem(playerItem, afterItem = null)
        AppLogger.i("iOS.Player", "  ✓ item inserted, queue size=${avPlayer.items().size}")
        diag("addItem media=${mediaItem.id} queue=${avPlayer.items().size}")
    }

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
        // Reset released flag so Ended/Playing state emits correctly on re-entry
        // (release() sets this true on navigation-away; initPlayer() is always called
        //  when PlaybackView re-enters so it's safe to clear here).
        released = false
        timerTick = 0
        lastEmittedPlaybackState = null
        diag("initPlayer released=$released")

        progressTimer?.invalidate()
        progressTimer = null
        AppLogger.i("iOS.Player", "initPlayer — attaching AVQueuePlayer progress timer")

        val timer = NSTimer.timerWithTimeInterval(0.5, repeats = true) {
            timerTick += 1
            val pos = currentPosition()
            val dur = duration()
            callback(pos, dur)
            if (timerTick % 10 == 0) {
                diag("tick=$timerTick pos=$pos dur=$dur queue=${avPlayer.items().size} rate=${avPlayer.rate}")
            }

            val item = avPlayer.currentItem

            // ── Check AVPlayerItem-level error (e.g. 401, file not found, decode fail) ──
            val itemErr = item?.error
            if (itemErr != null) {
                AppLogger.e("iOS.Player",
                    "AVPlayerItem error: code=${itemErr.code()} " +
                    "domain=${itemErr.domain()} " +
                    "desc=${itemErr.localizedDescription()}", null)
                val desc = itemErr.localizedDescription()?.lowercase() ?: ""
                if (desc.contains("401") || desc.contains("unauthorized")) {
                    com.vditital.data.security.SessionRevokedBus.emit()
                        emitPlaybackStateIfChanged(PlaybackState.SessionExpired, playbackState)
                } else {
                        emitPlaybackStateIfChanged(
                            PlaybackState.Error("Playback error ${itemErr.code()}: ${itemErr.localizedDescription()}"),
                            playbackState
                        )
                }
                return@timerWithTimeInterval
            }
            // ── Check AVPlayerItem failed status (item may have .status==failed but .error==null) ──
            if (item?.status == AVPlayerItemStatusFailed) {
                val statusErr = item?.error
                val desc = statusErr?.localizedDescription() ?: "item status is failed"
                AppLogger.e("iOS.Player", "AVPlayerItem status failed: $desc", null)
                emitPlaybackStateIfChanged(Error("Playback failed: $desc"), playbackState)
                return@timerWithTimeInterval
            }
            // ── Check AVPlayer-level error ────────────────────────────────────────────
            val playerErr = avPlayer.error
            if (playerErr != null) {
                AppLogger.e("iOS.Player",
                    "AVPlayer error: code=${playerErr.code()} " +
                    "domain=${playerErr.domain()} " +
                    "desc=${playerErr.localizedDescription()}", null)
                emitPlaybackStateIfChanged(
                    Error("Player error ${playerErr.code()}: ${playerErr.localizedDescription()}"),
                    playbackState
                )
                return@timerWithTimeInterval
            }

            when {
                isPlaying() ->
                    emitPlaybackStateIfChanged(playerState(), playbackState)
                // Guard: if we're in the middle of swapping the queue
                // (removeAllItems → addItem), don't emit Ended prematurely.
                item == null && !released && !isLoadingItems ->
                    emitPlaybackStateIfChanged(PlaybackState.Ended, playbackState)
                item?.isPlaybackBufferEmpty() == true ->
                    emitPlaybackStateIfChanged(Buffering, playbackState)
                else ->
                    // Not playing, not empty, no error yet — still buffering
                    emitPlaybackStateIfChanged(Buffering, playbackState)
            }
        }
        NSRunLoop.mainRunLoop.addTimer(timer, forMode = NSRunLoopCommonModes)
        progressTimer = timer
    }

    actual fun pause(playbackState: (PlaybackState) -> Unit) {
        (avPlayer as AVPlayer).pause()
        diag("pause")
        emitPlaybackStateIfChanged(Paused, playbackState)
    }

    actual fun release() {
        if (released) return
        diag("release start queue=${avPlayer.items().size}")
        released = true
        lastEmittedPlaybackState = null
        prefetchClient?.close()
        prefetchClient = null
        (avPlayer as AVPlayer).pause()
        progressTimer?.invalidate()
        progressTimer = null
        avPlayer.removeAllItems()
        diag("release done queue=${avPlayer.items().size}")
    }

    actual fun resume() { avPlayer.play() }

    actual fun isPlaying(): Boolean =
        avPlayer.rate.toLong().toInt() != 0 && avPlayer.error == null

    actual fun duration(): Long {
        val sec = avPlayer.currentItem?.let { CMTimeGetSeconds(it.duration) }
        return if (sec != null && !sec.isNaN() && sec > 0) (sec * 1000).toLong() else 0L
    }

    actual fun currentPosition(): Long {
        val sec = avPlayer.currentItem?.let { CMTimeGetSeconds(it.currentTime()) }
        return if (sec != null && !sec.isNaN()) (sec * 1000).toLong() else 0L
    }

    actual fun seekTo(position: Long) {
        diag("seekTo position=$position")
        avPlayer.seekToTime(CMTimeMake(position, 1000))
    }

    actual fun play(playbackState: (PlaybackState) -> Unit) {
        avPlayer.play()
        diag("play")
        emitPlaybackStateIfChanged(Playing, playbackState)
    }

    actual fun addItemItems(items: List<PlaybackMediaItem>) {
        // Guard the timer against misreading the briefly-empty queue as stream-ended.
        isLoadingItems = true
        avPlayer.removeAllItems()
        items.forEach { addItem(it) }
        isLoadingItems = false
    }

    actual fun downloadDashManifest(playbackItem: PlaybackMediaItem) {}

    actual fun setQuality(quality: PlaybackQuality) {
        // AVFoundation selects the optimal HLS variant automatically based on bandwidth.
        // preferredPeakBitRate hints the adaptive algorithm toward a specific tier;
        // 0.0 restores fully-automatic selection.
        when (quality) {
            PlaybackQuality.Auto   -> avPlayer.currentItem?.preferredPeakBitRate = 0.0
            PlaybackQuality.Q360p  -> avPlayer.currentItem?.preferredPeakBitRate = 800_000.0
            PlaybackQuality.Q480p  -> avPlayer.currentItem?.preferredPeakBitRate = 1_500_000.0
            PlaybackQuality.Q720p  -> avPlayer.currentItem?.preferredPeakBitRate = 3_000_000.0
            PlaybackQuality.Q1080p -> avPlayer.currentItem?.preferredPeakBitRate = 8_000_000.0
            PlaybackQuality.Q1440p -> avPlayer.currentItem?.preferredPeakBitRate = 16_000_000.0
            PlaybackQuality.Q4K    -> avPlayer.currentItem?.preferredPeakBitRate = 40_000_000.0
        }
    }

    private fun playerState(): PlaybackState {
        val item = avPlayer.currentItem
        return when {
            item?.isPlaybackLikelyToKeepUp() == true || item?.isPlaybackBufferFull() == true -> Playing
            item?.isPlaybackBufferEmpty() == true -> Buffering
            avPlayer.error != null -> Error(avPlayer.error!!.code().toString())
            else -> Buffering
        }
    }
}
