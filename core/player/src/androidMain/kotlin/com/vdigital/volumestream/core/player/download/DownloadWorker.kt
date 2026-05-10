package com.vdigital.volumestream.core.player.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vditital.data.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_URL     = "url"
        const val KEY_ID      = "id"
        const val KEY_TITLE   = "title"
        const val KEY_ARTWORK = "artwork"
        const val KEY_AUTHORIZATION = "authorization"
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_AES_KEY_B64 = "aes_key_b64"
        const val KEY_PROGRESS  = "progress"
        const val KEY_LOCAL_PATH = "local_path"
        const val PREFS = "vs_downloads"
        private const val NOTIF_CHANNEL_ID = "vs_download_channel"
        private const val NOTIF_ID = 7001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id       = inputData.getString(KEY_ID)      ?: return@withContext Result.failure()
        val url      = inputData.getString(KEY_URL)     ?: return@withContext Result.failure()
        val title    = inputData.getString(KEY_TITLE)   ?: "Downloading…"
        val artwork  = inputData.getString(KEY_ARTWORK) ?: ""
        val authorization = inputData.getString(KEY_AUTHORIZATION)
        val sessionToken = inputData.getString(KEY_SESSION_TOKEN)
        val aesKey = inputData.getString(KEY_AES_KEY_B64)
            ?.takeIf { it.isNotBlank() }
            ?.let { encoded -> runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() }
            ?.takeIf { it.size == 16 }

        setForeground(createForegroundInfo(title, 0))

        val downloadsDir = File(context.filesDir, "downloads").also { it.mkdirs() }
        var outputFile: File? = null

        try {
            val probe = openConnection(url, authorization, sessionToken)
            val extension = inferExtension(url, probe.contentType)
            probe.disconnect()
            outputFile = if (extension == ".mpd") {
                File(downloadsDir, id).also { it.mkdirs() }.resolve("manifest.mpd")
            } else {
                File(downloadsDir, "$id$extension")
            }

            if (extension == ".m3u8") {
                setProgress(workDataOf(KEY_PROGRESS to 0f, KEY_ID to id))
                setForeground(createForegroundInfo(title, 0))
                packageHlsLocally(
                    rootUrl = url,
                    rootOutput = outputFile,
                    downloadsDir = downloadsDir,
                    authorization = authorization,
                    sessionToken = sessionToken
                )
                val localUri = Uri.fromFile(outputFile).toString()
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(id, localUri)
                    .putString("_t_$id", title)
                    .putString("_a_$id", artwork)
                    .putString("_u_$id", url)
                    .apply()
                setProgress(workDataOf(KEY_PROGRESS to 100f, KEY_ID to id))
                setForeground(createForegroundInfo(title, 100))
                return@withContext Result.success(workDataOf(KEY_LOCAL_PATH to localUri))
            }

            if (extension == ".mpd") {
                setProgress(workDataOf(KEY_PROGRESS to 0f, KEY_ID to id))
                setForeground(createForegroundInfo(title, 0))
                packageDashLocally(
                    mediaId = id,
                    rootUrl = url,
                    rootOutput = outputFile,
                    authorization = authorization,
                    sessionToken = sessionToken,
                    aesKey = aesKey
                )
                val localUri = Uri.fromFile(outputFile).toString()
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(id, localUri)
                    .putString("_t_$id", title)
                    .putString("_a_$id", artwork)
                    .putString("_u_$id", url)
                    .apply()
                setProgress(workDataOf(KEY_PROGRESS to 100f, KEY_ID to id))
                setForeground(createForegroundInfo(title, 100))
                return@withContext Result.success(workDataOf(KEY_LOCAL_PATH to localUri))
            }

            val connection = openConnection(url, authorization, sessionToken)
            val totalBytes = connection.contentLengthLong
            var downloaded = 0L
            var lastReportedProgress = -1
            // Update notification/progress every 5 % to avoid excessive Binder IPC.
            val progressStepSize = 5

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (isStopped) {
                            outputFile?.delete()
                            return@withContext Result.failure()
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = if (totalBytes > 0) (downloaded * 100f / totalBytes).toInt() else 0
                        // Only report when progress crosses a step boundary.
                        val reportedBucket = (progress / progressStepSize) * progressStepSize
                        val lastBucket = if (lastReportedProgress < 0) -1
                                         else (lastReportedProgress / progressStepSize) * progressStepSize
                        if (reportedBucket != lastBucket) {
                            lastReportedProgress = progress
                            setProgress(workDataOf(KEY_PROGRESS to progress.toFloat(), KEY_ID to id))
                            setForeground(createForegroundInfo(title, progress))
                        }
                    }
                }
            }
            connection.disconnect()

            val completedFile = outputFile ?: return@withContext Result.failure()
            val localUri = Uri.fromFile(completedFile).toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(id, localUri)
                .putString("_t_$id", title)
                .putString("_a_$id", artwork)
                .putString("_u_$id", url)
                .apply()

            Result.success(workDataOf(KEY_LOCAL_PATH to localUri))
        } catch (e: Exception) {
            AppLogger.e("DownloadWorker", "Download failed", e)
            outputFile?.delete()
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun packageHlsLocally(
        rootUrl: String,
        rootOutput: File,
        downloadsDir: File,
        authorization: String?,
        sessionToken: String?
    ) {
        val uriMap = mutableMapOf<String, String>()
        val visitedPlaylists = mutableSetOf<String>()

        fun localNameFor(remoteUrl: String, fallbackExt: String = ".bin"): String {
            uriMap[remoteUrl]?.let { return it }
            val path = runCatching { URI(remoteUrl).path }.getOrNull().orEmpty()
            val base = path.substringAfterLast('/').ifBlank { "asset$fallbackExt" }
            val dot = base.lastIndexOf('.')
            val stem = if (dot > 0) base.substring(0, dot) else base
            val ext = if (dot > 0) base.substring(dot) else fallbackExt
            val hash = sha1Short(remoteUrl)
            val safe = stem.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val name = "${safe}_$hash$ext"
            uriMap[remoteUrl] = name
            return name
        }

        fun downloadBinary(remoteUrl: String, localName: String) {
            val out = File(downloadsDir, localName)
            if (out.exists()) return
            val conn = openConnection(remoteUrl, authorization, sessionToken)
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
        }

        val keyUriRegex = Regex("URI=\"([^\"]+)\"")

        fun rewritePlaylist(remoteUrl: String, outFile: File, depth: Int) {
            if (depth > 4 || !visitedPlaylists.add(remoteUrl)) return
            val conn = openConnection(remoteUrl, authorization, sessionToken)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val parent = URL(remoteUrl)
            val rewritten = text.lineSequence().map { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    return@map line
                }

                if (trimmed.startsWith("#")) {
                    val keyMatch = keyUriRegex.find(line)
                    if (keyMatch != null) {
                        val keyUri = keyMatch.groupValues[1]
                        val resolved = URL(parent, keyUri).toString()
                        val keyName = localNameFor(resolved, ".key")
                        downloadBinary(resolved, keyName)
                        return@map line.replace(keyUri, keyName)
                    }
                    return@map line
                }

                val resolved = URL(parent, trimmed).toString()
                if (trimmed.lowercase().endsWith(".m3u8")) {
                    val localName = localNameFor(resolved, ".m3u8")
                    rewritePlaylist(resolved, File(downloadsDir, localName), depth + 1)
                    localName
                } else {
                    val fallbackExt = when {
                        trimmed.lowercase().endsWith(".ts") -> ".ts"
                        trimmed.lowercase().endsWith(".m4s") -> ".m4s"
                        else -> ".bin"
                    }
                    val localName = localNameFor(resolved, fallbackExt)
                    downloadBinary(resolved, localName)
                    localName
                }
            }.joinToString("\n")

            outFile.writeText(rewritten)
        }

        rewritePlaylist(rootUrl, rootOutput, depth = 0)
    }

    private suspend fun packageDashLocally(
        mediaId: String,
        rootUrl: String,
        rootOutput: File,
        authorization: String?,
        sessionToken: String?,
        aesKey: ByteArray?
    ) {
        val rootDir = rootOutput.parentFile ?: error("Missing output parent directory")
        rootDir.deleteRecursively()
        rootDir.mkdirs()
        val mpdText = openConnection(rootUrl, authorization, sessionToken).useText()
        val segmentTemplates = parseSegmentTemplates(mpdText)

        val assetsByLocalName = linkedMapOf<String, String>()
        segmentTemplates.forEach { template ->
            val isAudio = template.isAudio
            val initLocal = if (isAudio) "audio_init.mp4" else "init.mp4"
            assetsByLocalName.putIfAbsent(initLocal, template.initialization)

            val remoteSegments = expandMediaTemplate(template.media, template.startNumber, template.segmentCount)
            remoteSegments.forEachIndexed { idx, remoteSegment ->
                val number = template.startNumber + idx
                val localSegment = if (isAudio) {
                    "audio_${number.toFiveDigits()}.m4s"
                } else {
                    "chunk${number.toFiveDigits()}.m4s"
                }
                assetsByLocalName.putIfAbsent(localSegment, remoteSegment)
            }
        }

        val assets = assetsByLocalName.entries.toList()
        assets.forEachIndexed { index, entry ->
            val out = File(rootDir, entry.key)
            out.parentFile?.mkdirs()
            if (!out.exists()) {
                downloadDashAsset(
                    mediaId = mediaId,
                    rootUrl = rootUrl,
                    assetPath = entry.value,
                    outputFile = out,
                    authorization = authorization,
                    sessionToken = sessionToken,
                    aesKey = aesKey
                )
            }
            val progress = if (assets.isNotEmpty()) (((index + 1) * 100f) / assets.size).toInt() else 100
            setProgress(workDataOf(KEY_PROGRESS to progress.toFloat(), KEY_ID to mediaId))
        }

        rootOutput.writeText(rewriteDashManifestToLocal(mpdText, segmentTemplates))
    }

    private data class SegmentTemplateInfo(
        val initialization: String,
        val media: String,
        val startNumber: Int,
        val segmentCount: Int,
        val isAudio: Boolean
    )

    private fun parseSegmentTemplates(mpdText: String): List<SegmentTemplateInfo> {
        val templateRegex = Regex("""<SegmentTemplate\b([^>]*)>(.*?)</SegmentTemplate>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val sRegex = Regex("""<S\b([^>]*)/>""", RegexOption.IGNORE_CASE)
        val rRegex = Regex("""\br="(-?\d+)"""")

        fun readAttr(attrs: String, name: String): String? {
            val attrRegex = Regex("""\b$name="([^"]+)""", RegexOption.IGNORE_CASE)
            return attrRegex.find(attrs)?.groupValues?.get(1)
        }

        return templateRegex.findAll(mpdText).map { match ->
            val attrs = match.groupValues[1]
            val timeline = match.groupValues[2]
            val segmentCount = sRegex.findAll(timeline).sumOf { sMatch ->
                val attrs = sMatch.groupValues[1]
                val r = rRegex.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (r >= 0) r + 1 else 1
            }.coerceAtLeast(1)

            val initialization = readAttr(attrs, "initialization") ?: return@map null
            val media = readAttr(attrs, "media") ?: return@map null
            val startNumber = readAttr(attrs, "startNumber")?.toIntOrNull() ?: 1
            val lowered = "$initialization $media".lowercase()
            val isAudio = lowered.contains("stream=audio") || lowered.contains("audio")

            SegmentTemplateInfo(
                initialization = initialization,
                media = media,
                startNumber = startNumber,
                segmentCount = segmentCount,
                isAudio = isAudio
            )
        }.mapNotNull { it }.toList()
    }

    private fun rewriteDashManifestToLocal(mpdText: String, templates: List<SegmentTemplateInfo>): String {
        var rewritten = mpdText
        templates.forEach { template ->
            val localInit = if (template.isAudio) "audio_init.mp4" else "init.mp4"
            val localMedia = if (template.isAudio) "audio_${'$'}Number%05d${'$'}.m4s" else "chunk${'$'}Number%05d${'$'}.m4s"
            rewritten = rewritten
                .replace("initialization=\"${template.initialization}\"", "initialization=\"$localInit\"")
                .replace("media=\"${template.media}\"", "media=\"$localMedia\"")
        }
        return rewritten
    }

    private fun Int.toFiveDigits(): String = String.format(Locale.US, "%05d", this)

    private fun expandMediaTemplate(template: String, startNumber: Int, count: Int): List<String> {
        val numberPattern = Regex("\\${'$'}Number(%0(\\d+)d)?\\${'$'}")
        val matcher = numberPattern.find(template) ?: return listOf(template)
        val width = matcher.groupValues.getOrNull(2)?.toIntOrNull()
        return (startNumber until (startNumber + count)).map { value ->
            val replacement = if (width != null) {
                String.format(Locale.US, "%0${width}d", value)
            } else {
                value.toString()
            }
            template.replaceRange(matcher.range, replacement)
        }
    }

    private fun downloadDashAsset(
        mediaId: String,
        rootUrl: String,
        assetPath: String,
        outputFile: File,
        authorization: String?,
        sessionToken: String?,
        aesKey: ByteArray?
    ) {
        val candidates = buildDashAssetCandidates(rootUrl, mediaId, assetPath)
        var lastError: Exception? = null
        for (candidate in candidates) {
            try {
                val conn = openConnection(candidate, authorization, sessionToken)
                val payload = conn.inputStream.use { it.readBytes() }
                val bytesToWrite = if (isDashProxyAsset(candidate)) {
                    val key = aesKey ?: throw IllegalStateException("Missing AES key for DASH proxy asset")
                    decryptDashProxyPayload(payload, key, candidate)
                } else {
                    payload
                }
                conn.disconnect()
                FileOutputStream(outputFile).use { output -> output.write(bytesToWrite) }
                return
            } catch (e: Exception) {
                lastError = e
                outputFile.delete()
            }
        }
        throw IllegalStateException("Failed downloading DASH asset: $assetPath", lastError)
    }

    private fun isDashProxyAsset(url: String): Boolean =
        url.contains("/api/v1/proxy/dash/", ignoreCase = true)

    private fun decryptDashProxyPayload(encrypted: ByteArray, aesKey: ByteArray, sourceUrl: String): ByteArray {
        if (encrypted.size < 28) {
            throw IllegalStateException("Encrypted DASH payload too small: ${encrypted.size} from $sourceUrl")
        }
        val nonce = encrypted.copyOf(12)
        val blob = encrypted.copyOfRange(12, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(128, nonce)
        )
        return cipher.doFinal(blob)
    }

    private fun buildDashAssetCandidates(rootUrl: String, mediaId: String, assetPath: String): List<String> {
        val root = URL(rootUrl)
        val origin = "${root.protocol}://${root.host}${if (root.port != -1) ":${root.port}" else ""}"
        val decodedAsset = assetPath.replace("&amp;", "&")
        val normalizedAsset = decodedAsset.trimStart('/')
        return listOf(
            URL(root, decodedAsset).toString(),
            URL(root, normalizedAsset).toString(),
            "$origin/segments/$mediaId/dash/$normalizedAsset"
        ).distinct()
    }

    private fun HttpURLConnection.useText(): String {
        return try {
            inputStream.bufferedReader().use { it.readText() }
        } finally {
            disconnect()
        }
    }

    private fun openConnection(url: String, authorization: String?, sessionToken: String?): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            if (!authorization.isNullOrBlank()) {
                setRequestProperty("Authorization", authorization)
            }
            if (!sessionToken.isNullOrBlank()) {
                setRequestProperty("X-Session-Token", sessionToken)
            }
            connect()
        }
    }

    private fun sha1Short(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    private fun inferExtension(url: String, contentType: String?): String {
        val path = runCatching { URL(url).path.lowercase() }.getOrDefault(url.lowercase())
        if (path.endsWith(".m3u8")) return ".m3u8"
        if (path.endsWith(".mpd")) return ".mpd"
        if (path.endsWith(".mp4")) return ".mp4"

        val type = contentType?.lowercase().orEmpty()
        return when {
            type.contains("mpegurl") || type.contains("x-mpegurl") -> ".m3u8"
            type.contains("dash+xml") -> ".mpd"
            else -> ".mp4"
        }
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        val notifManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notifManager.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
            notifManager.createNotificationChannel(
                NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val notification = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (progress > 0) "$progress%" else "Starting…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()

        return ForegroundInfo(
            NOTIF_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
