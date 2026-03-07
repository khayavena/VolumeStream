package com.vditital.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaFeedResponse(
    @SerialName("items")
    val items: List<MediaItemDto>,
    @SerialName("totalCount")
    val totalCount: Int = 0,
    @SerialName("page")
    val page: Int = 1,
    @SerialName("pageSize")
    val pageSize: Int = 20
)

@Serializable
data class MediaItemDto(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("streamUrl")
    val streamUrl: String,
    @SerialName("downloadUrl")
    val downloadUrl: String = "",
    @SerialName("artworkUrl")
    val artworkUrl: String = "",
    @SerialName("durationMs")
    val durationMs: Long = 0,
    @SerialName("fileSizeBytes")
    val fileSizeBytes: Long = 0,
    @SerialName("categoryId")
    val categoryId: String = "",
    @SerialName("categoryName")
    val categoryName: String = "",
    @SerialName("isPublished")
    val isPublished: Boolean = true,
    @SerialName("releasedAt")
    val releasedAt: String = "",
    @SerialName("qualities")
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
