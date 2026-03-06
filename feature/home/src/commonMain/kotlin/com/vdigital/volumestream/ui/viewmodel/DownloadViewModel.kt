package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.core.player.download.DownloadController
import com.vdigital.volumestream.core.player.download.DownloadItem
import com.vdigital.volumestream.core.player.download.DownloadState
import com.vditital.data.model.PlaybackMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadViewModel(
    private val downloadController: DownloadController,
    private val selectedMediaItemHolder: SelectedMediaItemHolder
) : ViewModel() {

    private val _allDownloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val allDownloads: StateFlow<List<DownloadItem>> = _allDownloads.asStateFlow()

    /**
     * Loads the persisted download list off the Main thread to avoid disk I/O
     * (SharedPreferences / NSUserDefaults scan) on the UI thread.
     */
    fun refreshDownloads() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { downloadController.listDownloads() }
            _allDownloads.value = items
        }
    }

    fun download(item: PlaybackMediaItem) {
        val url = item.downloadUrl.ifBlank { item.streamUrl }
        viewModelScope.launch {
            // enqueueUniqueWork() is a Binder IPC call — must not run on Main.
            withContext(Dispatchers.IO) {
                downloadController.download(item.id, url, item.title, item.artworkUrl)
            }
            // After enqueue, wait off-Main for completion then refresh.
            downloadController.observeState(item.id)
                .filter { it == DownloadState.Completed }
                .first()
            refreshDownloads()
        }
    }

    /**
     * Cancels an in-progress download off the Main thread (WorkManager Binder IPC +
     * SharedPreferences write must not block UI).
     */
    fun cancel(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadController.cancel(id)
        }
    }

    /**
     * Deletes the downloaded file and removes metadata off the Main thread to avoid
     * file-system I/O blocking the UI thread.
     */
    fun remove(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { downloadController.remove(id) }
            refreshDownloads()
        }
    }

    fun observeState(id: String): StateFlow<DownloadState> =
        downloadController.observeState(id)
            .stateIn(
                scope          = viewModelScope,
                started        = SharingStarted.WhileSubscribed(5_000),
                // Seed with Completed immediately if already downloaded so the UI
                // never flickers back to Idle while waiting for the first emission
                // from WorkManager (Android) or NSUserDefaults (iOS).
                initialValue   = if (downloadController.isDownloaded(id)) DownloadState.Completed
                                 else DownloadState.Idle
            )

    /**
     * Checks whether an item is already downloaded. The underlying read is lightweight
     * (in-memory prefs cache on Android; NSUserDefaults on iOS) but is still called
     * from Compose so we keep the result observable via [observeState] instead of
     * reading synchronously here where possible.
     */
    fun isDownloaded(id: String): Boolean = downloadController.isDownloaded(id)

    fun getLocalPath(id: String): String? = downloadController.getLocalPath(id)

    /** Select a downloaded item for playback via the shared holder. */
    fun selectForPlayback(item: DownloadItem) {
        val localPath = item.localPath ?: return
        selectedMediaItemHolder.select(
            PlaybackMediaItem(
                id         = item.id,
                title      = item.title,
                streamUrl  = localPath,
                artworkUrl = item.artworkUrl
            )
        )
    }
}

