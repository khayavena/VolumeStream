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
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.state.ResultState
import com.vditital.data.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackViewModel(
    private val playbackStateController: PlaybackStateController,
    private val playbackMediaItemRepository: PlaybackMediaItemRepository,
    private val selectedMediaItemHolder: SelectedMediaItemHolder,
    val osType: OsType,
    private val downloadController: DownloadController,
) : ViewModel() {

    private val _playBackState   = MutableStateFlow<PlaybackState>(PlaybackState.Buffering)
    private val _progressState   = MutableStateFlow(0F)
    private val _positionMs      = MutableStateFlow(0L)
    private val _durationMs      = MutableStateFlow(0L)
    private val _trackList       = MutableStateFlow<List<PlaybackMediaItem>>(emptyList())
    private val _selectedTrackId = MutableStateFlow<String?>(null)
    private val _quality         = MutableStateFlow<PlaybackQuality>(PlaybackQuality.Auto)

    val playBackStateUI   = _playBackState.asStateFlow()
    val progressStateUI   = _progressState.asStateFlow()
    val positionMsUI      = _positionMs.asStateFlow()
    val durationMsUI      = _durationMs.asStateFlow()
    val trackListUI       = _trackList.asStateFlow()
    val selectedTrackIdUI = _selectedTrackId.asStateFlow()
    val qualityUI         = _quality.asStateFlow()

    fun getPlatformController(): PlaybackStateController = playbackStateController

    fun initialise() {
        // Always launch on Main — AVFoundation (initPlayer, addItemItems, play) must
        // be called on the Main thread. We switch to IO only for blocking reads.
        viewModelScope.launch(Dispatchers.Main) {
            try {
                loadTrackList()
                val item = selectedMediaItemHolder.current() ?: return@launch
                // getLocalPath reads NSUserDefaults — dispatch to IO, then return to Main.
                val localPath = withContext(Dispatchers.IO) { downloadController.getLocalPath(item.id) }
                val playItem = if (localPath != null) item.copy(streamUrl = localPath) else item
                // Back on Main here — safe to call AVFoundation.
                handleStartPlayback(mutableListOf(playItem))
            } catch (e: Exception) {
                AppLogger.e("PlaybackVM", "Playback initialisation failed", e)
                _playBackState.value = PlaybackState.Error("Playback initialisation failed.")
            }
        }
    }

    private suspend fun loadTrackList() {
        // Suspend call — repository may hit network/DB; ensure it runs off Main.
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

    private fun handleStartPlayback(playbackMediaItems: MutableList<PlaybackMediaItem>) {
        try {
            playbackStateController.initPlayer({ currentPosition, duration ->
                _positionMs.value = currentPosition
                _durationMs.value = duration
                _progressState.value =
                    if (duration > 0) currentPosition.toFloat() / duration else 0f
            }, playbackState = { _playBackState.value = it })
            playbackStateController.addItemItems(playbackMediaItems)
            playbackStateController.play(playbackState = { _playBackState.value = it })
        } catch (e: Exception) {
            AppLogger.e("PlaybackVM", "Player initialisation error", e)
            _playBackState.value = PlaybackState.Error("Exception was thrown.")
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
        // Read local path on IO, then switch back to Main before touching AVFoundation.
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val localPath = withContext(Dispatchers.IO) { downloadController.getLocalPath(item.id) }
                val playItem = if (localPath != null) item.copy(streamUrl = localPath) else item
                // handleStartPlayback calls AVFoundation APIs — must stay on Main.
                handleStartPlayback(mutableListOf(playItem))
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
    }
}
