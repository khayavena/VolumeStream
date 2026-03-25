@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vditital.data.security

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import platform.CoreFoundation.*

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = this.size.toULong())
}

private const val DEFAULT_KEY_ALIAS = "com.vdigital.vs.devkey"

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DeviceCrypto(private val alias: String = DEFAULT_KEY_ALIAS) {

    actual fun getOrCreatePublicKeyB64(): String {
        val priv = getOrCreateKey()
        val pub = SecKeyCopyPublicKey(priv) ?: return ""
        return memScoped {
            val err = alloc<CFErrorRefVar>()
            val data = SecKeyCopyExternalRepresentation(pub, err.ptr)
            CFRelease(pub)
            if (data == null) return@memScoped ""
            val b64 = (data as NSData).base64EncodedStringWithOptions(0u)
            CFRelease(data)
            b64
        }
    }

    actual fun signPayload(payload: String): String {
        val priv = getOrCreateKey()
        val inputNs = payload.encodeToByteArray().toNSData()
        return memScoped {
            val err = alloc<CFErrorRefVar>()
            val sig = SecKeyCreateSignature(
                priv,
                kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,
                inputNs as CFDataRef,
                err.ptr
            ) ?: return@memScoped ""
            val b64 = (sig as NSData).base64EncodedStringWithOptions(0u)
            CFRelease(sig)
            b64
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getOrCreateKey(): SecKeyRef {
        val tagData = alias.encodeToByteArray().toNSData()

        // Build load-query dictionary via NSMutableDictionary (toll-free bridged with CFDictionary)
        val loadAttrs = NSMutableDictionary()
        loadAttrs.setObject(kSecClassKey as Any, forKey = kSecClass as NSCopyingProtocol)
        loadAttrs.setObject(kSecAttrKeyTypeRSA as Any, forKey = kSecAttrKeyType as NSCopyingProtocol)
        loadAttrs.setObject(tagData, forKey = kSecAttrApplicationTag as NSCopyingProtocol)
        loadAttrs.setObject(kSecAttrKeyClassPrivate as Any, forKey = kSecAttrKeyClass as NSCopyingProtocol)
        loadAttrs.setObject(NSNumber.numberWithBool(true), forKey = kSecReturnRef as NSCopyingProtocol)

        memScoped {
            val result = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(loadAttrs as CFDictionaryRef, result.ptr) == errSecSuccess) {
                return result.value as SecKeyRef
            }
        }

        // Generate RSA-2048 private key and store in Keychain
        val genAttrs = NSMutableDictionary()
        genAttrs.setObject(kSecAttrKeyTypeRSA as Any, forKey = kSecAttrKeyType as NSCopyingProtocol)
        genAttrs.setObject(NSNumber.numberWithInt(2048), forKey = kSecAttrKeySizeInBits as NSCopyingProtocol)
        genAttrs.setObject(tagData, forKey = kSecAttrApplicationTag as NSCopyingProtocol)
        genAttrs.setObject(NSNumber.numberWithBool(true), forKey = kSecAttrIsPermanent as NSCopyingProtocol)

        return memScoped {
            val err = alloc<CFErrorRefVar>()
            SecKeyCreateRandomKey(genAttrs as CFDictionaryRef, err.ptr)
                ?: error("RSA-2048 key generation failed on iOS")
        }
    }
}
