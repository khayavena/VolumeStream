package com.vdigital.volumestream.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts AES-128-GCM DASH segments from StreamVault's proxy endpoint.
 *
 * Wire format returned by  GET /api/v1/proxy/dash/{mediaId}/{idx}?t=…
 *
 *   ┌──────────────┬─────────────────────────────┬────────────┐
 *   │  nonce 12 B  │   AES-GCM ciphertext  (N B) │  tag 16 B  │
 *   └──────────────┴─────────────────────────────┴────────────┘
 *
 * javax.crypto expects  ciphertext‖tag  as a single [Cipher.doFinal] input and
 * returns the plaintext, automatically verifying the 128-bit authentication tag.
 */
@OptIn(UnstableApi::class)
internal class AesGcmDecryptingDataSource(
    private val aesKey: ByteArray,
    private val httpFactory: DefaultHttpDataSource.Factory
) : DataSource {

    private var openedUri: Uri? = null
    private var decryptedBuf: ByteArray? = null
    private var readPos: Int = 0

    override fun open(dataSpec: DataSpec): Long {
        openedUri = dataSpec.uri

        // Fetch the full encrypted segment (AES-GCM cannot be split).
        val httpSource = httpFactory.createDataSource()

        // Drop any byte-range from the DataSpec — we need the complete ciphertext.
        val fullSpec = dataSpec.buildUpon().setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build()
        val encrypted: ByteArray = try {
            httpSource.open(fullSpec)
            httpSource.readFully()
        } finally {
            httpSource.close()
        }

        if (encrypted.size < 28) {
            throw IOException(
                "StreamVault DASH segment too small for GCM nonce + tag: ${encrypted.size} B"
            )
        }

        // nonce = bytes[0..11],  ciphertext+tag = bytes[12..end]
        val nonce = encrypted.copyOf(12)
        val blob  = encrypted.copyOfRange(12, encrypted.size)

        decryptedBuf = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                GCMParameterSpec(128, nonce)
            )
            cipher.doFinal(blob)
        } catch (e: Exception) {
            throw IOException("AES-128-GCM decryption failed for ${dataSpec.uri}", e)
        }

        readPos = dataSpec.position.coerceIn(0L, decryptedBuf!!.size.toLong()).toInt()
        return (decryptedBuf!!.size - readPos).toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val data = decryptedBuf ?: throw IOException("DataSource not opened")
        if (readPos >= data.size) return C.RESULT_END_OF_INPUT
        val toRead = minOf(length, data.size - readPos)
        System.arraycopy(data, readPos, buffer, offset, toRead)
        readPos += toRead
        return toRead
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        decryptedBuf = null
        openedUri = null
    }

    override fun addTransferListener(transferListener: TransferListener) { /* no-op */ }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun DataSource.readFully(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            if (n > 0) out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
