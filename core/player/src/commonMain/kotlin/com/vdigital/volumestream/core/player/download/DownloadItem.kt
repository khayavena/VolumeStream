package com.vdigital.volumestream.core.player.download

data class DownloadItem(
    val id: String,
    val title: String,
    val url: String,
    val artworkUrl: String = "",
    val state: DownloadState = DownloadState.Idle,
    val localPath: String? = null
)
