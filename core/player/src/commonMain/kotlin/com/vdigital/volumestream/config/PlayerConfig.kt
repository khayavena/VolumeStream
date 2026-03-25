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
    /** Maximum disk space used for the media cache (default 100 MB). */
    val cacheSizeBytes: Long = 100L * 1024L * 1024L,
    /** Name of the subdirectory inside the app cache directory. */
    val cacheDirName: String = "media",

    // ── iOS AVFoundation ──────────────────────────────────────────────────────
    /**
     * Key used in [AVURLAsset] options to inject custom HTTP headers.
     * Matches the Objective-C constant `AVURLAssetHTTPHeaderFieldsKey`.
     */
    val avFoundationHttpHeadersKey: String = "AVURLAssetHTTPHeaderFieldsKey",
)

