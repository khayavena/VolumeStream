package com.vditital.data.repository.state

import com.vditital.data.model.PlaybackMediaItem

sealed class MediaItemDataState {
    data object Loading : MediaItemDataState()
    data class Success(val data: MutableList<PlaybackMediaItem>) : MediaItemDataState()
    data class Failure(val error: Throwable) : MediaItemDataState()
}
