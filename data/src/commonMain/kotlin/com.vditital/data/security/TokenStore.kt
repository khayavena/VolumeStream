package com.vditital.data.security

/**
 * Lightweight secure store for the JWT, device ID, and user email.
 *
 * Android actual: SharedPreferences (EncryptedSharedPreferences for prod)
 * iOS actual:     NSUserDefaults (Keychain for prod)
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class TokenStore {
    fun getJwt(): String?
    fun setJwt(jwt: String)
    fun clearJwt()

    /** Stable UUID created on first install and never changed. */
    fun getDeviceId(): String

    fun getUserEmail(): String?
    fun setUserEmail(email: String)
    fun clearAll()
}

