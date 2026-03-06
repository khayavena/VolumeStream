package com.vdigital.volumestream.core.player.download

import kotlinx.coroutines.flow.Flow

expect class DownloadController {
    fun download(id: String, url: String, title: String, artworkUrl: String)
    fun cancel(id: String)
    fun remove(id: String)
    fun observeState(id: String): Flow<DownloadState>
    fun getLocalPath(id: String): String?
    fun isDownloaded(id: String): Boolean
    fun listDownloads(): List<DownloadItem>
}
