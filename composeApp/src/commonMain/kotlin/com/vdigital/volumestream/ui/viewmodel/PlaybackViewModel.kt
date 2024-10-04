package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.platform.controller.PlaybackStateController
import com.vdigital.volumestream.platform.enum.OsType
import com.vdigital.volumestream.repository.PlaybackMediaItemRepository
import com.vdigital.volumestream.ui.viewmodel.state.PlaybackState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class PlaybackViewModel(
    private val playbackStateController: PlaybackStateController,
    val osType: OsType,
) :
    ViewModel() {
    private val _playBackState = MutableStateFlow<PlaybackState>(PlaybackState.bufferig)
    private val _progressState = MutableStateFlow(0F)
    val playBackStateUI = _playBackState.asStateFlow()
    val progressStateUI = _progressState.asStateFlow()

    fun getPlatformController(): PlaybackStateController {
        return playbackStateController
    }

    fun initialise() {
        viewModelScope.launch {
            handleStartPlayback(mutableListOf(currentMediaItem))
        }
    }

    private fun handleStartPlayback(playbackMediaItems: MutableList<PlaybackMediaItem>) {
        try {
            playbackStateController.initPlayer({ currentPosition, duration ->
                _progressState.value =
                    if (duration > 0) currentPosition
                        .toFloat() / duration else 0f
            }, playbackState = {
                _playBackState.value = it
            })
            playbackStateController.addItemItems(playbackMediaItems)
        } catch (e: Exception) {
            e.printStackTrace()
            _playBackState.value = PlaybackState.error("Exception was thrown.")
        }
    }

    fun onSeekChanged(seekValue: Float) {
        viewModelScope.launch {
            playbackStateController.seekTo((seekValue * playbackStateController.duration()).toLong())
            _progressState.value = seekValue
        }
    }


    fun playPause() {
        viewModelScope.launch {
            if (_playBackState.value == PlaybackState.playing) {
                playbackStateController.pause(
                    playbackState = {
                        _playBackState.value = it
                    }
                )
            } else {
                playbackStateController.play(playbackState = {
                    _playBackState.value = it
                })
            }
        }
    }

    override fun onCleared() {
        viewModelScope.cancel()
        super.onCleared()
    }

  companion object{
      private lateinit var currentMediaItem: PlaybackMediaItem
      fun setSelectedItem(mediaItem: PlaybackMediaItem) {
          currentMediaItem = mediaItem
      }
  }
}