package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.state.ResultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomaPageViewModel(
    private val playbackMediaItemRepository: PlaybackMediaItemRepository
) : ViewModel() {

    private val homeDataState =
        MutableStateFlow<ResultState<Map<String, MutableList<PlaybackMediaItem>>>>(ResultState.Loading)
    val homeDataUIState = homeDataState.asStateFlow()

    fun fetchData() {
        viewModelScope.launch {
            println("[HomeVM] fetchData called")
            try {
                val result = withContext(Dispatchers.IO) {
                    playbackMediaItemRepository.getMediaItemsByCategoryState()
                }
                println("[HomeVM] result: $result")
                homeDataState.value = result
            } catch (e: Throwable) {
                println("[HomeVM] uncaught error: $e")
                homeDataState.value = ResultState.Error(Exception(e))
            }
        }
    }
}
