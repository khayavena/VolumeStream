package com.vditital.data.config

enum class ArtworkProfile {
    MOBILE,
    TV,
}

/**
 * All runtime-configurable parameters for the StreamVault data layer.
 *
 * Supply a custom instance via Koin before `externalDataModule` is loaded:
 *
 * ```kotlin
 * startKoin {
 *     modules(
 *         module { single { StreamVaultConfig(useHttps = true, apiPort = 443) } },
 *         appModule
 *     )
 * }
 * ```
 *
 * Every field has a default that matches the StreamVault reference server so that
 * no configuration is required for standard deployments.
 */
data class StreamVaultConfig(
    // ── Network ───────────────────────────────────────────────────────────────
    /** Port the StreamVault API listens on. */
    val apiPort: Int = 8081,
    /** Port the auth-pulse-service listens on. */
    val authPort: Int = 8080,
    /** Use HTTPS for auth-pulse-service requests. */
    val authUseHttps: Boolean = false,
    /** Use HTTPS for Stream Vault API/media requests. */
    val apiUseHttps: Boolean = false,
    /**
     * Backward-compatibility alias for older call sites that assumed one protocol.
     * Prefer [authUseHttps] and [apiUseHttps] for mixed-protocol deployments.
     */
    val useHttps: Boolean = authUseHttps || apiUseHttps,

    // ── Path prefixes ─────────────────────────────────────────────────────────
    /** Common prefix for all StreamVault API paths, e.g. "api/v1". */
    val apiBasePath: String = "api/v1",
    /** Common prefix for all auth-pulse paths, e.g. "api". */
    val authBasePath: String = "api",
    /** Canonical user-scoped HLS manifest path advertised by the media feed. */
    val userManifestPath: String = "manifest",
    /** Non-user-scoped HLS master manifest path. */
    val hlsManifestPath: String = "manifest/hls",
    /**
     * Path segment appended to [apiBasePath] to reach the DASH MPD manifest endpoint.
     * Full manifest URL: {scheme}://{host}:{port}/{apiBasePath}/{dashManifestPath}/{mediaId}
     * → default: api/v1/manifest/dash/{id}
     */
    val dashManifestPath: String = "manifest/dash",
    /**
     * Path segment appended to [apiBasePath] for the AES-128-GCM encrypted DASH
     * segment proxy.  Segments are served at:
     *   {scheme}://{host}:{port}/{apiBasePath}/{dashProxyPath}/{mediaId}/{segmentIdx}?t=…
     * The player layer uses this value to route matching URLs through
     * AesGcmDecryptingDataSource.  Must stay in sync with the server configuration.
     */
    val dashProxyPath: String = "proxy/dash",

    // ── Cert-pin session headers ──────────────────────────────────────────────
    /** Header carrying the stable device identifier. */
    val headerDeviceId: String = "X-Device-Id",
    /** Header carrying the millisecond timestamp used in the RSA signature. */
    val headerCertTimestamp: String = "X-Cert-Timestamp",
    /** Header carrying the Base64-encoded RSA-2048 signature. */
    val headerCertSignature: String = "X-Cert-Signature",

    // ── Crypto ────────────────────────────────────────────────────────────────
    /**
     * Alias used to store the device RSA key pair in the platform keystore
     * (AndroidKeyStore alias / iOS Keychain application tag).
     * Change this if multiple SDK consumers share the same keystore namespace.
     */
    val deviceKeyAlias: String = "streamvault_device_key",

    // ── Artwork mapping ───────────────────────────────────────────────────────
    /** Which artwork variant to prefer when feed items contain sample image URLs. */
    val artworkProfile: ArtworkProfile = ArtworkProfile.MOBILE,
)

