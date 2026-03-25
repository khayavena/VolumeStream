package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vdigital.volumestream.core.player.SelectedMediaItemHolder
import com.vdigital.volumestream.core.player.download.DownloadController
import com.vdigital.volumestream.core.player.download.DownloadItem
import com.vdigital.volumestream.core.player.download.DownloadState
import com.vditital.data.model.PlaybackMediaItem
import com.vditital.data.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    // Cache the combined+stateIn StateFlow per id so that recompositions
    // always get the same shared hot flow rather than creating a new stateIn
    // subscription on every call.
    private val _observedStates = mutableMapOf<String, StateFlow<DownloadState>>()

    private fun overrideFor(id: String): MutableStateFlow<DownloadState?> =
        _stateOverrides.getOrPut(id) { MutableStateFlow(null) }

    /**
     * Loads the persisted download list off the Main thread to avoid disk I/O
     * (SharedPreferences / NSUserDefaults scan) on the UI thread.
     */
    fun refreshDownloads() {
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { downloadController.listDownloads() }
                _allDownloads.value = items
            } catch (e: Exception) {
                AppLogger.e("DownloadVM", "refreshDownloads failed", e)
            }
        }
    }

    fun download(item: PlaybackMediaItem) {
        val url = item.downloadUrl.ifBlank { item.streamUrl }
        overrideFor(item.id).value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    downloadController.download(item.id, url, item.title, item.artworkUrl)
                }
                // Wait for terminal state — Idle covers the "cancelled" path so
                // this coroutine always unblocks and refreshDownloads() always fires.
                withContext(Dispatchers.IO) {
                    downloadController.observeState(item.id)
                        .first {
                            it == DownloadState.Completed ||
                            it is DownloadState.Failed    ||
                            it == DownloadState.Idle
                        }
                }
                refreshDownloads()
            } catch (e: Exception) {
                AppLogger.e("DownloadVM", "download failed", e)
            }
        }
    }

    /**
     * Cancels an in-progress download off the Main thread (WorkManager Binder IPC +
     * SharedPreferences write must not block UI).
     */
    fun cancel(id: String) {
        // Force the UI to Idle immediately — before WorkManager's async CANCELLED
        // event arrives — so the button never flashes back to a "tick" state.
        AppLogger.d("DownloadVM", "[$id] cancel() — setting override=Idle")
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
        AppLogger.d("DownloadVM", "[$id] remove() — setting override=Idle")
        overrideFor(id).value = DownloadState.Idle
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { downloadController.remove(id) }
                refreshDownloads()
            } catch (e: Exception) {
                AppLogger.e("DownloadVM", "remove failed", e)
            }
        }
    }

    fun observeState(id: String): StateFlow<DownloadState> {
        // Return the cached flow if already created — avoids creating a new stateIn
        // subscription on every recomposition of MediaItemWidget.
        _observedStates[id]?.let { return it }

        val override = overrideFor(id)
        // Combine: if an override is present use it, otherwise fall through to the
        // controller's flow.
        //
        // Override clearing rules:
        //  • Idle override  → only cleared when the controller enters an active state
        //                     (Queued/Downloading/Completed), i.e. a re-download has begun.
        //                     It is NOT cleared when controllerState==Idle because
        //                     WorkManager emits a CANCELLED→Idle transition *after* the
        //                     override was set, which would cause the tick to flash back.
        //  • Other overrides → cleared as soon as the controller reaches the same state
        //                     or moves to any active state (normal flow).
        val flow = combine(
            downloadController.observeState(id),
            override
        ) { controllerState, overrideState ->
            AppLogger.d("DownloadVM", "[$id] combine: controller=$controllerState  override=$overrideState")
            val result = if (overrideState != null) {
                val shouldClear = when {
                    // Idle override: only clear when a fresh active download starts
                    overrideState == DownloadState.Idle ->
                        controllerState is DownloadState.Queued ||
                        controllerState is DownloadState.Downloading ||
                        controllerState == DownloadState.Completed
                    // Non-idle override: clear when controller reaches the same state
                    // or when a new active download supersedes it
                    controllerState == overrideState -> true
                    overrideState != DownloadState.Idle &&
                        (controllerState is DownloadState.Queued ||
                         controllerState is DownloadState.Downloading) -> true
                    else -> false
                }
                if (shouldClear) {
                    AppLogger.d("DownloadVM", "[$id] clearing override (was $overrideState)")
                    override.value = null
                }
                overrideState
            } else {
                controllerState
            }
            AppLogger.d("DownloadVM", "[$id] emitting: $result")
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
        _observedStates[id] = flow
        return flow
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

    /** Release all cached StateFlow subscriptions when the ViewModel is destroyed. */
    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled by super — the stateIn coroutines it owns stop
        // automatically. Clearing the maps releases the StateFlow references too.
        _stateOverrides.clear()
        _observedStates.clear()
    }
}

