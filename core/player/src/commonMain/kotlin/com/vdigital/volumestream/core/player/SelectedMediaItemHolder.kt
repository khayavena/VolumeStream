package com.vdigital.volumestream.core.player

import com.vditital.data.model.PlaybackMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared singleton that carries the media item the user tapped on in
 * feature:home so that feature:playback can pick it up without either
 * module depending on the other.
 */
class SelectedMediaItemHolder {

    private val _selectedItem = MutableStateFlow<PlaybackMediaItem?>(null)

    /** Observe the currently selected item from any module. */
    val selectedItem: StateFlow<PlaybackMediaItem?> = _selectedItem.asStateFlow()

    /** Store the item the user just chose. Called from feature:home. */
    fun select(item: PlaybackMediaItem) {
        _selectedItem.value = item
    }

    /** Convenience for non-reactive reads (e.g. inside PlaybackViewModel.init). */
    fun current(): PlaybackMediaItem? = _selectedItem.value
}
