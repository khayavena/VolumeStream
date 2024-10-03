package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.repository.PlaybackMediaItemRepository
import com.vdigital.volumestream.repository.state.ResultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomaPageViewModel(private val playbackMediaItemRepository: PlaybackMediaItemRepository) :
    ViewModel() {
    private val homeDataState =
        MutableStateFlow<ResultState<Map<String, MutableList<PlaybackMediaItem>>>>(ResultState.Loading)
    val homeDataUIState = homeDataState.asStateFlow()
    fun fetchData() {
        viewModelScope.launch {
            val data = playbackMediaItemRepository.getMediaItemsByCategoryState()
            homeDataState.value = data
        }
    }

}