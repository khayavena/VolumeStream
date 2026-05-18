package com.vditital.data.model

import com.vditital.data.config.StreamVaultConfig
import com.vditital.data.config.ArtworkProfile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames
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
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("isPublished", "published")
    val isPublished: Boolean = true,
    @SerialName("releasedAt")
    val releasedAt: String = "",
    @SerialName("qualities")
    val qualities: List<String> = emptyList()
)

private val absoluteUrlRegex = Regex("^(https?)://(\\[[^]]+]|[^/:]+)(:\\d+)?(.*)$", RegexOption.IGNORE_CASE)
private const val sampleArtworkPrefix = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/"

private fun buildAbsoluteApiUrl(
    apiHost: String,
    config: StreamVaultConfig,
    apiPath: String,
    originOverride: String? = null
): String {
    val origin = originOverride ?: "${if (config.apiUseHttps) "https" else "http"}://$apiHost:${config.apiPort}"
    return "$origin/${apiPath.trimStart('/')}"
}

private fun extractOrigin(rawUrl: String): String? {
    val match = absoluteUrlRegex.matchEntire(rawUrl.trim()) ?: return null
    return "${match.groupValues[1]}://${match.groupValues[2]}${match.groupValues[3]}"
}

private fun remapArtworkSource(rawArtworkUrl: String?, mediaId: String, config: StreamVaultConfig): String {
    val raw = rawArtworkUrl?.trim().orEmpty()
    if (raw.isBlank()) return raw

    return if (raw.startsWith(sampleArtworkPrefix, ignoreCase = true)) {
        // Route sample catalog artwork through local API by media id.
        val variant = when (config.artworkProfile) {
            ArtworkProfile.TV -> "tv"
            ArtworkProfile.MOBILE -> "mobile"
        }
        "http://localhost:${config.apiPort}/art/$variant/$mediaId.svg"
    } else {
        raw
    }
}

private fun normalizeMediaUrl(rawUrl: String?, apiHost: String, config: StreamVaultConfig): String {
    val raw = rawUrl?.trim().orEmpty()
    if (raw.isBlank()) return ""

    val preferredScheme = if (config.apiUseHttps) "https" else "http"

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
    // The server's feed `streamUrl` is already the canonical HLS manifest URL:
    //   http(s)://{host}:{port}/api/v1/manifest/{id}
    // `hlsStreamUrl` is used directly by iOS (prefetched to a local .m3u8 file).
    // `streamUrl` is remapped to the DASH manifest path for Android/Web.
    // The replace("/manifest/dash/", "/manifest/") guard in PlaybackStateController
    // covers the case where hlsStreamUrl falls through to streamUrl on iOS.
    id           = id,
    title        = title,
    isDownloaded = false,
    // Android/Web: DASH MPD endpoint derived from the feed origin
    streamUrl    = buildAbsoluteApiUrl(
        apiHost = apiHost,
        config = config,
        apiPath = "${config.apiBasePath}/${config.dashManifestPath}/$id",
        originOverride = extractOrigin(normalizeMediaUrl(streamUrl, apiHost, config))
    ),
    // iOS: the normalized feed streamUrl is already the HLS manifest URL.
    // Falls back to the canonical user-manifest path if the feed value is blank
    // (e.g. newly-ingested items not yet present in the feed cache).
    hlsStreamUrl = normalizeMediaUrl(streamUrl, apiHost, config).let { normalized ->
        if (normalized.isNotBlank() && !normalized.contains("/manifest/dash/")) {
            normalized
        } else {
            // Feed returned a DASH URL or blank — rewrite to HLS manifest path.
            buildAbsoluteApiUrl(
                apiHost = apiHost,
                config = config,
                apiPath = "${config.apiBasePath}/${config.userManifestPath}/$id",
                originOverride = extractOrigin(normalizeMediaUrl(streamUrl, apiHost, config))
                    ?: "${if (config.apiUseHttps) "https" else "http"}://$apiHost:${config.apiPort}"
            )
        }
    },
    downloadUrl  = normalizeMediaUrl(downloadUrl, apiHost, config),
    artworkUrl   = normalizeMediaUrl(remapArtworkSource(artworkUrl, id, config), apiHost, config),
    durationMs   = durationMs,
    description  = description ?: ""
)
