package com.vditital.data.config

/**
 * All runtime-configurable parameters for the StreamVault data layer.
 *
 * Supply a custom instance via Koin before [externalDataModule] is loaded:
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
    /** Use HTTPS instead of HTTP for all requests. */
    val useHttps: Boolean = false,

    // ── Path prefixes ─────────────────────────────────────────────────────────
    /** Common prefix for all StreamVault API paths, e.g. "api/v1". */
    val apiBasePath: String = "api/v1",
    /** Common prefix for all auth-pulse paths, e.g. "api". */
    val authBasePath: String = "api",
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
)

