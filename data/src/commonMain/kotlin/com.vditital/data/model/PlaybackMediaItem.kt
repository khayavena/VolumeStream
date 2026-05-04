package com.vditital.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackMediaItem(
    val id: String,
    val title: String,
    val isDownloaded: Boolean = false,
    val streamUrl: String,
    val hlsStreamUrl: String = "",
    val downloadUrl: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0L,
    val description: String = ""
)