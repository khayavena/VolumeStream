package com.vdigital.volumestream.repository

import com.vdigital.volumestream.model.DataModel
import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.repository.state.MediaItemDataState
import com.vdigital.volumestream.repository.state.ResultState

interface PlaybackMediaItemRepository {
    suspend fun fetchMediaItems(): MediaItemDataState
    suspend fun getMediaItemsState(): ResultState<MutableList<PlaybackMediaItem>>
    suspend fun getMediaItemsByCategoryState(): ResultState<Map<String, MutableList<PlaybackMediaItem>>>
    suspend fun fetchDataModel(): DataModel
}