package com.vdigital.volumestream.core.player.download

sealed class DownloadState {
    data object Idle : DownloadState()
    data object Queued : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data object Completed : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}
