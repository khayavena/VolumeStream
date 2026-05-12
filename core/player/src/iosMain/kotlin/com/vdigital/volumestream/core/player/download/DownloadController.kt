package com.vdigital.volumestream.core.player.download

import com.vditital.data.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.darwin.NSObject
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.setValue
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.darwin.NSUInteger
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.rewind

@OptIn(ExperimentalForeignApi::class)
private class VsDownloadDelegate(
    private val onFinished: (taskId: NSUInteger, tmpUrl: NSURL?, error: NSError?) -> Unit,
    private val onProgress: (taskId: NSUInteger, written: Long, total: Long) -> Unit
) : NSObject(), NSURLSessionDownloadDelegateProtocol {

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL
    ) {
        onFinished(downloadTask.taskIdentifier, didFinishDownloadingToURL, null)
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long
    ) {
        onProgress(downloadTask.taskIdentifier, totalBytesWritten, totalBytesExpectedToWrite)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?
    ) {
        if (didCompleteWithError != null) {
            onFinished(task.taskIdentifier, null, didCompleteWithError)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual class DownloadController {

    private val prefs = NSUserDefaults.standardUserDefaults

    // Guarded by `lock` – always access inside `withLock { }`.
    private val lock = platform.Foundation.NSLock()
    private val stateFlows  = mutableMapOf<String, MutableStateFlow<DownloadState>>()
    private val taskIdToId  = mutableMapOf<NSUInteger, String>()
    private val idToTask    = mutableMapOf<String, NSURLSessionDownloadTask>()
    private val idToExtension = mutableMapOf<String, String>()

    private fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }

    private val delegateQueue: NSOperationQueue = NSOperationQueue().apply {
        maxConcurrentOperationCount = 1   // serial
        name = "com.vdigital.volumestream.download.delegate"
    }

    // ---- session management -------------------------------------------------
    /** true = allow cellular, false = Wi-Fi only */
    private var currentAllowsCellular: Boolean = true

    private fun makeSession(allowCellular: Boolean): NSURLSession {
        val config = NSURLSessionConfiguration.defaultSessionConfiguration()
        config.allowsCellularAccess = allowCellular
        return NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate      = delegate,
            delegateQueue = delegateQueue
        )
    }

    private var session: NSURLSession = makeSession(allowCellular = true)
    // -------------------------------------------------------------------------    }

    private val delegate = VsDownloadDelegate(
        onFinished = { taskId, tmpUrl, error ->
            // Running on delegateQueue (background) — file I/O is safe here.
            val id = withLock { taskIdToId.remove(taskId) }
            if (id != null) {
                withLock { idToTask.remove(id) }
                val ext = withLock { idToExtension.remove(id) }
                val flow = withLock { stateFlows[id] }
                if (flow != null) {
                    if (error != null || tmpUrl == null) {
                        flow.value = DownloadState.Failed(error?.localizedDescription ?: "Download failed")
                    } else {
                        val dest = destPathFor(id, ext)
                        if (dest == null) {
                            flow.value = DownloadState.Failed("No destination")
                        } else {
                            val fm = NSFileManager.defaultManager
                            fm.createDirectoryAtPath(
                                dest.substringBeforeLast("/"),
                                withIntermediateDirectories = true,
                                attributes = null,
                                error = null
                            )
                            fm.removeItemAtPath(dest, error = null)
                            if (fm.moveItemAtURL(tmpUrl, toURL = NSURL.fileURLWithPath(dest), error = null)) {
                                if (ext.equals(".m3u8", ignoreCase = true)) {
                                    val sourceUrl = prefs.stringForKey("vs_dl_u_$id")
                                    if (!sourceUrl.isNullOrBlank()) {
                                        rewriteManifestForLocalPlayback(dest, sourceUrl)
                                    }
                                }
                                prefs.setObject(dest, forKey = prefKey(id))
                                prefs.synchronize()   // flush to disk immediately
                                flow.value = DownloadState.Completed
                            } else {
                                flow.value = DownloadState.Failed("Move failed")
                            }
                        }
                    }
                }
            }
        },
        onProgress = { taskId, written, total ->
            val id   = withLock { taskIdToId[taskId] }
            val flow = if (id != null) withLock { stateFlows[id] } else null
            if (id != null && flow != null) {
                val progress = if (total > 0) written.toFloat() / total else 0f
                flow.value = DownloadState.Downloading(progress)
            }
        }
    )

    private fun prefKey(id: String) = "vs_dl_$id"

    @Suppress("UNCHECKED_CAST")
    private fun docsPath(): String? =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String

    private fun destPathFor(id: String, extension: String? = null): String? {
        val ext = when {
            extension.isNullOrBlank() -> ".mp4"
            extension.startsWith(".") -> extension
            else -> ".$extension"
        }
        return docsPath()?.let { "$it/vs_downloads/$id$ext" }
    }

    private fun readUtf8File(path: String): String? {
        val file = fopen(path, "rb") ?: return null
        try {
            fseek(file, 0, SEEK_END)
            val fileSize = ftell(file)
            if (fileSize < 0L) return null
            rewind(file)
            if (fileSize == 0L) return ""

            val buffer = ByteArray(fileSize.toInt())
            val bytesRead = buffer.usePinned {
                fread(it.addressOf(0), 1.convert(), buffer.size.convert(), file).toInt()
            }
            if (bytesRead <= 0) return null
            return if (bytesRead == buffer.size) {
                buffer.decodeToString()
            } else {
                buffer.copyOf(bytesRead).decodeToString()
            }
        } finally {
            fclose(file)
        }
    }

    private fun writeUtf8File(path: String, content: String): Boolean {
        val file = fopen(path, "wb") ?: return false
        try {
            val bytes = content.encodeToByteArray()
            val bytesWritten = bytes.usePinned {
                fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt()
            }
            return bytesWritten == bytes.size
        } finally {
            fclose(file)
        }
    }

    private fun rewriteManifestForLocalPlayback(localPath: String, sourceUrl: String) {
        val baseUrl = NSURL.URLWithString(sourceUrl) ?: return
        val manifest = readUtf8File(localPath) ?: return
        val keyUriRegex = Regex("URI=\"([^\"]+)\"")

        fun absolutize(ref: String): String {
            val candidate = ref.trim()
            if (candidate.isEmpty()) return candidate
            val lower = candidate.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file://")) {
                return candidate
            }
            return NSURL.URLWithString(candidate, relativeToURL = baseUrl)?.absoluteString ?: candidate
        }

        val rewritten = manifest.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                line
            } else if (trimmed.startsWith("#")) {
                val match = keyUriRegex.find(line)
                if (match != null) {
                    val original = match.groupValues[1]
                    line.replace(original, absolutize(original))
                } else {
                    line
                }
            } else {
                absolutize(trimmed)
            }
        }

        if (rewritten != manifest && !writeUtf8File(localPath, rewritten)) {
            AppLogger.w("DownloadController.iOS", "Failed to rewrite HLS manifest for local playback: $localPath")
        }
    }

    private fun hasStaleSignedHlsUrls(path: String): Boolean {
        if (!path.lowercase().endsWith(".m3u8")) return false
        val manifest = readUtf8File(path) ?: return false
        return manifest.lineSequence().any { line ->
            val trimmed = line.trim()
            trimmed.startsWith("http://") &&
                trimmed.contains("/manifest/hls/") &&
                !trimmed.contains("?sid=")
        }
    }

    private fun flowFor(id: String): MutableStateFlow<DownloadState> =
        withLock {
            stateFlows.getOrPut(id) {
                MutableStateFlow(
                    if (prefs.stringForKey(prefKey(id)) != null) DownloadState.Completed
                    else DownloadState.Idle
                )
            }
        }

    actual fun download(
        id: String,
        url: String,
        title: String,
        artworkUrl: String,
        wifiOnly: Boolean,
        headers: Map<String, String>
    ) {
        val flow = flowFor(id)
        if (flow.value == DownloadState.Completed ||
            flow.value is DownloadState.Downloading ||
            flow.value == DownloadState.Queued
        ) return
        val nsUrl = NSURL.URLWithString(url) ?: run {
            flow.value = DownloadState.Failed("Invalid URL"); return
        }
        // Recreate the NSURLSession only when the cellular policy changes AND no
        // downloads are currently active (to avoid disrupting in-flight tasks).
        val needCellular = !wifiOnly
        withLock {
            if (needCellular != currentAllowsCellular && idToTask.isEmpty()) {
                currentAllowsCellular = needCellular
                session.finishTasksAndInvalidate()
                session = makeSession(needCellular)
            }
            idToExtension[id] = inferExtension(url)
        }
        prefs.setObject(title, forKey = "vs_dl_t_$id")
        prefs.setObject(artworkUrl, forKey = "vs_dl_a_$id")
        prefs.setObject(url, forKey = "vs_dl_u_$id")
        flow.value = DownloadState.Downloading(0f)
        val currentSession = withLock { session }
        val task = if (headers.isEmpty()) {
            currentSession.downloadTaskWithURL(nsUrl)
        } else {
            val request = NSMutableURLRequest.requestWithURL(nsUrl)
            headers.forEach { (key, value) ->
                request.setValue(value, forHTTPHeaderField = key)
            }
            currentSession.downloadTaskWithRequest(request)
        }
        withLock {
            taskIdToId[task.taskIdentifier] = id
            idToTask[id] = task
        }
        task.resume()
    }

    actual fun cancel(id: String) {
        val task = withLock {
            idToTask.remove(id).also { t ->
                if (t != null) taskIdToId.entries.firstOrNull { it.value == id }
                    ?.let { taskIdToId.remove(it.key) }
            }
        }
        task?.cancel()
        withLock { stateFlows[id] }?.value = DownloadState.Idle
    }

    actual fun remove(id: String) {
        // cancel() already cancels the NSURLSession task, clears the task maps,
        // and sets the stateFlow to Idle — so we only need to clean up storage here.
        cancel(id)
        prefs.stringForKey(prefKey(id))?.let { path ->
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
        prefs.removeObjectForKey(prefKey(id))
        prefs.removeObjectForKey("vs_dl_t_$id")
        prefs.removeObjectForKey("vs_dl_a_$id")
        prefs.removeObjectForKey("vs_dl_u_$id")
        // Flush NSUserDefaults to disk so that isDownloaded(id) returns false
        // immediately in the same run-loop turn (avoids a stale-read race in
        // observeState / flowFor on the next recomposition).
        prefs.synchronize()
    }

    private fun inferExtension(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("/manifest/dash/") || lower.contains(".mpd") -> ".mpd"
            lower.contains(".m3u8") || lower.contains("/manifest/") -> ".m3u8"
            else -> ".mp4"
        }
    }

    actual fun observeState(id: String): Flow<DownloadState> = flowFor(id)

    actual fun getLocalPath(id: String): String? =
        prefs.stringForKey(prefKey(id))?.let { path ->
            val sourceUrl = prefs.stringForKey("vs_dl_u_$id")
            if (sourceUrl != null && path.lowercase().endsWith(".m3u8")) {
                rewriteManifestForLocalPlayback(path, sourceUrl)
            }
            if (hasStaleSignedHlsUrls(path)) {
                AppLogger.w("DownloadController.iOS", "Stale downloaded manifest for $id is missing sid query params; re-download required")
                return@let null
            }
            // Normalise: strip a file:// prefix that may have been written by older
            // code so callers always receive a plain POSIX path.
            if (path.startsWith("file://")) path.removePrefix("file://") else path
        }

    actual fun isDownloaded(id: String): Boolean = getLocalPath(id) != null

    @Suppress("UNCHECKED_CAST")
    actual fun listDownloads(): List<DownloadItem> {
        // Take a snapshot on the calling thread — NSUserDefaults is not thread-safe
        // for bulk reads; callers must dispatch this off Main themselves (already
        // done via Dispatchers.IO in DownloadViewModel.refreshDownloads).
        val dict = prefs.dictionaryRepresentation() as? Map<*, *> ?: return emptyList()
        return dict.entries
            .filter { entry ->
                val key = entry.key as? String ?: return@filter false
                key.startsWith("vs_dl_") && !key.startsWith("vs_dl_t_") && !key.startsWith("vs_dl_a_")
                    && !key.startsWith("vs_dl_u_")
            }
            .mapNotNull { entry ->
                val key  = entry.key as? String ?: return@mapNotNull null
                val id   = key.removePrefix("vs_dl_")
                // Reuse getLocalPath() so stale manifests are filtered out consistently.
                val localPath = getLocalPath(id) ?: return@mapNotNull null
                DownloadItem(
                    id         = id,
                    title      = prefs.stringForKey("vs_dl_t_$id") ?: id,
                    url        = prefs.stringForKey("vs_dl_u_$id") ?: localPath,
                    artworkUrl = prefs.stringForKey("vs_dl_a_$id") ?: "",
                    state      = DownloadState.Completed,
                    localPath  = localPath
                )
            }
    }
}
