package com.vditital.data.repository

import com.vditital.data.repository.state.MediaItemDataState
import com.vditital.data.datasource.RemotePlaybackDataSource
import com.vditital.data.model.DataModel
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.state.ResultState

class PlaybackMediaItemRepositoryImpl(private val dataSource: RemotePlaybackDataSource) :
    PlaybackMediaItemRepository {

    override suspend fun fetchMediaItems(): MediaItemDataState {
        return try {
            MediaItemDataState.Success(dataSource.fetchData())
        } catch (e: Exception) {
            MediaItemDataState.Failure(e)
        }
    }

    override suspend fun getMediaItemsState(): ResultState<MutableList<PlaybackMediaItem>> {
        return try {
            ResultState.Success(dataSource.fetchData())
        } catch (e: Exception) {
            ResultState.Error(e)
        }
    }

    override suspend fun getMediaItemsByCategoryState(): ResultState<Map<String, MutableList<PlaybackMediaItem>>> {
        return try {
            ResultState.Success(dataSource.fetchFeed())
        } catch (e: Exception) {
            ResultState.Error(e)
        }
    }

    override suspend fun fetchDataModel(): DataModel {
        return dataSource.fetchDataModel()
    }

}