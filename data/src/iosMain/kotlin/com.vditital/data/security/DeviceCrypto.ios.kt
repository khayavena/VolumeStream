@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.vditital.data.security

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import platform.CoreFoundation.*

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(bytes = it.addressOf(0), length = this.size.toULong())
}

// Must match StreamVaultConfig.deviceKeyAlias so that DI-wired and default-constructed
// instances use the same Keychain application tag.
private const val DEFAULT_KEY_ALIAS = "streamvault_device_key"

private fun derLength(length: Int): ByteArray {
    if (length < 0x80) return byteArrayOf(length.toByte())
    var value = length
    val octets = mutableListOf<Byte>()
    while (value > 0) {
        octets.add(0, (value and 0xFF).toByte())
        value = value ushr 8
    }
    return byteArrayOf((0x80 or octets.size).toByte()) + octets.toByteArray()
}

private fun ByteArray.wrapInSpki(): ByteArray {
    val rsaOid = byteArrayOf(
        0x30, 0x0d, 0x06, 0x09,
        0x2a.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(),
        0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01,
        0x05, 0x00
    )
    val bitStringPayload = byteArrayOf(0x00) + this
    val bitString = byteArrayOf(0x03) + derLength(bitStringPayload.size) + bitStringPayload
    val sequenceContent = rsaOid + bitString
    return byteArrayOf(0x30) + derLength(sequenceContent.size) + sequenceContent
}

/**
 * Safely bridge an NSMutableDictionary to CFDictionaryRef using toll-free bridging.
 * Direct `dict as CFDictionaryRef` casts are UNSAFE in Kotlin/Native on iOS 26 Simulator
 * because the runtime type is NSDictionaryAsKMap, not a raw CPointer.
 */
@Suppress("UNCHECKED_CAST")
private fun NSMutableDictionary.asCFDictionary(): CFDictionaryRef =
    CFBridgingRetain(this) as CFDictionaryRef

/**
 * Pure-Kotlin SHA-256 implementation — no native dependencies.
 * Used to pre-hash for kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256,
 * which is the Digest variant of PKCS1v15-SHA256 signing.
 */
