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
    /** Minimum steady-state forward buffer kept in memory (default 15s). */
    val minBufferMs: Int = 15_000,
    /** Maximum forward buffer the player will try to maintain ahead (default 30s). */
    val maxBufferMs: Int = 30_000,
    /** Buffer required before initial playback starts (default 2.5s). */
    val bufferForPlaybackMs: Int = 2_500,
    /** Buffer required before resuming after a stall (default 5s). */
    val bufferForPlaybackAfterRebufferMs: Int = 5_000,

    // ── iOS AVPlayer buffering ────────────────────────────────────────────────
    /**
     * Preferred amount of media AVPlayer should keep buffered ahead when possible.
     * A small forward buffer (default 6s) reduces visible stalls on variable networks
     * without adding excessive startup delay.
     */
    val iosPreferredForwardBufferSeconds: Double = 6.0,
    /**
     * When true, AVPlayer waits briefly for enough media to reduce the chance of
     * immediate rebuffer stalls during startup/resume.
     */
    val iosAutomaticallyWaitsToMinimizeStalling: Boolean = true,

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

