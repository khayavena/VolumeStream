package com.vdigital.volumestream.repository

import com.vdigital.volumestream.model.PlaybackMediaItem
import com.vdigital.volumestream.repository.state.MediaItemDataState

class PlaybackMediaItemRepositoryImpl : PlaybackMediaItemRepository {
    private val remoteDataSourceData = mutableListOf(
        PlaybackMediaItem(
            "1",
            "Sample Video 1",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        PlaybackMediaItem(
            "2",
            "Sample Video 2",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        )
    )

    override suspend fun fetchMediaItems(): MediaItemDataState {
        return MediaItemDataState.Success(remoteDataSourceData)
    }

}