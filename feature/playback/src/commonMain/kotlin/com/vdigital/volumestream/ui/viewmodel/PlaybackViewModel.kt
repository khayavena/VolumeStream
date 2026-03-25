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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class PlaybackViewModel(
    private val playbackStateController: PlaybackStateController,
    private val playbackMediaItemRepository: PlaybackMediaItemRepository,
    private val selectedMediaItemHolder: SelectedMediaItemHolder,
    val osType: OsType,
    private val downloadController: DownloadController,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

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

    /** Holds the active session ID so we can revoke it when the ViewModel is cleared. */
    private var activeSessionId: String? = null
    private var activeJwt: String? = null

    /** Re-entry guard: prevents a second initialise() while the first is still in flight. */
    private var initialiseJob: Job? = null

    /** Separate scope for fire-and-forget cleanup after viewModelScope is cancelled. */
    private val cleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        /** Header names used by StreamVault. Centralised here so SDK consumers can
         *  override them without touching the player layer. */
        const val HEADER_AUTHORIZATION  = "Authorization"
        const val HEADER_SESSION_TOKEN  = "X-Session-Token"
    }

    fun getPlatformController(): PlaybackStateController = playbackStateController

    fun initialise() {
        // Prevent duplicate initialisations (e.g. from a LaunchedEffect re-fire).
        if (initialiseJob?.isActive == true) return
        initialiseJob = viewModelScope.launch(Dispatchers.Main) {
            try {
                loadTrackList()
                val item = selectedMediaItemHolder.current() ?: return@launch

                // 1. Ensure we have a valid (non-expired) JWT
                val jwt = withContext(Dispatchers.IO) { authRepository.ensureValidJwt() }

                // 2. Start a cert-pinned playback session
                if (jwt != null) {
                    val sessionResult = withContext(Dispatchers.IO) {
                        sessionRepository.startSession(jwt, item.id)
                    }
                    if (sessionResult is ResultState.Success) {
                        activeSessionId = sessionResult.data.sessionId
                        activeJwt       = jwt
                        // 3. Inject headers into the player's HTTP layer BEFORE
                        //    initPlayer() builds ExoPlayer / AVPlayer.
                        //    Header names come from this common layer — the player
                        //    SDK is completely header-agnostic.
                        playbackStateController.setAuthHeaders(
                            mapOf(
                                HEADER_AUTHORIZATION to "Bearer $jwt",
                                HEADER_SESSION_TOKEN to sessionResult.data.sessionToken
                            )
                        )
                        AppLogger.d("PlaybackVM", "Session started: ${sessionResult.data.sessionId}")
                    } else {
                        AppLogger.e("PlaybackVM", "Session start failed — playing without token", null)
                    }
                }

                val localPath = withContext(Dispatchers.IO) { downloadController.getLocalPath(item.id) }
                val playItem = if (localPath != null) item.copy(streamUrl = localPath) else item
                handleInitialPlayback(mutableListOf(playItem))
            } catch (e: Exception) {
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
            // initPlayer builds a brand-new ExoPlayer / AVPlayer and attaches it to
            // the surface owned by PlatformMediaPlayerView.  Call this ONLY once — on
            // first launch.  For subsequent track changes use handleTrackSwitch().
            playbackStateController.initPlayer({ currentPosition, duration ->
                _durationMs.value    = duration
                _progressState.value = if (duration > 0) currentPosition.toFloat() / duration else 0f
            }, playbackState = { _playBackState.value = it })
            playbackStateController.addItemItems(playbackMediaItems)
            playbackStateController.play(playbackState = { _playBackState.value = it })
        } catch (e: Exception) {
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
            _playBackState.value = PlaybackState.Buffering
            _progressState.value = 0f
            _durationMs.value    = 0L
            playbackStateController.pause(playbackState = { _playBackState.value = it })
            playbackStateController.addItemItems(mutableListOf(item))
            playbackStateController.seekTo(0L)
            playbackStateController.play(playbackState = { _playBackState.value = it })
        } catch (e: Exception) {
            AppLogger.e("PlaybackVM", "Track switch error", e)
            _playBackState.value = PlaybackState.Error("Track switch failed.")
        }
    }

    // These player calls are all synchronous and must run on Main (player APIs are
    // Main-thread-bound). No coroutine wrapper needed — calling directly is correct
    // and avoids unnecessary coroutine allocations + event-loop round-trips.

    fun onSeekChanged(seekValue: Float) {
        val targetMs = (seekValue * playbackStateController.duration()).toLong()
        playbackStateController.seekTo(targetMs)
        playbackStateController.play(playbackState = { _playBackState.value = it })
        _progressState.value = seekValue
    }

    fun playPause() {
        if (_playBackState.value == PlaybackState.Playing) {
            playbackStateController.pause(playbackState = { _playBackState.value = it })
        } else {
            playbackStateController.play(playbackState = { _playBackState.value = it })
        }
    }

    fun skipForward() {
        val target = (playbackStateController.currentPosition() + 10_000L)
            .coerceAtMost(playbackStateController.duration())
        playbackStateController.seekTo(target)
    }

    fun skipBackward() {
        val target = (playbackStateController.currentPosition() - 10_000L).coerceAtLeast(0L)
        playbackStateController.seekTo(target)
    }

    fun selectTrack(item: PlaybackMediaItem) {
        _selectedTrackId.value = item.id
        selectedMediaItemHolder.select(item)
        viewModelScope.launch(Dispatchers.Main) {
            try {
                // End the previous session — with a timeout so a slow server
                // cannot stall the track-switch indefinitely.
                val prevJwt = activeJwt
                val prevSid = activeSessionId
                if (prevJwt != null && prevSid != null) {
                    runCatching {
                        withTimeout(5_000L) {
                            withContext(Dispatchers.IO) {
                                sessionRepository.endSession(prevJwt, prevSid)
                            }
                        }
                    }
                }
                activeSessionId = null
                activeJwt = null

                val jwt = withContext(Dispatchers.IO) { authRepository.ensureValidJwt() }
                if (jwt != null) {
                    val sessionResult = withContext(Dispatchers.IO) {
                        sessionRepository.startSession(jwt, item.id)
                    }
                    if (sessionResult is ResultState.Success) {
                        activeSessionId = sessionResult.data.sessionId
                        activeJwt       = jwt
                        playbackStateController.setAuthHeaders(
                            mapOf(
                                HEADER_AUTHORIZATION to "Bearer $jwt",
                                HEADER_SESSION_TOKEN to sessionResult.data.sessionToken
                            )
                        )
                    }
                }

                val localPath = withContext(Dispatchers.IO) { downloadController.getLocalPath(item.id) }
                val playItem  = if (localPath != null) item.copy(streamUrl = localPath) else item

                // Switch tracks in-place — keeps the platform player attached to its
                // rendering surface.  release() + initPlayer() would detach the player
                // from the AndroidView / UIViewRepresentable and produce a blank frame.
                handleTrackSwitch(playItem)
            } catch (e: Exception) {
                AppLogger.e("PlaybackVM", "Track selection failed", e)
                _playBackState.value = PlaybackState.Error("Track selection failed.")
            }
        }
    }

    fun setQuality(q: PlaybackQuality) {
        _quality.value = q
        playbackStateController.setQuality(q)
    }

    // viewModelScope is already cancelled by ViewModel.onCleared() — no need to
    // call viewModelScope.cancel() manually; doing so is redundant and can mask
    // bugs by cancelling the scope before super.onCleared() runs.
    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled immediately after onCleared() — use cleanupScope
        // to guarantee the DELETE /session request is dispatched before the process exits.
        val sid = activeSessionId
        val jwt = activeJwt
        if (sid != null && jwt != null) {
            cleanupScope.launch {
                AppLogger.d("PlaybackVM", "onCleared — ending session $sid")
                try {
                    withTimeout(5_000L) { sessionRepository.endSession(jwt, sid) }
                } catch (e: Exception) {
                    AppLogger.e("PlaybackVM", "endSession timed out or failed", e)
                } finally {
                    cleanupScope.cancel()
                }
            }
        } else {
            cleanupScope.cancel()
        }
    }
}
