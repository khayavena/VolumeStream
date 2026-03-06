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
import kotlinx.coroutines.flow.combine
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

    // Cache one StateFlow per item id so that every call to observeState(id)
    // from different Composables shares the same hot flow — and so that we can
    // force-push Idle into it immediately when remove() is called, without
    // waiting for WorkManager's async CANCELLED notification.
    // All access happens on the Main thread (ViewModel scope), so a plain
    // mutableMapOf is safe without synchronisation.
    private val _stateOverrides = mutableMapOf<String, MutableStateFlow<DownloadState?>>()

    private fun overrideFor(id: String): MutableStateFlow<DownloadState?> =
        _stateOverrides.getOrPut(id) { MutableStateFlow(null) }

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
        // Force the UI to Idle immediately — before WorkManager's async CANCELLED
        // event arrives — so the button never flashes back to a "tick" state.
        println("VS_DL_VM [$id] cancel() — setting override=Idle")
        overrideFor(id).value = DownloadState.Idle
        viewModelScope.launch(Dispatchers.IO) {
            downloadController.cancel(id)
        }
    }

    /**
     * Deletes the downloaded file and removes metadata off the Main thread to avoid
     * file-system I/O blocking the UI thread.
     */
    fun remove(id: String) {
        // Push Idle synchronously on the Main thread so the button updates instantly,
        // before the file deletion and WorkManager cancellation complete asynchronously.
        println("VS_DL_VM [$id] remove() — setting override=Idle")
        overrideFor(id).value = DownloadState.Idle
        viewModelScope.launch {
            withContext(Dispatchers.IO) { downloadController.remove(id) }
            refreshDownloads()
        }
    }

    fun observeState(id: String): StateFlow<DownloadState> {
        val override = overrideFor(id)
        // Combine: if an override is present use it, otherwise fall through to the
        // controller's flow.  The override is cleared back to null once the
        // underlying flow emits a stable (non-transitional) state so that fresh
        // downloads or re-downloads work normally.
        return combine(
            downloadController.observeState(id),
            override
        ) { controllerState, overrideState ->
            println("VS_DL_VM [$id] combine: controller=$controllerState  override=$overrideState")
            val result = if (overrideState != null) {
                // Clear the override only when the controller has reached a stable
                // matching state OR when the override was NOT an Idle-cancel and the
                // controller has moved on to a new active state (re-download).
                // Crucially, do NOT clear an Idle override just because WorkManager
                // briefly emits RUNNING while cancelling — that is what causes the flash.
                if (controllerState == overrideState ||
                    (overrideState != DownloadState.Idle &&
                        (controllerState is DownloadState.Queued ||
                         controllerState is DownloadState.Downloading))) {
                    println("VS_DL_VM [$id] clearing override (was $overrideState)")
                    override.value = null
                }
                overrideState
            } else {
                controllerState
            }
            println("VS_DL_VM [$id] emitting: $result")
            result
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            // Seed with the override if one is already set (e.g. remove was called
            // just before the first subscriber attached), otherwise use the
            // controller's current knowledge.
            initialValue = override.value
                ?: if (downloadController.isDownloaded(id)) DownloadState.Completed
                   else DownloadState.Idle
        )
    }

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

