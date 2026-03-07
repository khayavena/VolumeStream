package com.vditital.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaFeedResponse(
    val categories: Map<String, List<MediaItemDto>>
)

@Serializable
data class MediaItemDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val streamUrl: String,
    val downloadUrl: String = "",
    val artworkUrl: String = "",
    val durationMs: Long = 0,
    val fileSizeBytes: Long = 0,
    val categoryId: String = "",
    val categoryName: String = "",
    val isPublished: Boolean = true,
    val releasedAt: String = "",
    val qualities: List<String> = emptyList()
)

fun MediaItemDto.toPlaybackMediaItem() = PlaybackMediaItem(
    id = id,
    title = title,
    isDownloaded = false,
    streamUrl = streamUrl,
    downloadUrl = downloadUrl,
    artworkUrl = artworkUrl
)
