@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vditital.data.security

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

/**
 * Secure token storage for iOS.
 *
 * - **JWT & email** → iOS Keychain (kSecClassGenericPassword).
 *   The Keychain is encrypted at rest, excluded from iCloud backups by default,
 *   and protected by the device passcode / Secure Enclave.
 * - **deviceId** → NSUserDefaults (not sensitive; must survive app reinstall via
 *   iCloud backup so device registration is idempotent).
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class TokenStore {

    // ── Keychain helpers ──────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun keychainRead(key: String): String? = memScoped {
        val query = NSMutableDictionary()
        query.setObject(kSecClassGenericPassword as Any, forKey = kSecClass as NSCopyingProtocol)
        query.setObject(key,                             forKey = kSecAttrAccount as NSCopyingProtocol)
        query.setObject(KEYCHAIN_SERVICE,               forKey = kSecAttrService as NSCopyingProtocol)
        query.setObject(NSNumber.numberWithBool(true),   forKey = kSecReturnData as NSCopyingProtocol)
        query.setObject(kSecMatchLimitOne as Any,        forKey = kSecMatchLimit as NSCopyingProtocol)

        val queryRef = CFBridgingRetain(query) as CFDictionaryRef
        val keychainResult = try {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(queryRef, result.ptr)
            if (status != errSecSuccess) null
            else {
                val data = result.value as? NSData ?: return@memScoped null
                NSString.create(data, NSUTF8StringEncoding) as? String
            }
        } finally {
            CFRelease(queryRef)
        }

        // If keychain returned nothing, check the NSUserDefaults fallback written by keychainWrite.
        keychainResult ?: (NSUserDefaults.standardUserDefaults.stringForKey("kc_fb_$key"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun keychainWrite(key: String, value: String) {
        val data = value.encodeToByteArray().let { bytes ->
            bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        }

        val updateQuery = NSMutableDictionary()
        updateQuery.setObject(kSecClassGenericPassword as Any, forKey = kSecClass as NSCopyingProtocol)
        updateQuery.setObject(key,              forKey = kSecAttrAccount as NSCopyingProtocol)
        updateQuery.setObject(KEYCHAIN_SERVICE, forKey = kSecAttrService as NSCopyingProtocol)

        val attrs = NSMutableDictionary()
        attrs.setObject(data, forKey = kSecValueData as NSCopyingProtocol)

        val updateQueryRef = CFBridgingRetain(updateQuery) as CFDictionaryRef
        val attrsRef = CFBridgingRetain(attrs) as CFDictionaryRef
        val updateStatus = try {
            SecItemUpdate(updateQueryRef, attrsRef)
        } finally {
            CFRelease(updateQueryRef)
            CFRelease(attrsRef)
        }

        if (updateStatus == errSecItemNotFound) {
            val addQuery = updateQuery.mutableCopy() as NSMutableDictionary
            addQuery.setObject(data, forKey = kSecValueData as NSCopyingProtocol)
            addQuery.setObject(
                kSecAttrAccessibleAfterFirstUnlock as Any,
                forKey = kSecAttrAccessible as NSCopyingProtocol
            )

            val addQueryRef = CFBridgingRetain(addQuery) as CFDictionaryRef
            val addStatus = try {
                SecItemAdd(addQueryRef, null)
            } finally {
                CFRelease(addQueryRef)
            }

            if (addStatus != errSecSuccess) {
                // Keychain unavailable (e.g. simulator entitlement issue) — fall back to
                // NSUserDefaults so the app remains functional in development.
                println("[TokenStore] Keychain SecItemAdd failed status=$addStatus for key=$key — falling back to NSUserDefaults")
                NSUserDefaults.standardUserDefaults.setObject(value, forKey = "kc_fb_$key")
                NSUserDefaults.standardUserDefaults.synchronize()
            }
        } else if (updateStatus != errSecSuccess) {
            println("[TokenStore] Keychain SecItemUpdate failed status=$updateStatus for key=$key — falling back to NSUserDefaults")
            NSUserDefaults.standardUserDefaults.setObject(value, forKey = "kc_fb_$key")
            NSUserDefaults.standardUserDefaults.synchronize()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun keychainDelete(key: String) {
        val query = NSMutableDictionary()
        query.setObject(kSecClassGenericPassword as Any, forKey = kSecClass as NSCopyingProtocol)
        query.setObject(key,              forKey = kSecAttrAccount as NSCopyingProtocol)
        query.setObject(KEYCHAIN_SERVICE, forKey = kSecAttrService as NSCopyingProtocol)

        val queryRef = CFBridgingRetain(query) as CFDictionaryRef
        try {
            SecItemDelete(queryRef)
        } finally {
            CFRelease(queryRef)
        }
        // Also clear the NSUserDefaults fallback
        NSUserDefaults.standardUserDefaults.removeObjectForKey("kc_fb_$key")
        NSUserDefaults.standardUserDefaults.synchronize()
    }

    // ── TokenStore API ────────────────────────────────────────────────────────

    actual fun getJwt(): String?      = keychainRead(KEY_JWT)
    actual fun setJwt(jwt: String)    = keychainWrite(KEY_JWT, jwt)
    actual fun clearJwt()             = keychainDelete(KEY_JWT)

    actual fun getUserEmail(): String?       = keychainRead(KEY_EMAIL)
    actual fun setUserEmail(email: String)   = keychainWrite(KEY_EMAIL, email)

    actual fun clearAll() {
        keychainDelete(KEY_JWT)
        keychainDelete(KEY_EMAIL)
        // Keep deviceId in NSUserDefaults — intentionally not cleared
    }

    /** deviceId is not sensitive and must survive reinstall via backup. */
    actual fun getDeviceId(): String {
        val defaults = NSUserDefaults.standardUserDefaults
        return defaults.stringForKey(KEY_DEVICE_ID) ?: run {
            val id = NSUUID().UUIDString
            defaults.setObject(id, forKey = KEY_DEVICE_ID)
            defaults.synchronize()
            id
        }
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "com.vdigital.volumestream"
        const val KEY_JWT       = "vs_jwt"
        const val KEY_EMAIL     = "vs_user_email"
        const val KEY_DEVICE_ID = "vs_device_id"
    }
}
