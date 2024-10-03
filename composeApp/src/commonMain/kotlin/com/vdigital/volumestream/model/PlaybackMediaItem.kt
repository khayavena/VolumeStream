package com.vdigital.volumestream.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackMediaItem(
    val id: String,
    val title: String,
    val isDownloaded: Boolean = false,
    val streamUrl: String,
    val downloadUrl: String = "",
    val artworkUrl: String = ""
)