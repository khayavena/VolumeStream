package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.core.player.download.DownloadController
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.platform.enum.OsType
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackQuality
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.AuthRepository
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.SessionRepository
import com.vditital.data.repository.state.ResultState
import com.vditital.data.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackViewModel(
    private val playbackStateController: PlaybackStateController,
    private val playbackMediaItemRepository: PlaybackMediaItemRepository,
    private val selectedMediaItemHolder: SelectedMediaItemHolder,
    val osType: OsType,
    private val downloadController: DownloadController,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val vmId = hashCode()
    private fun diag(msg: String) = AppLogger.d("Diag.VM", "vm=$vmId controller=${playbackStateController.hashCode()} $msg")

    private fun runPlayerAction(
        action: String,
        onFailure: (() -> Unit)? = null,
        block: () -> Unit
    ) {
        diag("player_action start=$action")
        try {
            block()
            diag("player_action ok=$action")
        } catch (t: Throwable) {
            diag("player_action fail=$action err=${t.message}")
            AppLogger.e("PlaybackVM", "Player action failed: $action", t as? Exception ?: Exception(t))
            onFailure?.invoke()
        }
    }

    private val _playBackState   = MutableStateFlow<PlaybackState>(PlaybackState.Buffering)
    private val _progressState   = MutableStateFlow(0F)
    private val _durationMs      = MutableStateFlow(0L)
    private val _trackList       = MutableStateFlow<List<PlaybackMediaItem>>(emptyList())
    private val _selectedTrackId = MutableStateFlow<String?>(null)
    private val _quality         = MutableStateFlow<PlaybackQuality>(PlaybackQuality.Auto)

    val playBackStateUI   = _playBackState.asStateFlow()
    val progressStateUI   = _progressState.asStateFlow()
    val durationMsUI      = _durationMs.asStateFlow()
    val trackListUI       = _trackList.asStateFlow()
    val selectedTrackIdUI = _selectedTrackId.asStateFlow()
    val qualityUI         = _quality.asStateFlow()


    /** Re-entry guard: prevents a second initialise() while the first is still in flight. */
    private var initialiseJob: Job? = null
    /** Latest-track-wins guard: prevents stale async selectTrack completions from overriding newer taps. */
    private var selectTrackJob: Job? = null
    /** Guards against ultra-fast duplicate play/pause taps (seen mostly on iOS overlays). */
    private var playPauseToggleLocked = false
    private var trackSelectionVersion: Long = 0L
    private var recentlyWatchedTickerJob: Job? = null
    private var activeRecentlyWatchedMediaId: String? = null
    private var lastSyncedPlaybackPositionMs: Long = -1L

    companion object {
        /** Header names used by StreamVault. Centralised here so SDK consumers can
         *  override them without touching the player layer. */
        const val HEADER_AUTHORIZATION  = "Authorization"
        const val HEADER_SESSION_TOKEN  = "X-Session-Token"
        const val RECENTLY_WATCHED_SYNC_INTERVAL_MS = 60_000L
    }

    fun getPlatformController(): PlaybackStateController = playbackStateController

    fun initialise() {
        diag("initialise called")
        // Prevent duplicate initialisations (e.g. from a LaunchedEffect re-fire).
        if (initialiseJob?.isActive == true) {
            diag("initialise skipped reason=job_active")
            return
        }
        // No explicit dispatcher — viewModelScope already uses Dispatchers.Main.immediate,
        // which starts the coroutine body synchronously when called from Main, avoiding
        // an unnecessary queue-posting cycle before the first UI state update.
        initialiseJob = viewModelScope.launch {
            try {
                loadTrackList()
                val item = selectedMediaItemHolder.current()
                if (item == null) {
                    diag("initialise exit reason=no_selected_item")
                    AppLogger.e("PlaybackVM", "initialise aborted: no selected media item", null)
                    _playBackState.value = PlaybackState.Error("No media selected.")
                    return@launch
                }
                diag("initialise media=${item.id} os=${osType.name}")
                AppLogger.i("PlaybackVM", "initialise mediaId=${item.id} platform=${osType.name}")

                val localPlaybackItem = withContext(Dispatchers.IO) { resolveLocalPlaybackItem(item) }
                if (localPlaybackItem != null) {
                    diag("initialise local_source media=${item.id}")
                    AppLogger.i("PlaybackVM", "Local playback source detected for mediaId=${item.id}; preparing best-effort auth/session headers")
                    val localHeaders = withContext(Dispatchers.IO) { prepareBestEffortPlaybackHeaders(item.id) }
                    playbackStateController.setAuthHeaders(localHeaders)
                    handleInitialPlayback(mutableListOf(localPlaybackItem))
                    return@launch
                }
                if (item.isDownloaded) {
                    diag("initialise downloaded_missing_local media=${item.id}")
                    AppLogger.e("PlaybackVM", "Downloaded item has no local source: mediaId=${item.id}. Blocking API fallback.", null)
                    _playBackState.value = PlaybackState.Error("Downloaded file is missing. Please re-download.")
                    return@launch
                }

                // 1. Ensure we have a valid (non-expired) JWT
                val jwt = withContext(Dispatchers.IO) { authRepository.ensureValidJwt() }
                if (jwt == null) {
                    diag("initialise exit reason=no_jwt media=${item.id}")
                    AppLogger.e("PlaybackVM",
                        "JWT unavailable — user is not logged in or token refresh failed. " +
                        "Check auth-pulse-service is running on the configured auth port.", null)
                    _playBackState.value = PlaybackState.Error("Auth failed: no JWT in store. Please log in.")
                    return@launch
                }
                diag("initialise jwt_ready media=${item.id}")
                AppLogger.d("PlaybackVM", "JWT obtained (len=${jwt.length})")

                // 2. Ensure device is registered (RSA-2048 public key on the server).
                //    Must complete BEFORE startSession, which requires a valid cert signature.
                //    HomaPageViewModel also fires this but it is fire-and-forget; we do it here
                //    too so playback is never blocked by a missed registration.
                AppLogger.d("PlaybackVM", "ensureDeviceRegistered for mediaId=${item.id}")
                val regResult = withContext(Dispatchers.IO) {
                    sessionRepository.ensureDeviceRegistered()
                }
                if (regResult is ResultState.Error) {
                    AppLogger.w("PlaybackVM",
                        "Device registration failed: ${regResult.exception.message}. " +
                        "Session start may fail with 403 if device was never registered.")
                } else {
                    AppLogger.d("PlaybackVM", "Device registration OK")
                }

                // 3. Start a cert-pinned playback session
                AppLogger.i("PlaybackVM", "startSession mediaId=${item.id}")
                var sessionToken: String? = null
                val sessionResult = withContext(Dispatchers.IO) {
                    sessionRepository.startSession(jwt, item.id)
                }
                if (sessionResult is ResultState.Success) {
                    sessionToken    = sessionResult.data.sessionToken
                    diag("session started id=${sessionResult.data.sessionId} media=${item.id}")
                    AppLogger.i("PlaybackVM",
                        "Session started: sessionId=${sessionResult.data.sessionId} mediaId=${item.id}")
                    val playbackJwt = withContext(Dispatchers.IO) { latestJwtOrFallback(jwt) }
                    // 4. Inject headers into the player's HTTP layer BEFORE
                    //    initPlayer() builds ExoPlayer / AVPlayer.
                    playbackStateController.setAuthHeaders(
                        mapOf(
                            HEADER_AUTHORIZATION to "Bearer $playbackJwt",
                            HEADER_SESSION_TOKEN to sessionResult.data.sessionToken
                        )
                    )
                    // 5. Fetch + inject the 16-byte AES-128 key for DASH segment decryption.
                    //    iOS SKIPPED: AVFoundation handles HLS AES-128 natively via EXT-X-KEY.
                    if (osType != OsType.IOS) {
                        val keyResult = withContext(Dispatchers.IO) {
                            sessionRepository.fetchAesKey(
                                mediaId      = item.id,
                                sessionId    = sessionResult.data.sessionId,
                                sessionToken = sessionResult.data.sessionToken
                            )
                        }
                        if (keyResult is ResultState.Success) {
                            playbackStateController.setAesKey(keyResult.data)
                            AppLogger.d("PlaybackVM", "AES key injected (${keyResult.data.size} bytes)")
                        } else {
                            AppLogger.w("PlaybackVM", "AES key unavailable — encrypted DASH may not play")
                        }
                    } else {
                        AppLogger.d("PlaybackVM", "iOS: skipping AES key fetch — AVFoundation handles EXT-X-KEY")
                    }
                } else {
                    diag("session start failed media=${item.id}")
                    val cause = (sessionResult as? ResultState.Error)?.exception?.message ?: "unknown"
                    AppLogger.e("PlaybackVM",
                        "Session start failed — cause='$cause'. " +
                        "Check: (1) device is registered, (2) cert signature is valid, " +
                        "(3) POST /api/v1/session/start returns 200 in server logs.", null)
                }

                if (sessionToken == null) {
                    diag("initialise exit reason=no_session_token media=${item.id}")
                    AppLogger.e("PlaybackVM",
                        "Playback blocked: no session token. " +
                        "platform=${osType.name} mediaId=${item.id}", null)
                    val cause = (sessionResult as? ResultState.Error)?.exception?.message ?: "unknown"
                    _playBackState.value = PlaybackState.Error("Session failed: $cause")
                    return@launch
                }

                val localPath = withContext(Dispatchers.IO) { downloadController.getLocalPath(item.id) }
                val useLocalCandidate = localPath != null && !(osType == OsType.IOS && localPath.trim().lowercase().endsWith(".m3u8"))
                val candidate = if (useLocalCandidate) item.copy(streamUrl = localPath!!, hlsStreamUrl = localPath) else item
                AppLogger.d("PlaybackVM", "Prefetching item for ${osType.name}: mediaId=${item.id}")
                // Always prefetch on IO: Ktor Darwin uses NSURLSession (async) so the network call
                // never blocks the Main thread, and the IO dispatcher ensures any synchronous file
                // I/O (manifest writeToFile) is also off Main.  Coroutine happens-before semantics
                // guarantee the IO thread sees the auth headers written by setAuthHeaders() above.
                val playItem = withContext(Dispatchers.IO) {
                    playbackStateController.prefetchForPlayback(candidate)
                }
                AppLogger.d("PlaybackVM", "Prefetch complete for mediaId=${item.id}")
                diag("prefetch complete media=${item.id} start_player=true")
                handleInitialPlayback(mutableListOf(playItem))
                startRecentlyWatchedTicker(item.id)
            } catch (e: CancellationException) {
                // Propagate coroutine cancellation — do NOT treat it as a playback error.
                // This fires when onCleared() cancels viewModelScope mid-initialisation.
                diag("initialise cancelled")
                throw e
            } catch (e: Throwable) {
                diag("initialise exception=${e.message}")
                AppLogger.e("PlaybackVM", "Playback initialisation failed", e)
                _playBackState.value = PlaybackState.Error("Playback initialisation failed.")
            }
        }
    }

    private suspend fun loadTrackList() {
        val result = withContext(Dispatchers.IO) {
            playbackMediaItemRepository.getMediaItemsState()
        }
        when (result) {
            is ResultState.Success -> _trackList.value = result.data
            else -> {
                val item = selectedMediaItemHolder.current()
                if (item != null) _trackList.value = listOf(item)
            }
        }
    }

    private fun handleInitialPlayback(playbackMediaItems: MutableList<PlaybackMediaItem>) {
        try {
            diag("handleInitialPlayback items=${playbackMediaItems.size}")
            // Reset to Buffering immediately.  The previous session's timer may have emitted
            // PlaybackState.Ended while initialise() was running its IO chain (JWT, session,
            // prefetch), which starts a 600 ms auto-back countdown in PlaybackView's
            // LaunchedEffect.  Setting Buffering here changes the LaunchedEffect key, cancelling
            // that countdown before the new item is even inserted into the player.
            _playBackState.value = PlaybackState.Buffering
             // initPlayer builds a brand-new ExoPlayer / AVPlayer and attaches it to
             // the surface owned by PlatformMediaPlayerView.  Call this ONLY once — on
             // first launch.  For subsequent track changes use handleTrackSwitch().
             runPlayerAction(
                 action = "handleInitialPlayback.initPlayer",
                 onFailure = { _playBackState.value = PlaybackState.Error("Player initialisation failed.") }
             ) {
                 playbackStateController.initPlayer({ currentPosition, duration ->
                     _durationMs.value    = duration
                     _progressState.value = if (duration > 0) currentPosition.toFloat() / duration else 0f
                 }, playbackState = { _playBackState.value = it })
             }
            // Queue items AFTER initPlayer: Android initPlayer() clears ExoPlayer items.
            // Adding media first causes the startup item to be wiped before playback starts.
            runPlayerAction(action = "handleInitialPlayback.addItems") {
                playbackStateController.addItemItems(playbackMediaItems)
            }
            runPlayerAction(
                action = "handleInitialPlayback.play",
                onFailure = { _playBackState.value = PlaybackState.Error("Playback start failed.") }
            ) {
                playbackStateController.play(playbackState = { _playBackState.value = it })
            }
        } catch (e: Throwable) {
            AppLogger.e("PlaybackVM", "Player initialisation error", e)
            _playBackState.value = PlaybackState.Error("Exception was thrown.")
        }
    }

    /**
     * Switches to a new track **without** releasing or rebuilding the platform player.
     *
     * Calling release() + initPlayer() for a track switch detaches the player from
     * the PlatformMediaPlayerView rendering surface (the AndroidView / UIViewRepresentable
     * factory runs only once and caches the old player reference).  Instead we reset UI
     * state, replace the media item in the existing player, seek to the start, and play.
     */
    private fun handleTrackSwitch(item: PlaybackMediaItem) {
        try {
            diag("handleTrackSwitch media=${item.id}")
            _playBackState.value = PlaybackState.Buffering
            _progressState.value = 0f
            _durationMs.value    = 0L
            runPlayerAction(action = "handleTrackSwitch.pause") {
                playbackStateController.pause(playbackState = { _playBackState.value = it })
            }
            runPlayerAction(action = "handleTrackSwitch.addItems") {
                playbackStateController.addItemItems(mutableListOf(item))
            }
            runPlayerAction(action = "handleTrackSwitch.seekToStart") {
                playbackStateController.seekTo(0L)
            }
            runPlayerAction(
                action = "handleTrackSwitch.play",
                onFailure = { _playBackState.value = PlaybackState.Error("Track switch failed.") }
            ) {
                playbackStateController.play(playbackState = { _playBackState.value = it })
            }
        } catch (e: Throwable) {
            AppLogger.e("PlaybackVM", "Track switch error", e)
            _playBackState.value = PlaybackState.Error("Track switch failed.")
        }
    }

    // These player calls are all synchronous and must run on Main (player APIs are
    // Main-thread-bound). No coroutine wrapper needed — calling directly is correct
    // and avoids unnecessary coroutine allocations + event-loop round-trips.

    fun onSeekChanged(seekValue: Float) {
        runPlayerAction(
            action = "onSeekChanged",
            onFailure = { _playBackState.value = PlaybackState.Error("Seek failed.") }
        ) {
            val targetMs = (seekValue * playbackStateController.duration()).toLong()
            // Emit Buffering immediately so the UI reflects the seek rather than
            // flashing Playing→Buffering→Playing when the buffer hasn't loaded yet.
            _playBackState.value = PlaybackState.Buffering
            _progressState.value = seekValue
            playbackStateController.seekTo(targetMs)
            // Resume play after seek — the timer will transition to Playing once
            // AVFoundation reports isPlaybackLikelyToKeepUp = true at the new position.
            playbackStateController.resume()
        }
    }

    fun playPause() {
        runPlayerAction(
            action = "playPause",
            onFailure = { _playBackState.value = PlaybackState.Error("Play/pause failed.") }
        ) {
            if (playPauseToggleLocked) return@runPlayerAction
            playPauseToggleLocked = true
            viewModelScope.launch {
                delay(220)
                playPauseToggleLocked = false
            }

            // Use the platform player's real-time state to avoid stale UI-state races
            // from timer-based playback updates (seen most on iOS AVPlayer).
            if (playbackStateController.isPlaying()) {
                playbackStateController.pause(playbackState = { _playBackState.value = it })
            } else {
                playbackStateController.play(playbackState = { _playBackState.value = it })
            }
        }
    }

    fun skipForward() {
        runPlayerAction(action = "skipForward") {
            val dur    = playbackStateController.duration()
            val target = (playbackStateController.currentPosition() + 10_000L).coerceAtMost(dur)
            playbackStateController.seekTo(target)
            // Immediately reflect the new position so the seek bar jumps to the
            // correct spot without waiting for the next timer tick.
            if (dur > 0) _progressState.value = target.toFloat() / dur
        }
    }

    fun skipBackward() {
        runPlayerAction(action = "skipBackward") {
            val dur    = playbackStateController.duration()
            val target = (playbackStateController.currentPosition() - 10_000L).coerceAtLeast(0L)
            playbackStateController.seekTo(target)
            if (dur > 0) _progressState.value = target.toFloat() / dur
        }
    }

    fun selectTrack(item: PlaybackMediaItem) {
        diag("selectTrack called media=${item.id}")
        _selectedTrackId.value = item.id
        selectedMediaItemHolder.select(item)

        // Cancel any in-flight switch; only the latest user selection should apply.
        selectTrackJob?.cancel()
        val selectionVersion = ++trackSelectionVersion

        selectTrackJob = viewModelScope.launch { // uses Dispatchers.Main.immediate from viewModelScope
            try {
                val localPlaybackItem = withContext(Dispatchers.IO) { resolveLocalPlaybackItem(item) }
                if (localPlaybackItem != null) {
                    diag("selectTrack local_source media=${item.id}")
                    AppLogger.i("PlaybackVM", "Local playback source detected for track ${item.id}; preparing best-effort auth/session headers")
                    if (selectionVersion != trackSelectionVersion) return@launch
                    val localHeaders = withContext(Dispatchers.IO) { prepareBestEffortPlaybackHeaders(item.id) }
                    playbackStateController.setAuthHeaders(localHeaders)
                    handleTrackSwitch(localPlaybackItem)
                    return@launch
                }
                if (item.isDownloaded) {
                    diag("selectTrack downloaded_missing_local media=${item.id}")
                    AppLogger.e("PlaybackVM", "Downloaded track has no local source: mediaId=${item.id}. Blocking API fallback.", null)
                    _playBackState.value = PlaybackState.Error("Downloaded file is missing. Please re-download.")
                    return@launch
                }

                val jwt = withContext(Dispatchers.IO) { authRepository.ensureValidJwt() }
                if (jwt == null) {
                    diag("selectTrack exit reason=no_jwt media=${item.id}")
                    AppLogger.e("PlaybackVM", "Track switch blocked: JWT unavailable", null)
                    _playBackState.value = PlaybackState.Error("Not authenticated. Please log in.")
                    return@launch
                }


                // Ensure device is registered before starting the new session
                val regResult = withContext(Dispatchers.IO) { sessionRepository.ensureDeviceRegistered() }
                if (regResult is ResultState.Error) {
                    AppLogger.w("PlaybackVM", "Device re-registration failed: ${regResult.exception.message}")
                }

                var sessionToken: String? = null
                val sessionResult = withContext(Dispatchers.IO) {
                    sessionRepository.startSession(jwt, item.id)
                }
                if (sessionResult is ResultState.Success) {
                    sessionToken    = sessionResult.data.sessionToken
                    diag("selectTrack session_started id=${sessionResult.data.sessionId} media=${item.id}")
                    AppLogger.i("PlaybackVM", "Track switch session: ${sessionResult.data.sessionId} mediaId=${item.id}")
                    if (selectionVersion != trackSelectionVersion) return@launch
                    val playbackJwt = withContext(Dispatchers.IO) { latestJwtOrFallback(jwt) }
                    playbackStateController.setAuthHeaders(
                        mapOf(
                            HEADER_AUTHORIZATION to "Bearer $playbackJwt",
                            HEADER_SESSION_TOKEN to sessionResult.data.sessionToken
                        )
                    )
                    // 5. Fetch + inject AES key for DASH segment decryption.
                    //    iOS SKIPPED: AVFoundation handles HLS AES-128 natively via EXT-X-KEY.
                    if (osType != OsType.IOS) {
                        val keyResult = withContext(Dispatchers.IO) {
                            sessionRepository.fetchAesKey(
                                mediaId      = item.id,
                                sessionId    = sessionResult.data.sessionId,
                                sessionToken = sessionResult.data.sessionToken
                            )
                        }
                        if (keyResult is ResultState.Success) {
                            playbackStateController.setAesKey(keyResult.data)
                            AppLogger.d("PlaybackVM", "AES key injected for track ${item.id}")
                        } else {
                            AppLogger.w("PlaybackVM", "AES key unavailable for track ${item.id}")
                        }
                    } else {
                        AppLogger.d("PlaybackVM", "iOS: skipping AES key fetch for track switch — AVFoundation handles EXT-X-KEY")
                    }
                } else {
                    diag("selectTrack session_start_failed media=${item.id}")
                    val cause = (sessionResult as? ResultState.Error)?.exception?.message ?: "unknown"
                    AppLogger.e("PlaybackVM", "Track switch session start failed: $cause", null)
                }

                if (sessionToken == null) {
                    diag("selectTrack exit reason=no_session_token media=${item.id}")
                    AppLogger.e("PlaybackVM", "iOS track switch blocked: missing session token", null)
                    _playBackState.value = PlaybackState.Error("Playback session unavailable.")
                    return@launch
                }

                if (selectionVersion != trackSelectionVersion) return@launch
                val localPath = withContext(Dispatchers.IO) { downloadController.getLocalPath(item.id) }
                val useLocalCandidate = localPath != null && !(osType == OsType.IOS && localPath.trim().lowercase().endsWith(".m3u8"))
                val candidate = if (useLocalCandidate) item.copy(streamUrl = localPath!!, hlsStreamUrl = localPath) else item
                // Always prefetch on IO — see initialise() comment above.
                val playItem = withContext(Dispatchers.IO) {
                    playbackStateController.prefetchForPlayback(candidate)
                }

                // Switch tracks in-place — keeps the platform player attached to its
                // rendering surface.  release() + initPlayer() would detach the player
                // from the AndroidView / UIViewRepresentable and produce a blank frame.
                if (selectionVersion != trackSelectionVersion) return@launch
                handleTrackSwitch(playItem)
                startRecentlyWatchedTicker(item.id)
            } catch (_: CancellationException) {
                diag("selectTrack cancelled media=${item.id}")
            } catch (e: Throwable) {
                diag("selectTrack exception=${e.message}")
                AppLogger.e("PlaybackVM", "Track selection failed", e)
                _playBackState.value = PlaybackState.Error("Track selection failed.")
            }
        }
    }

    fun setQuality(q: PlaybackQuality) {
        runPlayerAction(action = "setQuality:$q") {
            _quality.value = q
            playbackStateController.setQuality(q)
        }
    }

    private suspend fun resolveLocalPlaybackItem(item: PlaybackMediaItem): PlaybackMediaItem? {
        val localPath = downloadController.getLocalPath(item.id)
        if (localPath != null) {
            if (osType == OsType.IOS && localPath.trim().lowercase().endsWith(".m3u8")) {
                AppLogger.i("PlaybackVM", "iOS downloaded HLS detected for ${item.id}; using remote manifest playback path")
                return null
            }
            return item.copy(isDownloaded = true, streamUrl = localPath, hlsStreamUrl = localPath)
        }

        val current = item.hlsStreamUrl.ifBlank { item.streamUrl }
        return if (isLocalPath(current)) {
            item.copy(isDownloaded = true, streamUrl = current, hlsStreamUrl = current)
        } else {
            null
        }
    }

    /**
     * Downloaded iOS playback may still fetch remote segment/key URLs from a local manifest.
     * Prepare session headers when possible, but never block local playback if auth bootstrap fails.
     */
    private suspend fun prepareBestEffortPlaybackHeaders(mediaId: String): Map<String, String> {
        val jwt = authRepository.ensureValidJwt()
        if (jwt.isNullOrBlank()) {
            AppLogger.w("PlaybackVM", "Local playback for $mediaId: JWT unavailable; proceeding without auth headers")
            return emptyMap()
        }

        val regResult = sessionRepository.ensureDeviceRegistered()
        if (regResult is ResultState.Error) {
            AppLogger.w("PlaybackVM", "Local playback for $mediaId: device registration failed: ${regResult.exception.message}")
        }

        val sessionResult = sessionRepository.startSession(jwt, mediaId)
        return if (sessionResult is ResultState.Success) {
            val playbackJwt = latestJwtOrFallback(jwt)
            mapOf(
                HEADER_AUTHORIZATION to "Bearer $playbackJwt",
                HEADER_SESSION_TOKEN to sessionResult.data.sessionToken
            )
        } else {
            val cause = (sessionResult as? ResultState.Error)?.exception?.message ?: "unknown"
            AppLogger.w("PlaybackVM", "Local playback for $mediaId: session start failed ($cause); proceeding without auth headers")
            emptyMap()
        }
    }

    private fun isLocalPath(path: String): Boolean {
        val p = path.trim().lowercase()
        return p.startsWith("file://") || p.startsWith("/")
    }

    private suspend fun latestJwtOrFallback(fallbackJwt: String): String =
        authRepository.ensureValidJwt()?.takeIf { it.isNotBlank() } ?: fallbackJwt

    fun onPlayerClosing() {
        viewModelScope.launch {
            syncRecentlyWatched(force = true, reason = "close")
        }
    }

    private fun startRecentlyWatchedTicker(mediaId: String) {
        activeRecentlyWatchedMediaId = mediaId
        lastSyncedPlaybackPositionMs = -1L
        recentlyWatchedTickerJob?.cancel()
        recentlyWatchedTickerJob = viewModelScope.launch {
            while (true) {
                delay(RECENTLY_WATCHED_SYNC_INTERVAL_MS)
                if (_playBackState.value != PlaybackState.Playing) continue
                syncRecentlyWatched(force = false, reason = "periodic")
            }
        }
    }

    private suspend fun syncRecentlyWatched(force: Boolean, reason: String) {
        val mediaId = activeRecentlyWatchedMediaId ?: selectedMediaItemHolder.current()?.id ?: return
        val positionMs = playbackStateController.currentPosition().coerceAtLeast(0L)
        if (!force && positionMs <= 0L) return
        if (!force && positionMs == lastSyncedPlaybackPositionMs) return

        when (val result = withContext(Dispatchers.IO) {
            sessionRepository.saveRecentlyWatched(mediaId = mediaId, playbackPosition = positionMs)
        }) {
            is ResultState.Loading -> Unit
            is ResultState.Success -> {
                lastSyncedPlaybackPositionMs = positionMs
                AppLogger.d("PlaybackVM", "recentlyWatched synced reason=$reason mediaId=$mediaId posMs=$positionMs")
            }
            is ResultState.Error -> {
                AppLogger.w(
                    "PlaybackVM",
                    "recentlyWatched sync failed reason=$reason mediaId=$mediaId: ${result.exception.message}"
                )
            }
        }
    }

    // viewModelScope is already cancelled by ViewModel.onCleared() — no need to
    // call viewModelScope.cancel() manually; doing so is redundant and can mask
    // bugs by cancelling the scope before super.onCleared() runs.
    override fun onCleared() {
        diag("onCleared release_controller=true")
        recentlyWatchedTickerJob?.cancel()
        super.onCleared()
        // Session cleanup is handled server-side via TTL / 401 revocation —
        // no client-side DELETE call needed.
        runPlayerAction(action = "onCleared.release") {
            playbackStateController.release()
        }
    }
}
