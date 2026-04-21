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

private val absoluteUrlRegex = Regex("^(https?)://(\\[[^\\]]+]|[^/:]+)(:\\d+)?(.*)$", RegexOption.IGNORE_CASE)
private const val sampleArtworkPrefix = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/"

private fun remapArtworkSource(rawArtworkUrl: String?, mediaId: String, config: StreamVaultConfig): String {
    val raw = rawArtworkUrl?.trim().orEmpty()
    if (raw.isBlank()) return raw

    return if (raw.startsWith(sampleArtworkPrefix, ignoreCase = true)) {
        // Route sample catalog artwork through local API by media id.
        "http://localhost:${config.apiPort}/art/tv/$mediaId.svg"
    } else {
        raw
    }
}

private fun normalizeMediaUrl(rawUrl: String?, apiHost: String, config: StreamVaultConfig): String {
    val raw = rawUrl?.trim().orEmpty()
    if (raw.isBlank()) return ""

    val preferredScheme = if (config.useHttps) "https" else "http"

    // Supports backend values like "art/mobile/..." (without a leading slash).
    if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
        val normalizedPath = "/${raw.removePrefix("/")}"
        return "$preferredScheme://$apiHost:${config.apiPort}$normalizedPath"
    }

    val match = absoluteUrlRegex.matchEntire(raw) ?: return raw
    val scheme = match.groupValues[1]
    val host = match.groupValues[2].removePrefix("[").removeSuffix("]").lowercase()
    val port = match.groupValues[3]
    val suffix = match.groupValues[4]

    val isLoopbackHost = host == "localhost" || host == "127.0.0.1" || host == "::1"
    if (!isLoopbackHost) return raw

    val finalPort = if (port.isNotBlank()) port else ":${config.apiPort}"
    return "$scheme://$apiHost$finalPort$suffix"
}

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
    downloadUrl  = normalizeMediaUrl(downloadUrl, apiHost, config),
    artworkUrl   = normalizeMediaUrl(remapArtworkSource(artworkUrl, id, config), apiHost, config),
    durationMs   = durationMs,
    description  = description ?: ""
)
