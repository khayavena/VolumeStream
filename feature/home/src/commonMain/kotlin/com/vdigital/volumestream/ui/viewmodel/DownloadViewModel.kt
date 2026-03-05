package com.vdigital.volumestream.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vdigital.volumestream.core.player.download.DownloadController
import com.vdigital.volumestream.core.player.download.DownloadState
import com.vditital.data.model.PlaybackMediaItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DownloadViewModel(private val downloadController: DownloadController) : ViewModel() {

    fun download(item: PlaybackMediaItem) {
        val url = item.downloadUrl.ifBlank { item.streamUrl }
        downloadController.download(item.id, url, item.title)
    }

    fun cancel(id: String) = downloadController.cancel(id)

    fun remove(id: String) = downloadController.remove(id)

    fun observeState(id: String): StateFlow<DownloadState> =
        downloadController.observeState(id)
            .stateIn(viewModelScope, SharingStarted.Lazily, DownloadState.Idle)

    fun isDownloaded(id: String): Boolean = downloadController.isDownloaded(id)

    fun getLocalPath(id: String): String? = downloadController.getLocalPath(id)
}
