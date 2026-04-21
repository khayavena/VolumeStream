package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.repository.PlaybackMediaItemRepository
import com.vditital.data.repository.state.ResultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: PlaybackMediaItemRepository,
) : ViewModel() {

    private data class IndexedMediaItem(
        val item: PlaybackMediaItem,
        val searchableText: String,
    )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** True while the initial network/cache load is in progress. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Non-null when the item load fails; null otherwise. */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _indexedItems = MutableStateFlow<List<IndexedMediaItem>>(emptyList())

    /**
     * When query is blank → shows the full catalogue (explore mode).
     * When query is non-blank → filters by title + description.
     */
    val searchResults: StateFlow<List<PlaybackMediaItem>> =
        _query
            .debounce(150)
            .distinctUntilChanged()
            .combine(_indexedItems) { q, indexed -> q to indexed }
            .mapLatest { (q, indexed) ->
                val query = q.trim().lowercase()
                if (query.isBlank()) {
                    indexed.map { it.item }
                } else {
                    withContext(Dispatchers.Default) {
                        indexed
                            .asSequence()
                            .filter { it.searchableText.contains(query) }
                            .map { it.item }
                            .toList()
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                val result = withContext(Dispatchers.Default) {
                    repository.getMediaItemsState()
                }
                when (result) {
                    is ResultState.Success -> {
                        _indexedItems.value = result.data.map { mediaItem ->
                            IndexedMediaItem(
                                item = mediaItem,
                                searchableText = buildString {
                                    append(mediaItem.title)
                                    append(' ')
                                    append(mediaItem.description)
                                }.lowercase()
                            )
                        }
                    }
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
