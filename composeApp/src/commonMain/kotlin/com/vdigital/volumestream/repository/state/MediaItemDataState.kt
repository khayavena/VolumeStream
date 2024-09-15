package com.vdigital.volumestream.repository.state

import com.vdigital.volumestream.model.PlaybackMediaItem

sealed class MediaItemDataState {
    data object Loading : MediaItemDataState()
    data class Success(val data: MutableList<PlaybackMediaItem>) : MediaItemDataState()
    data class Failure(val error: Throwable) : MediaItemDataState()
}
