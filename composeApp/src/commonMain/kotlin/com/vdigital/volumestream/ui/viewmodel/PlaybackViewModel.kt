package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.platform.enum.OsType
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
    val osType: OsType,
) : ViewModel() {

    private val _playBackState = MutableStateFlow<PlaybackState>(PlaybackState.Buffering)
    private val _progressState = MutableStateFlow(0F)
    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _trackList = MutableStateFlow<List<PlaybackMediaItem>>(emptyList())
    private val _selectedTrackId = MutableStateFlow<String?>(null)

    val playBackStateUI = _playBackState.asStateFlow()
    val progressStateUI = _progressState.asStateFlow()
    val positionMsUI = _positionMs.asStateFlow()
    val durationMsUI = _durationMs.asStateFlow()
    val trackListUI = _trackList.asStateFlow()
    val selectedTrackIdUI = _selectedTrackId.asStateFlow()

    fun getPlatformController(): PlaybackStateController = playbackStateController

    fun initialise() {
        viewModelScope.launch {
            loadTrackList()
            handleStartPlayback(mutableListOf(currentMediaItem))
        }
    }

    private suspend fun loadTrackList() {
        when (val result = playbackMediaItemRepository.getMediaItemsState()) {
            is ResultState.Success -> _trackList.value = result.data
            else -> _trackList.value = listOf(currentMediaItem)
        }
    }

    private fun handleStartPlayback(playbackMediaItems: MutableList<PlaybackMediaItem>) {
        try {
            playbackStateController.initPlayer({ currentPosition, duration ->
                _positionMs.value = currentPosition
                _durationMs.value = duration
                _progressState.value =
                    if (duration > 0) currentPosition.toFloat() / duration else 0f
            }, playbackState = {
                _playBackState.value = it
            })
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
            // Ensure playback resumes from the new position regardless of prior state
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
        currentMediaItem = item
        handleStartPlayback(mutableListOf(item))
    }

    override fun onCleared() {
        viewModelScope.cancel()
        super.onCleared()
    }

    companion object {
        private lateinit var currentMediaItem: PlaybackMediaItem
        fun setSelectedItem(mediaItem: PlaybackMediaItem) {
            currentMediaItem = mediaItem
        }
    }
}