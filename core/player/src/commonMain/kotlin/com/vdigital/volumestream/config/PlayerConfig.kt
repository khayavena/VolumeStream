package com.vdigital.volumestream.config

/**
 * All runtime-configurable parameters for the VolumeStream player SDK.
 *
 * Supply a custom instance via Koin before [playerCoreModule] is loaded:
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         module { single { PlayerConfig(cacheSizeBytes = 500L * 1024 * 1024) } },
 *         appModule
 *     )
 * }
 * ```
 */
data class PlayerConfig(
    // ── Android ExoPlayer cache ───────────────────────────────────────────────
    /** Maximum disk space used for the media cache (default 500 MB). */
    val cacheSizeBytes: Long = 500L * 1024L * 1024L,
    /** Name of the subdirectory inside the app cache directory. */
    val cacheDirName: String = "media",

    // ── Android ExoPlayer buffering ───────────────────────────────────────────
    /** Minimum buffer kept in memory before ExoPlayer is satisfied (ms). */
    val minBufferMs: Int = 30_000,
    /** Maximum buffer ExoPlayer will try to maintain ahead (ms). */
    val maxBufferMs: Int = 60_000,
    /** Buffer required before playback starts for the first time (ms). */
    val bufferForPlaybackMs: Int = 3_000,
    /** Buffer required before playback resumes after a rebuffer stall (ms). */
    val bufferForPlaybackAfterRebufferMs: Int = 8_000,

    // ── Android HTTP timeouts ─────────────────────────────────────────────────
    /** TCP connect timeout for segment / manifest requests (ms). */
    val httpConnectTimeoutMs: Int = 15_000,
    /** Socket read timeout — should be generous on slow Wi-Fi (ms). */
    val httpReadTimeoutMs: Int = 20_000,


    // ── DASH proxy ────────────────────────────────────────────────────────────
    /**
     * URL sub-path used to identify AES-128-GCM encrypted DASH segment requests.
     * Any request URL containing this string is routed through
     * AesGcmDecryptingDataSource.  Must match the server-side DASH proxy path,
     * e.g. "/api/v1/proxy/dash/".
     *
     * Default aligns with StreamVaultConfig.dashProxyPath ("proxy/dash") prepended
     * by the standard API base path "api/v1".
     */
    val dashProxyPathFragment: String = "/api/v1/proxy/dash/",
)

