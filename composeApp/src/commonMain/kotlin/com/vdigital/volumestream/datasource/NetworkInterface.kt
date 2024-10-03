package com.vdigital.volumestream.datasource

import com.vdigital.volumestream.model.DataModel
import com.vdigital.volumestream.model.PlaybackMediaItem


interface RemotePlaybackDataSource {
    suspend fun fetchData(): MutableList<PlaybackMediaItem>
    suspend fun fetchDataModel(): DataModel
}
