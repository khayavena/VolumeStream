package com.vdigital.volumestream.ui.viewmodel

import com.vditital.data.config.StreamVaultConfig

internal object DownloadUrlResolver {
    private data class RewriteContext(val prefix: String, val trimmedBase: String, val mediaId: String)

    internal fun toDownloadManifestUrl(
        source: String,
        fallbackMediaId: String,
        config: StreamVaultConfig
    ): String? = toHlsManifestUrl(source, fallbackMediaId, config)

    internal fun toHlsManifestUrl(
        source: String,
        fallbackMediaId: String,
        config: StreamVaultConfig
    ): String? {
        val ctx = parse(source, fallbackMediaId, config) ?: return null
        return "${ctx.prefix}/${ctx.trimmedBase}/manifest/${ctx.mediaId}"
    }

    internal fun toDashManifestUrl(
        source: String,
        fallbackMediaId: String,
        config: StreamVaultConfig
    ): String? {
        val ctx = parse(source, fallbackMediaId, config) ?: return null
        return "${ctx.prefix}/${ctx.trimmedBase}/manifest/dash/${ctx.mediaId}"
    }

    private fun parse(source: String, fallbackMediaId: String, config: StreamVaultConfig): RewriteContext? {
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
            .takeIf { it.isNotBlank() }
            ?: return null

        return RewriteContext(prefix = prefix, trimmedBase = trimmedBase, mediaId = mediaId)
    }
}
