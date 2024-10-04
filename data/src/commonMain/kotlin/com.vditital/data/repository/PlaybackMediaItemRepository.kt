package com.vditital.data.repository

import com.vditital.data.model.DataModel
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.state.MediaItemDataState
import com.vditital.data.repository.state.ResultState

interface PlaybackMediaItemRepository {
    suspend fun fetchMediaItems(): MediaItemDataState
    suspend fun getMediaItemsState(): ResultState<MutableList<PlaybackMediaItem>>
    suspend fun getMediaItemsByCategoryState(): ResultState<Map<String, MutableList<PlaybackMediaItem>>>
    suspend fun fetchDataModel(): DataModel
}