package com.vditital.data.datasource

import com.vditital.data.model.DataModel
import com.vditital.data.model.PlaybackMediaItem


interface RemotePlaybackDataSource {
    suspend fun fetchData(): MutableList<PlaybackMediaItem>
    suspend fun fetchFeed(): Map<String, MutableList<PlaybackMediaItem>>
    suspend fun fetchDataModel(): DataModel
}
