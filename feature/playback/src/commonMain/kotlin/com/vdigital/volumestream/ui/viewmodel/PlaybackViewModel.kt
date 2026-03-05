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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaybackViewModel(
    private val playbackStateController: PlaybackStateController,
    private val playbackMediaItemRepository: PlaybackMediaItemRepository,
    private val selectedMediaItemHolder: SelectedMediaItemHolder,
    val osType: OsType,
    private val downloadController: DownloadController,
) : ViewModel() {

    private val _playBackState  = MutableStateFlow<PlaybackState>(PlaybackState.Buffering)
    private val _progressState  = MutableStateFlow(0F)
    private val _positionMs     = MutableStateFlow(0L)
    private val _durationMs     = MutableStateFlow(0L)
    private val _trackList      = MutableStateFlow<List<PlaybackMediaItem>>(emptyList())
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
        viewModelScope.launch {
            loadTrackList()
            val item = selectedMediaItemHolder.current() ?: return@launch
            val localPath = downloadController.getLocalPath(item.id)
            val playItem = if (localPath != null) item.copy(streamUrl = localPath) else item
            handleStartPlayback(mutableListOf(playItem))
        }
    }

    private suspend fun loadTrackList() {
        when (val result = playbackMediaItemRepository.getMediaItemsState()) {
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
            e.printStackTrace()
            _playBackState.value = PlaybackState.Error("Exception was thrown.")
        }
    }

    fun onSeekChanged(seekValue: Float) {
        viewModelScope.launch {
            val targetMs = (seekValue * playbackStateController.duration()).toLong()
            playbackStateController.seekTo(targetMs)
            playbackStateController.play(playbackState = { _playBackState.value = it })
            _progressState.value = seekValue
        }
    }

    fun playPause() {
        viewModelScope.launch {
            if (_playBackState.value == PlaybackState.Playing) {
                playbackStateController.pause(playbackState = { _playBackState.value = it })
            } else {
                playbackStateController.play(playbackState = { _playBackState.value = it })
            }
        }
    }

    fun skipForward() {
        viewModelScope.launch {
            val target = (playbackStateController.currentPosition() + 10_000L)
                .coerceAtMost(playbackStateController.duration())
            playbackStateController.seekTo(target)
        }
    }

    fun skipBackward() {
        viewModelScope.launch {
            val target = (playbackStateController.currentPosition() - 10_000L).coerceAtLeast(0L)
            playbackStateController.seekTo(target)
        }
    }

    fun selectTrack(item: PlaybackMediaItem) {
        _selectedTrackId.value = item.id
        selectedMediaItemHolder.select(item)
        val localPath = downloadController.getLocalPath(item.id)
        val playItem = if (localPath != null) item.copy(streamUrl = localPath) else item
        handleStartPlayback(mutableListOf(playItem))
    }

    fun setQuality(q: PlaybackQuality) {
        _quality.value = q
        playbackStateController.setQuality(q)
    }

    override fun onCleared() {
        viewModelScope.cancel()
        super.onCleared()
    }
}
