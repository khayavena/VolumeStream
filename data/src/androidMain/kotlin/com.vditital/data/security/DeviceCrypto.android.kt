package com.vditital.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature

private const val DEFAULT_ALIAS = "streamvault_device_key"
private const val PROVIDER      = "AndroidKeyStore"

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DeviceCrypto(
    private val context: Context,
    private val alias: String = DEFAULT_ALIAS
) {

    actual fun getOrCreatePublicKeyB64(): String {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (!ks.containsAlias(alias)) generateKeyPair()
        val publicKey = ks.getCertificate(alias).publicKey
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    actual fun signPayload(payload: String): String {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val privateKey = ks.getKey(alias, null) as PrivateKey
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(payload.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
    }

    private fun generateKeyPair() {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build()
            )
        }.generateKeyPair()
    }
}

