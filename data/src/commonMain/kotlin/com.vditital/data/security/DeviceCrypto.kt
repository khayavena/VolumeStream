package com.vditital.data.security

/**
 * Platform-specific RSA-2048 key pair manager.
 *
 * Android: AndroidKeyStore (hardware-backed)
 * iOS: Security.framework Keychain (Secure Enclave on supported hardware)
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class DeviceCrypto {
    /** Returns the Base64-encoded DER SubjectPublicKeyInfo for the device's RSA-2048 public key. */
    fun getOrCreatePublicKeyB64(): String

    /** Signs [payload] with SHA256withRSA and returns the standard Base64-encoded signature. */
    fun signPayload(payload: String): String
}

