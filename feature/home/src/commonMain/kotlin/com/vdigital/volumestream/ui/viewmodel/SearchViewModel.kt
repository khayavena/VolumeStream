package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.state.ResultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(
    private val repository: PlaybackMediaItemRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** True while the initial network/cache load is in progress. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Non-null when the item load fails; null otherwise. */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _allItems = MutableStateFlow<List<PlaybackMediaItem>>(emptyList())

    /**
     * When query is blank → shows the full catalogue (explore mode).
     * When query is non-blank → filters by title + description.
     */
    val searchResults: StateFlow<List<PlaybackMediaItem>> =
        combine(_query, _allItems) { q, items ->
            if (q.isBlank()) items
            else items.filter {
                it.title.contains(q, ignoreCase = true) ||
                        it.description.contains(q, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadItems()
    }

    fun updateQuery(q: String) {
        _query.value = q
    }

    fun clearQuery() {
        _query.value = ""
    }

    private fun loadItems() {
        viewModelScope.launch {
            _isLoading.value = true
            _loadError.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getMediaItemsState()
                }
                when (result) {
                    is ResultState.Success -> _allItems.value = result.data
                    is ResultState.Error   -> _loadError.value =
                        result.exception.message ?: "Failed to load content"
                    else -> Unit
                }
            } catch (e: Exception) {
                _loadError.value = e.message ?: "An unexpected error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
