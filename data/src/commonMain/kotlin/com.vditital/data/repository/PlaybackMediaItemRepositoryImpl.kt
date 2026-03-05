package com.vditital.data.repository

import com.vditital.data.repository.state.MediaItemDataState
import com.vditital.data.datasource.RemotePlaybackDataSource
import com.vditital.data.model.DataModel
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.state.ResultState

class PlaybackMediaItemRepositoryImpl(private val dataSource: RemotePlaybackDataSource) :
    PlaybackMediaItemRepository {
    private val remoteDataSourceData = mutableListOf(
        PlaybackMediaItem(
            "1",
            "Big Buck Bunny",
            isDownloaded = false,
            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            artworkUrl = "https://cdn.dstv.com/dstvcms/2024/07/19/E36B_1079965_PP_med.jpg"
        ),
        PlaybackMediaItem(
            "2",
            "Elephant Dream",
            isDownloaded = false,
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            artworkUrl = "https://cdn.dstv.com/dstvcms/2024/06/19/IS20_1070794_PP_med.jpg"
        ),
        PlaybackMediaItem(
            "3",
            "For Bigger Blazes",
            isDownloaded = false,
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            artworkUrl = "https://cdn.dstv.com/dstvcms/2024/09/13/E36B_1080099_PP_med.jpg"
        ),
        PlaybackMediaItem(
            "4",
            "For Bigger Escapes",
            isDownloaded = false,
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            artworkUrl = "https://cdn.dstv.com/dstvcms/2024/09/04/E36B_1078979_PP_med.jpg"
        ),
        PlaybackMediaItem(
            "5",
            "Subaru Outback",
            isDownloaded = false,
            "https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            artworkUrl = "https://cdn.dstv.com/dstvcms/2024/08/30/E36B_1069995_PP_med.jpg"
        ),
        PlaybackMediaItem(
            "6",
            "Bad Boys: Ride or Die",
            isDownloaded = false,
            "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            artworkUrl = "https://cdn.dstv.com/mms.dstv.com/content/images/dstv/boxoffice/untitled%20folder%205/badboysrideordie_pp.jpg"
        )
    )

    override suspend fun fetchMediaItems(): MediaItemDataState {
        return MediaItemDataState.Success(remoteDataSourceData)
    }

    override suspend fun getMediaItemsState(): ResultState<MutableList<PlaybackMediaItem>> {
        return ResultState.Success(remoteDataSourceData)
    }

    override suspend fun getMediaItemsByCategoryState(): ResultState<Map<String, MutableList<PlaybackMediaItem>>> {
        val moviesByCategory = mapOf(
            "Action" to remoteDataSourceData,
            "Drama" to remoteDataSourceData,
            "Comedy" to remoteDataSourceData
        )
        return ResultState.Success(moviesByCategory)
    }

    override suspend fun fetchDataModel(): DataModel {
        val data = dataSource.fetchDataModel()
        return data
    }

}