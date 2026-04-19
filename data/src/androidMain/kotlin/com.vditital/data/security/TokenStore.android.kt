package com.vditital.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class TokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vs_token_store_enc",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback to plain prefs on devices where EncryptedSharedPreferences fails
        // (e.g. during robolectric tests or on very old hardware without AES support).
        context.getSharedPreferences("vs_token_store", Context.MODE_PRIVATE)
    }

    actual fun getJwt(): String? = prefs.getString(KEY_JWT, null)

    actual fun setJwt(jwt: String) {
        prefs.edit().putString(KEY_JWT, jwt).apply()
    }

    actual fun clearJwt() {
        prefs.edit().remove(KEY_JWT).apply()
    }

    actual fun getDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            id
        }
    }

    actual fun getUserEmail(): String? = prefs.getString(KEY_EMAIL, null)

    actual fun setUserEmail(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    actual fun clearAll() {
        prefs.edit()
            .remove(KEY_JWT)
            .remove(KEY_EMAIL)
            .apply()
        // Keep deviceId — device registration persists across sessions
    }

    private companion object {
        const val KEY_JWT = "jwt"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_EMAIL = "user_email"
    }
}
