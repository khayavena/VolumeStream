package com.vdigital.volumestream.repository

import com.vdigital.volumestream.repository.state.MediaItemDataState

interface PlaybackMediaItemRepository {
    suspend fun fetchMediaItems(): MediaItemDataState
}