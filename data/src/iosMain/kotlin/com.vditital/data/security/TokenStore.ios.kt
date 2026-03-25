package com.vditital.data.security

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class TokenStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getJwt(): String? = defaults.stringForKey(KEY_JWT)

    actual fun setJwt(jwt: String) {
        defaults.setObject(jwt, forKey = KEY_JWT)
        defaults.synchronize()
    }

    actual fun clearJwt() {
        defaults.removeObjectForKey(KEY_JWT)
        defaults.synchronize()
    }

    actual fun getDeviceId(): String {
        return defaults.stringForKey(KEY_DEVICE_ID) ?: run {
            val id = NSUUID().UUIDString
            defaults.setObject(id, forKey = KEY_DEVICE_ID)
            defaults.synchronize()
            id
        }
    }

    actual fun getUserEmail(): String? = defaults.stringForKey(KEY_EMAIL)

    actual fun setUserEmail(email: String) {
        defaults.setObject(email, forKey = KEY_EMAIL)
        defaults.synchronize()
    }

    actual fun clearAll() {
        defaults.removeObjectForKey(KEY_JWT)
        defaults.removeObjectForKey(KEY_EMAIL)
        defaults.synchronize()
    }

    private companion object {
        const val KEY_JWT = "vs_jwt"
        const val KEY_DEVICE_ID = "vs_device_id"
        const val KEY_EMAIL = "vs_user_email"
    }
}

