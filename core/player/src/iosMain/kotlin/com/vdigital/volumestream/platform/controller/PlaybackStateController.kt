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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVURLAsset
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

    // Keep iOS Auto on a stable 720p-equivalent ceiling to avoid aggressive jumps to 1080p
    // that can cause video decode stalls while audio continues.
    private val autoPeakBitRate = 3_000_000.0

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
    private var endObserver: Any? = null
    private var didReachEnd = false

    // authHeaders is written by setAuthHeaders() in the ViewModel coroutine, always before a
    // withContext(Dispatchers.IO) boundary, so coroutine happens-before semantics guarantee
    // the IO thread sees the update — no @Volatile annotation required.
    private var authHeaders: Map<String, String> = emptyMap()
    private var prefetchClient: HttpClient? = null
    /** Debug map to correlate AVPlayerItem failures with the exact source URL. */
    private val itemSourceByHash = mutableMapOf<Int, String>()
    /** Tracks temp manifest files written by persistManifestToTempFile so they can be deleted on release(). */
    private val tempManifestPaths = mutableListOf<String>()

    /**
     * Scope used to dispatch background work (file cleanup on release) off the Main thread.
     * Cancelled once in release() so pending jobs don't outlive the controller.
     */
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        applyPlayerBufferingPreferences()
    }

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
            // Hard timeouts prevent runaway requests from holding the IO dispatcher too long.
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis  = 10_000
            }
        }.also { prefetchClient = it }
    }

    private fun clearEndObserver() {
        val observer = endObserver
        if (observer != null) {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
        endObserver = null
    }

    actual fun setAuthHeaders(headers: Map<String, String>) {
        authHeaders = headers
    }

    private fun applyPlayerBufferingPreferences() {
        val key = "automaticallyWaitsToMinimizeStalling"
        val selector = NSSelectorFromString("setAutomaticallyWaitsToMinimizeStalling:")
        if (avPlayer.respondsToSelector(selector)) {
            avPlayer.setValue(
                NSNumber.numberWithBool(playerConfig.iosAutomaticallyWaitsToMinimizeStalling),
                forKey = key
            )
        }
    }

    private fun applyBufferingPreferences(playerItem: AVPlayerItem) {
        val key = "preferredForwardBufferDuration"
        val selector = NSSelectorFromString("setPreferredForwardBufferDuration:")
        if (playerItem.respondsToSelector(selector)) {
            playerItem.setValue(
                NSNumber.numberWithDouble(playerConfig.iosPreferredForwardBufferSeconds.coerceAtLeast(0.0)),
                forKey = key
            )
        }
    }

    private suspend fun persistManifestToTempFile(mediaId: String, manifest: String): String {
        val tempDir = NSTemporaryDirectory()
        check(!tempDir.isNullOrBlank()) {
            "NSTemporaryDirectory returned a blank path"
        }

        // Use a unique filename per prefetch to avoid AVFoundation reusing a stale cached
        // local manifest URL that still points at an old session token.
        val uniqueSuffix = NSUUID().UUIDString.lowercase()
        val filePath = "${tempDir.trimEnd('/')}/streamvault-${mediaId}-$controllerId-$uniqueSuffix.m3u8"
        val manifestData = manifest.encodeToByteArray().toNSData()

        // NSData.writeToFile is synchronous blocking I/O — dispatch to IO to keep Main free.
        withContext(Dispatchers.IO) {
            check(manifestData.writeToFile(filePath, true)) {
                "Failed to write prefetched HLS manifest to $filePath"
            }
        }

        // Guard against duplicates — same mediaId replayed on same controller (Koin single scope)
        // would otherwise add the same path twice and trigger a double-delete warning on release().
        if (!tempManifestPaths.contains(filePath)) {
            tempManifestPaths.add(filePath)
        }
        return filePath
    }

    /**
     * No-op on iOS — AVFoundation handles HLS AES-128 key delivery natively
     * via the signed EXT-X-KEY URI emitted by the backend playlists.
     */
    actual fun setAesKey(key: ByteArray) {
        // No-op: AVFoundation decrypts HLS segments transparently.
    }

    actual suspend fun prefetchForPlayback(mediaItem: PlaybackMediaItem): PlaybackMediaItem {
        var manifestUrl = mediaItem.hlsStreamUrl.ifBlank { mediaItem.streamUrl }
        // StreamVault now advertises the canonical user-scoped HLS manifest via
        // /api/v1/manifest/{mediaId}. If a DASH URL slips through as a fallback,
        // rewrite it to the HLS/user-manifest path expected by AVPlayer prefetch.
        manifestUrl = manifestUrl.replace("/manifest/dash/", "/manifest/")

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

            AppLogger.i("iOS.Prefetch", "  ✓ Manifest valid — using remote URL for AVPlayer")
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

    /**
     * Builds an [AVPlayerItem] for [mediaItem] without queuing it.
     * Returns null and logs an error if the URL cannot be resolved.
     */
    private fun buildPlayerItem(mediaItem: PlaybackMediaItem): AVPlayerItem? {
        val resolved = mediaItem.hlsStreamUrl.ifBlank { mediaItem.streamUrl }
            .replace("/manifest/dash/", "/manifest/")

        AppLogger.i("iOS.Player", "buildPlayerItem mediaId=${mediaItem.id}")
        AppLogger.i("iOS.Player", "  hlsStreamUrl=${mediaItem.hlsStreamUrl}")
        AppLogger.i("iOS.Player", "  streamUrl=${mediaItem.streamUrl}")
        AppLogger.i("iOS.Player", "  → resolved URL: $resolved")

        val isRemoteUrl = resolved.startsWith("http://") || resolved.startsWith("https://")
        if (!isRemoteUrl) {
            AppLogger.i("iOS.Player", "  → Loading local manifest/file URL")
        } else {
            AppLogger.i("iOS.Player", "  → Loading remote manifest URL with AVURLAsset headers")
        }

        val url = nsUrlFor(resolved)
        if (url == null) {
            AppLogger.e("iOS.Player", "  ✗ nsUrlFor returned null for: $resolved", null)
            return null
        }
        val headerFields = NSMutableDictionary()
        authHeaders["X-Session-Token"]?.let { headerFields.setValue(it, forKey = "X-Session-Token") }
        authHeaders["Authorization"]?.let { headerFields.setValue(it, forKey = "Authorization") }
        val assetOptions: Map<Any?, Any?>? = if (headerFields.count.toInt() > 0) {
            // Keep headers available even when the root URL is local file:// because
            // HLS child resources (keys/segments) can still be remote and session-protected.
            mapOf("AVURLAssetHTTPHeaderFieldsKey" to headerFields)
        } else {
            null
        }
        AppLogger.i("iOS.Player", "  → AVURLAsset(url=$resolved, hasHeaders=${assetOptions != null}, remote=$isRemoteUrl)")
        val asset = AVURLAsset(uRL = url, options = assetOptions)
        val playerItem = AVPlayerItem(asset)
        // Apply default Auto cap on newly created items; manual quality changes can override it.
        playerItem.preferredPeakBitRate = autoPeakBitRate
        itemSourceByHash[playerItem.hashCode()] = resolved
        applyBufferingPreferences(playerItem)
        AppLogger.i(
            "iOS.Player",
            "  buffering waitToMinimizeStalling=${playerConfig.iosAutomaticallyWaitsToMinimizeStalling} " +
                "forwardBuffer=${playerConfig.iosPreferredForwardBufferSeconds}s"
        )
        return playerItem
    }

    actual fun addItem(mediaItem: PlaybackMediaItem) {
        val playerItem = buildPlayerItem(mediaItem) ?: return
        avPlayer.insertItem(playerItem, afterItem = null)
        AppLogger.i("iOS.Player", "  ✓ item appended to queue, size=${avPlayer.items().size}")
        diag("addItem media=${mediaItem.id} queue=${avPlayer.items().size}")
    }

    actual fun initPlayer(callback: (Long, Long) -> Unit, playbackState: (PlaybackState) -> Unit) {
        // Reset released flag so Ended/Playing state emits correctly on re-entry
        // (release() sets this true on navigation-away; initPlayer() is always called
        //  when PlaybackView re-enters so it's safe to clear here).
        released = false
        timerTick = 0
        lastEmittedPlaybackState = null
        didReachEnd = false
        applyPlayerBufferingPreferences()
        diag("initPlayer released=$released")

        clearEndObserver()
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = null
        ) { notification ->
            val endedItem = notification?.`object` as? AVPlayerItem
            if (endedItem != null && endedItem != avPlayer.currentItem) return@addObserverForName
            didReachEnd = true
            emitPlaybackStateIfChanged(PlaybackState.Ended, playbackState)
            diag("observed didPlayToEnd item=${endedItem?.hashCode()}")
        }

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
                val failedSource = item?.let { itemSourceByHash[it.hashCode()] }
                AppLogger.e("iOS.Player",
                    "AVPlayerItem error: code=${itemErr.code()} " +
                    "domain=${itemErr.domain()} " +
                    "desc=${itemErr.localizedDescription()} " +
                    "source=${failedSource ?: "<unknown>"}", null)
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
                val failedSource = item?.let { itemSourceByHash[it.hashCode()] }
                AppLogger.e("iOS.Player",
                    "AVPlayerItem status failed: $desc source=${failedSource ?: "<unknown>"}", null)
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
                didReachEnd && !released && !isLoadingItems ->
                    emitPlaybackStateIfChanged(PlaybackState.Ended, playbackState)
                isPlaying() ->
                    emitPlaybackStateIfChanged(playerState(), playbackState)
                // Guard: if we're in the middle of swapping the queue
                // (removeAllItems → addItem), don't emit Ended prematurely.
                item == null && !released && !isLoadingItems ->
                    emitPlaybackStateIfChanged(PlaybackState.Ended, playbackState)
                item?.isPlaybackBufferEmpty() == true ->
                    emitPlaybackStateIfChanged(Buffering, playbackState)
                // User-paused media that is otherwise ready should remain Paused,
                // not Buffering, so controls/state stay stable between timer ticks.
                avPlayer.rate == 0.0f && item != null &&
                    (item.isPlaybackLikelyToKeepUp() == true || item.isPlaybackBufferFull() == true) ->
                    emitPlaybackStateIfChanged(Paused, playbackState)
                else ->
                    // Not playing, not paused-ready, no error yet — still buffering.
                    emitPlaybackStateIfChanged(Buffering, playbackState)
            }
        }
        NSRunLoop.mainRunLoop.addTimer(timer, forMode = NSRunLoopCommonModes)
        progressTimer = timer
    }

    actual fun pause(playbackState: (PlaybackState) -> Unit) {
        avPlayer.pause()
        diag("pause")
        emitPlaybackStateIfChanged(Paused, playbackState)
    }

    actual fun release() {
        if (released) return
        diag("release start queue=${avPlayer.items().size}")
        released = true
        didReachEnd = false
        lastEmittedPlaybackState = null
        clearEndObserver()
        prefetchClient?.close()
        prefetchClient = null
        avPlayer.pause()
        progressTimer?.invalidate()
        progressTimer = null
        avPlayer.removeAllItems()
        // Dispatch temp file cleanup to IO — NSFileManager.removeItemAtPath is blocking I/O.
        // Snapshot the paths list before launching so the closure captures a safe copy.
        val pathsToDelete = tempManifestPaths.toList()
        tempManifestPaths.clear()
        itemSourceByHash.clear()
        controllerScope.launch(Dispatchers.IO) {
            val fileManager = NSFileManager.defaultManager
            pathsToDelete.forEach { path ->
                runCatching {
                    fileManager.removeItemAtPath(path, error = null)
                }.onFailure {
                    AppLogger.w("iOS.Player", "release: failed to delete temp manifest $path: ${it.message}")
                }
            }
        }
        controllerScope.cancel()
        diag("release done queue=${avPlayer.items().size}")
    }

    actual fun resume() { avPlayer.play() }

    actual fun isPlaying(): Boolean =
        // Compare as Float — toLong() truncates 0.999f to 0, falsely reporting not-playing.
        avPlayer.rate != 0.0f && avPlayer.error == null

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
        // Use zero tolerance (CMTimeMake(0,1) = 0 seconds) so AVFoundation seeks to
        // the exact millisecond requested rather than snapping to the nearest HLS
        // segment keyframe boundary (which can be ±3 s on 6-second segments with the
        // default tolerances).
        val zeroTolerance = CMTimeMake(0, 1)
        avPlayer.seekToTime(
            CMTimeMake(position, 1000),
            toleranceBefore = zeroTolerance,
            toleranceAfter  = zeroTolerance
        )
    }

    actual fun play(playbackState: (PlaybackState) -> Unit) {
        avPlayer.play()
        diag("play")
        emitPlaybackStateIfChanged(Playing, playbackState)
    }

    actual fun addItemItems(items: List<PlaybackMediaItem>) {
        if (items.isEmpty()) {
            AppLogger.w("iOS.Player", "addItemItems called with empty list — no-op to avoid spurious Ended state")
            return
        }
        isLoadingItems = true
        didReachEnd = false

        // Build the first AVPlayerItem up-front so queue replacement can happen immediately.
        val firstPlayerItem = buildPlayerItem(items.first())
        if (firstPlayerItem == null) {
            AppLogger.e("iOS.Player", "addItemItems: buildPlayerItem returned null for first item — aborting", null)
            isLoadingItems = false
            return
        }

        // Kotlin/Native AVFoundation bindings in this project do not expose
        // replaceCurrentItemWithPlayerItem(_:) on AVQueuePlayer, so perform an explicit clear+insert.
        avPlayer.removeAllItems()
        avPlayer.insertItem(firstPlayerItem, afterItem = null)
        AppLogger.i("iOS.Player",
            "addItemItems: queue replaced via removeAllItems+insertItem queue=${avPlayer.items().size}")

        // Append any additional items (track-queue support).
        for (i in 1 until items.size) {
            addItem(items[i])
        }

        isLoadingItems = false
        diag("addItemItems count=${items.size} queue=${avPlayer.items().size}")
    }

    actual fun downloadDashManifest(playbackItem: PlaybackMediaItem) {}

    actual fun setQuality(quality: PlaybackQuality) {
        // AVFoundation selects the optimal HLS variant automatically based on bandwidth.
        // preferredPeakBitRate hints the adaptive algorithm toward a specific tier;
        // 0.0 restores fully-automatic selection.
        when (quality) {
            PlaybackQuality.Auto   -> avPlayer.currentItem?.preferredPeakBitRate = autoPeakBitRate
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
