package com.vdigital.volumestream.ui.viewmodel

import com.vditital.data.config.StreamVaultConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadUrlResolverTest {

    @Test
    fun `rewrites user manifest url to dash manifest`() {
        val source = "https://api.example.com/api/v1/manifest/123e4567-e89b-12d3-a456-426614174000"

        val result = DownloadUrlResolver.toDownloadManifestUrl(
            source = source,
            fallbackMediaId = "unused",
            config = StreamVaultConfig()
        )

        assertEquals(
            "https://api.example.com/api/v1/manifest/dash/123e4567-e89b-12d3-a456-426614174000",
            result
        )
    }

    @Test
    fun `rewrites dash manifest url to dash manifest`() {
        val source = "http://localhost:8081/api/v1/manifest/dash/abc-123?t=x"

        val result = DownloadUrlResolver.toDownloadManifestUrl(
            source = source,
            fallbackMediaId = "unused",
            config = StreamVaultConfig()
        )

        assertEquals(
            "http://localhost:8081/api/v1/manifest/dash/abc-123",
            result
        )
    }

    @Test
    fun `supports custom apiBasePath`() {
        val config = StreamVaultConfig(apiBasePath = "edge/api")
        val source = "https://cdn.example.com/edge/api/manifest/media-42"

        val result = DownloadUrlResolver.toDownloadManifestUrl(
            source = source,
            fallbackMediaId = "unused",
            config = config
        )

        assertEquals(
            "https://cdn.example.com/edge/api/manifest/dash/media-42",
            result
        )
    }

    @Test
    fun `falls back to provided media id when source has no id segment`() {
        val source = "https://api.example.com/api/v1/manifest/"

        val result = DownloadUrlResolver.toDownloadManifestUrl(
            source = source,
            fallbackMediaId = "fallback-id",
            config = StreamVaultConfig()
        )

        assertEquals(
            "https://api.example.com/api/v1/manifest/dash/fallback-id",
            result
        )
    }

    @Test
    fun `rejects non-http sources`() {
        val result = DownloadUrlResolver.toDownloadManifestUrl(
            source = "/api/v1/manifest/abc",
            fallbackMediaId = "fallback-id",
            config = StreamVaultConfig()
        )

        assertNull(result)
    }
}

