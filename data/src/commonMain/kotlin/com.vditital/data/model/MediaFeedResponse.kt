package com.vditital.data.model

import com.vditital.data.config.StreamVaultConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Top-level response from GET /api/v1/media/feed */
@Serializable
data class MediaFeedResponse(
    @SerialName("categories")
    val categories: Map<String, List<MediaItemDto>> = emptyMap()
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
    val downloadUrl: String? = null,
    @SerialName("artworkUrl")
    val artworkUrl: String? = null,
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

fun MediaItemDto.toPlaybackMediaItem(apiHost: String, config: StreamVaultConfig = StreamVaultConfig()) = PlaybackMediaItem(
    id           = id,
    title        = title,
    isDownloaded = false,
    // DASH MPD manifest endpoint: GET /api/v1/manifest/dash/{id}
    // ExoPlayer parses the MPD and fetches encrypted segments from
    // /api/v1/proxy/dash/{id}/{segmentIdx}?t=… which are routed through
    // AesGcmDecryptingDataSource (AES-128-GCM) by the player's RoutingDataSource.
    // Requires Authorization + X-Session-Token headers (injected by the player layer).
    streamUrl    = "${if (config.useHttps) "https" else "http"}://$apiHost:${config.apiPort}/${config.apiBasePath}/${config.dashManifestPath}/$id",
    downloadUrl  = run {
        val raw = downloadUrl ?: ""
        if (raw.startsWith("/")) "${if (config.useHttps) "https" else "http"}://$apiHost:${config.apiPort}$raw" else raw
    },
    artworkUrl   = artworkUrl ?: "",
    durationMs   = durationMs,
    description  = description ?: ""
)