private fun sha256(data: ByteArray): ByteArray {
    // SHA-256 round constants
    val k = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
        0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
        0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
        0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
        0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
        0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
        0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
        0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )
    var h0 = 0x6a09e667.toInt(); var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372.toInt(); var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f.toInt(); var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab.toInt(); var h7 = 0x5be0cd19.toInt()

    val bitLen = data.size.toLong() * 8
    val padLen = if ((data.size % 64) < 56) 56 - (data.size % 64) else 120 - (data.size % 64)
    val msg = ByteArray(data.size + padLen + 8)
    data.copyInto(msg)
    msg[data.size] = 0x80.toByte()
    for (i in 0..7) msg[msg.size - 1 - i] = ((bitLen ushr (i * 8)) and 0xFF).toByte()

    val w = IntArray(64)
    for (i in msg.indices step 64) {
        for (j in 0..15) {
            w[j] = ((msg[i + j * 4].toInt() and 0xFF) shl 24) or
                   ((msg[i + j * 4 + 1].toInt() and 0xFF) shl 16) or
                   ((msg[i + j * 4 + 2].toInt() and 0xFF) shl 8) or
                    (msg[i + j * 4 + 3].toInt() and 0xFF)
        }
        for (j in 16..63) {
            val s0 = (w[j-15].rotateRight(7)) xor (w[j-15].rotateRight(18)) xor (w[j-15] ushr 3)
            val s1 = (w[j-2].rotateRight(17)) xor (w[j-2].rotateRight(19)) xor (w[j-2] ushr 10)
            w[j] = w[j-16] + s0 + w[j-7] + s1
        }
        var a = h0; var b = h1; var c = h2; var d = h3
        var e = h4; var f = h5; var g = h6; var h = h7
        for (j in 0..63) {
            val S1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = h + S1 + ch + k[j] + w[j]
            val S0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = S0 + maj
            h = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += h
    }
    val digest = ByteArray(32)
    listOf(h0, h1, h2, h3, h4, h5, h6, h7).forEachIndexed { i, v ->
        digest[i * 4]     = ((v ushr 24) and 0xFF).toByte()
        digest[i * 4 + 1] = ((v ushr 16) and 0xFF).toByte()
        digest[i * 4 + 2] = ((v ushr 8) and 0xFF).toByte()
        digest[i * 4 + 3] = (v and 0xFF).toByte()
    }
    return digest
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DeviceCrypto(private val alias: String = DEFAULT_KEY_ALIAS) {

    // In-memory cache — avoids rebuilding SecKeyRef on every call.
    private var cachedKey: SecKeyRef? = null

    actual fun getOrCreatePublicKeyB64(): String {
        val priv = getOrCreateKey()
        val pub = SecKeyCopyPublicKey(priv) ?: return ""
        return memScoped {
            val err = alloc<CFErrorRefVar>()
            val data = SecKeyCopyExternalRepresentation(pub, err.ptr)
            CFRelease(pub)
            if (data == null) return@memScoped ""
            val pkcs1 = CFBridgingRelease(data) as NSData
            val spkiBytes = ByteArray(pkcs1.length.toInt()).also { bytes ->
                memScoped {
                    platform.posix.memcpy(bytes.refTo(0), pkcs1.bytes, pkcs1.length)
                }
            }.wrapInSpki()
            spkiBytes.toNSData().base64EncodedStringWithOptions(0u)
        }
    }

    actual fun signPayload(payload: String): String {
        val priv = getOrCreateKey()
        val payloadBytes = payload.encodeToByteArray()
        val hashBytes = sha256(payloadBytes)  // pure-Kotlin SHA-256

        // Attempt order:
        //  1. SecKeyCreateSignature – Digest variant (pre-hashed, PKCS1v15-SHA256)
        //     Works on physical devices and modern Simulator builds.
        //  2. SecKeyCreateSignature – Message variant (full-message, hash internally)
        //     Some device/OS combos reject the Digest variant with OSStatus -50.
        //  3. SecKeyRawSign – kSecPaddingPKCS1SHA256 (0x8004), raw digest input
        //     Deprecated but broadly available; last-resort for older/restrictive builds.
        val sigB64 = trySignWithCreateSignature(priv, hashBytes)
            ?: trySignWithCreateSignatureMessage(priv, payloadBytes)
            ?: trySignWithRawSign(priv, hashBytes)

        if (sigB64 == null) {
            val msg = "[DeviceCrypto] signPayload: all signing attempts failed for payload len=${payloadBytes.size}"
            println(msg)
            error(msg)  // throw so callers surface a meaningful error, not a silent 403
        }
        println("[DeviceCrypto] signPayload: OK (sig len=${sigB64.length})")
        return sigB64
    }

    /** SecKeyCreateSignature with kSecKeyAlgorithmRSASignatureDigestPKCS1v15SHA256. */
    @Suppress("UNCHECKED_CAST")
    private fun trySignWithCreateSignature(key: SecKeyRef, hashBytes: ByteArray): String? {
        val hashNs = hashBytes.toNSData()
        val algo = CFBridgingRetain("rsaSignatureDigestPKCS1v15SHA256" as NSString) as CFStringRef
        return memScoped {
            val err = alloc<CFErrorRefVar>()
            val inputRef = CFBridgingRetain(hashNs) as CFDataRef
            val sig = try {
                SecKeyCreateSignature(key, algo, inputRef, err.ptr)
            } finally {
                CFRelease(inputRef)
                CFRelease(algo)
            }
            if (sig == null) {
                val cfErr = err.value
                val desc = if (cfErr != null) {
                    val descRef = CFErrorCopyDescription(cfErr)
                    val s = if (descRef != null) (CFBridgingRelease(descRef) as? NSString)?.toString() ?: "no-desc" else "null-desc"
                    CFRelease(cfErr)
                    s
                } else "no-cferror"
                println("[DeviceCrypto] createSignature(Digest) failed: $desc")
                null
            } else {
                (CFBridgingRelease(sig) as NSData).base64EncodedStringWithOptions(0u)
            }
        }
    }

    /**
     * Fallback: SecKeyCreateSignature with kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
     * (Message variant — the framework computes SHA-256 internally from the full payload).
     * Some device/OS combinations (notably iOS ≥18 physical devices with Secure Enclave RSA
     * keys) reject the Digest variant with OSStatus -50 but accept the Message variant.
     */
    @Suppress("UNCHECKED_CAST")
    private fun trySignWithCreateSignatureMessage(key: SecKeyRef, payloadBytes: ByteArray): String? {
        val dataNs = payloadBytes.toNSData()
        val algo = CFBridgingRetain("rsaSignatureMessagePKCS1v15SHA256" as NSString) as CFStringRef
        return memScoped {
            val err = alloc<CFErrorRefVar>()
            val inputRef = CFBridgingRetain(dataNs) as CFDataRef
            val sig = try {
                SecKeyCreateSignature(key, algo, inputRef, err.ptr)
            } finally {
                CFRelease(inputRef)
                CFRelease(algo)
            }
            if (sig == null) {
                val cfErr = err.value
                val desc = if (cfErr != null) {
                    val descRef = CFErrorCopyDescription(cfErr)
                    val s = if (descRef != null) (CFBridgingRelease(descRef) as? NSString)?.toString() ?: "no-desc" else "null-desc"
                    CFRelease(cfErr)
                    s
                } else "no-cferror"
                println("[DeviceCrypto] createSignature(Message) failed: $desc")
                null
            } else {
                (CFBridgingRelease(sig) as NSData).base64EncodedStringWithOptions(0u)
            }
        }
    }

    /**
     * Fallback: SecKeyRawSign with kSecPaddingPKCS1SHA256 (0x8004).
     * This lower-level API may succeed on iOS Simulator where SecKeyCreateSignature
     * returns -50 for software RSA keys.
     * With kSecPaddingPKCS1SHA256 the input must be the 32-byte raw SHA-256 digest;
     * the function internally adds the PKCS#1 v1.5 DigestInfo ASN.1 wrap.
     */
    private fun trySignWithRawSign(key: SecKeyRef, hashBytes: ByteArray): String? {
        val signatureBuffer = ByteArray(256)  // RSA-2048 → 256-byte signature
        return memScoped {
            val sigLen = alloc<platform.posix.size_tVar>()
            sigLen.value = 256.convert()
            val status = hashBytes.usePinned { hash ->
                signatureBuffer.usePinned { sig ->
                    SecKeyRawSign(
                        key,
                        0x8004u,  // kSecPaddingPKCS1SHA256
                        hash.addressOf(0).reinterpret(),
                        32.convert(),
                        sig.addressOf(0).reinterpret(),
                        sigLen.ptr
                    )
                }
            }
            if (status != 0) {
                println("[DeviceCrypto] SecKeyRawSign failed: OSStatus=$status")
                null
            } else {
                signatureBuffer.copyOfRange(0, sigLen.value.toInt()).toNSData()
                    .base64EncodedStringWithOptions(0u)
            }
        }
    }

    /**
     * Returns the private SecKeyRef from the Keychain, or generates a fresh RSA-2048 pair.
     *
     * KEY STRATEGY CHANGE: Keep the key permanently in the Keychain (kSecAttrIsPermanent=true)
     * and retrieve it on subsequent launches via SecItemCopyMatching.
     *
     * The previous approach (export PKCS#1 bytes → NSUserDefaults → SecKeyCreateWithData)
     * broke SecKeyCreateSignature with OSStatus -50 "algorithm not supported by the key"
     * because SecKeyCreateWithData produces a software key that lacks the signing capability
     * flags present on Keychain-backed keys.
     *
     * ALL kSec* attribute keys/values are raw CFString literals (kSec* globals are null
     * in Kotlin/Native CInterop on iOS 26 Simulator).
     *
     * Raw string reference:
     *   "type"/"42"    = kSecAttrKeyType / kSecAttrKeyTypeRSA
     *   "bsiz"         = kSecAttrKeySizeInBits
     *   "prvs"         = kSecPrivateKeyAttrs
     *   "perm"         = kSecAttrIsPermanent
     *   "atag"         = kSecAttrApplicationTag
     *   "class"/"keys" = kSecClass / kSecClassKey
     *   "kcls"/"1"     = kSecAttrKeyClass / kSecAttrKeyClassPrivate
     *   "r_ref"        = kSecReturnRef
     *   "mlmt"/"mlm1"  = kSecMatchLimit / kSecMatchLimitOne
     */
    private fun getOrCreateKey(): SecKeyRef {
        cachedKey?.let { return it }

        // Key tag versioned — "v2" ensures stale keys from pre-signing-fix builds are
        // ignored and a fresh key with kSecAttrCanSign=true is generated automatically.
        val appTag = "vs_key_v2_$alias".encodeToByteArray().toNSData()

        // ── 1. Try to load the existing Keychain-backed key ───────────────────
        val existingKey = loadKeyFromKeychain(appTag)
        if (existingKey != null) {
            cachedKey = existingKey
            return existingKey
        }

        // ── 2. Delete any stale/partial Keychain entry before generating ──────
        val delQuery = NSMutableDictionary()
        delQuery.setObject("keys" as NSString, forKey = "class" as NSCopyingProtocol)
        delQuery.setObject("42"   as NSString, forKey = "type"  as NSCopyingProtocol)
        delQuery.setObject(appTag,             forKey = "atag"  as NSCopyingProtocol)
        val delRef = delQuery.asCFDictionary()
        val delStatus = SecItemDelete(delRef)
        CFRelease(delRef)
        println("[DeviceCrypto] pre-generation Keychain delete status=$delStatus")

        // ── 3. Generate RSA-2048 (Keychain-backed, permanent) ─────────────────
        val privAttrs = NSMutableDictionary()
        privAttrs.setObject(NSNumber.numberWithBool(true), forKey = "perm" as NSCopyingProtocol)
        privAttrs.setObject(appTag,                        forKey = "atag" as NSCopyingProtocol)
        // kSecAttrCanSign — MUST be true for SecKeyCreateSignature to succeed on
        // Keychain-backed RSA keys.  Without this the Simulator returns OSStatus -50.
        privAttrs.setObject(NSNumber.numberWithBool(true), forKey = "sign" as NSCopyingProtocol)

        val genAttrs = NSMutableDictionary()
        genAttrs.setObject("42" as NSString,              forKey = "type" as NSCopyingProtocol)
        genAttrs.setObject(NSNumber.numberWithInt(2048),  forKey = "bsiz" as NSCopyingProtocol)
        genAttrs.setObject(privAttrs,                     forKey = "prvs" as NSCopyingProtocol)

        println("[DeviceCrypto] generating new RSA-2048 (permanent Keychain key, alias=$alias)")
        val genAttrsRef = genAttrs.asCFDictionary()
        val privKey = memScoped {
            val err = alloc<CFErrorRefVar>()
            val key = SecKeyCreateRandomKey(genAttrsRef, err.ptr)
            if (key == null) {
                val cfErr = err.value
                val desc = if (cfErr != null) {
                    val descRef = CFErrorCopyDescription(cfErr)
                    val s = if (descRef != null)
                        (CFBridgingRelease(descRef) as? NSString)?.toString() ?: "no-desc"
                    else "null-desc"
                    CFRelease(cfErr)
                    s
                } else "no-cferror"
                println("[DeviceCrypto] SecKeyCreateRandomKey returned null: $desc")
            } else {
                println("[DeviceCrypto] SecKeyCreateRandomKey succeeded!")
            }
            key
        }
        CFRelease(genAttrsRef)
        privKey ?: error("RSA-2048 key generation failed on iOS — check logs above")

        // Key is already in Keychain (perm=true). Keep in-memory reference.
        cachedKey = privKey
        println("[DeviceCrypto] RSA-2048 key ready (alias=$alias)")

        // ── 4. Self-test: verify signing works immediately after generation ─────
        val testHash = sha256("streamvault_selftest".encodeToByteArray())
        val testPayload = "streamvault_selftest".encodeToByteArray()
        val testSig = trySignWithCreateSignature(privKey, testHash)
        val testLabel = when {
            testSig != null -> "createSignature(Digest)"
            else -> {
                val m = trySignWithCreateSignatureMessage(privKey, testPayload)
                if (m != null) "createSignature(Message)" else null
            }
        } ?: run {
            val r = trySignWithRawSign(privKey, testHash)
            if (r != null) "SecKeyRawSign" else null
        }
        if (testLabel != null) {
            println("[DeviceCrypto] self-test sign: OK via $testLabel")
        } else {
            println("[DeviceCrypto] self-test sign: FAILED — signing will not work!")
        }

        return privKey
    }

    /**
     * Load the private key SecKeyRef directly from the iOS Keychain.
     * Keychain-backed keys retain full Security-framework capabilities (signing, etc.)
     * without the restrictions of keys reconstructed via SecKeyCreateWithData.
     *
     * Raw strings:
     *   "r_ref" = kSecReturnRef
     *   "mlmt"  = kSecMatchLimit,  "mlm1" = kSecMatchLimitOne
     */
    private fun loadKeyFromKeychain(appTag: NSData): SecKeyRef? {
        val query = NSMutableDictionary()
        query.setObject("keys" as NSString,              forKey = "class" as NSCopyingProtocol)
        query.setObject("42"   as NSString,              forKey = "type"  as NSCopyingProtocol)
        query.setObject("1"    as NSString,              forKey = "kcls"  as NSCopyingProtocol)
        query.setObject(appTag,                          forKey = "atag"  as NSCopyingProtocol)
        query.setObject(NSNumber.numberWithBool(true),   forKey = "r_ref" as NSCopyingProtocol)
        query.setObject("mlm1" as NSString,              forKey = "mlmt"  as NSCopyingProtocol)

        val queryRef = query.asCFDictionary()
        return memScoped {
            val resultVar = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(queryRef, resultVar.ptr)
            CFRelease(queryRef)
            if (status == 0) {
                val ref = resultVar.value
                @Suppress("UNCHECKED_CAST")
                if (ref != null) {
                    println("[DeviceCrypto] loadKeyFromKeychain: loaded Keychain-backed key")
                    ref as? SecKeyRef
                } else {
                    println("[DeviceCrypto] loadKeyFromKeychain: status=0 but ref is null")
                    null
                }
            } else {
                println("[DeviceCrypto] loadKeyFromKeychain: SecItemCopyMatching status=$status")
                null
            }
        }
    }
}
