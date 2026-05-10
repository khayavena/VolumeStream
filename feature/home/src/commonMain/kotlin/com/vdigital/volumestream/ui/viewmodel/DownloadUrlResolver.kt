package com.vdigital.volumestream.ui.viewmodel

import com.vditital.data.config.StreamVaultConfig

internal object DownloadUrlResolver {
    internal fun toDownloadManifestUrl(
        source: String,
        fallbackMediaId: String,
        config: StreamVaultConfig
    ): String? {
        val normalized = source.trim().replace("/manifest/dash/", "/manifest/")
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            return null
        }

        val trimmedBase = config.apiBasePath.trim('/').ifBlank { "api/v1" }
        val userManifestMarker = "/$trimmedBase/${config.userManifestPath.trim('/')}"
        val dashManifestMarker = "/$trimmedBase/${config.dashManifestPath.trim('/')}"
        val offlineDashMarker = "/$trimmedBase/manifest/offline/dash/"
        val offlineHlsMarker = "/$trimmedBase/manifest/offline/hls/"
        val marker = when {
            normalized.contains(offlineDashMarker) -> offlineDashMarker
            normalized.contains(offlineHlsMarker) -> offlineHlsMarker
            normalized.contains(userManifestMarker) -> userManifestMarker
            normalized.contains(dashManifestMarker) -> dashManifestMarker
            else -> null
        } ?: return null

        val idx = normalized.indexOf(marker)
        if (idx < 0 || idx + marker.length >= normalized.length) {
            return null
        }

        val prefix = normalized.substring(0, idx).trimEnd('/')
        val mediaId = normalized.substring(idx + marker.length)
            .trimStart('/')
            .substringBefore("?")
            .substringBefore("/")
            .ifBlank { fallbackMediaId }
        if (mediaId.isBlank()) {
            return null
        }
        return "$prefix/$trimmedBase/manifest/dash/$mediaId"
    }
}
