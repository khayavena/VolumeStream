package com.vdigital.volumestream.core.player.download

import kotlinx.coroutines.flow.Flow

expect class DownloadController {
    /**
     * Enqueues a download for [id].
     *
     * @param wifiOnly When true the download is restricted to unmetered/Wi-Fi networks.
     *                 Android: WorkManager constraint NetworkType.UNMETERED.
     *                 iOS:     NSURLSession with allowsCellularAccess = false.
     */
    fun download(
        id: String,
        url: String,
        title: String,
        artworkUrl: String,
        wifiOnly: Boolean = false,
        headers: Map<String, String> = emptyMap()
    )
    fun cancel(id: String)
    fun remove(id: String)
    fun observeState(id: String): Flow<DownloadState>
    fun getLocalPath(id: String): String?
    fun isDownloaded(id: String): Boolean
    fun listDownloads(): List<DownloadItem>
}
