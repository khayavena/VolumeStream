package com.vdigital.volumestream.core.player.download

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.darwin.NSObject
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.darwin.NSUInteger

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

@OptIn(ExperimentalForeignApi::class)
actual class DownloadController {

    private val prefs = NSUserDefaults.standardUserDefaults

    // Guarded by `lock` – always access inside `withLock { }`.
    private val lock = platform.Foundation.NSLock()
    private val stateFlows  = mutableMapOf<String, MutableStateFlow<DownloadState>>()
    private val taskIdToId  = mutableMapOf<NSUInteger, String>()
    private val idToTask    = mutableMapOf<String, NSURLSessionDownloadTask>()

    private fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }

    // -------------------------------------------------------------------------
    // Delegate queue: use a dedicated background serial queue instead of
    // mainQueue so that file-system operations (moveItemAtURL) inside
    // onFinished run off the Main thread and do not cause UI jank / ANRs.
    // -------------------------------------------------------------------------
    private val delegateQueue: NSOperationQueue = NSOperationQueue().apply {
        maxConcurrentOperationCount = 1   // serial
        name = "com.vdigital.volumestream.download.delegate"
    }

    private val delegate = VsDownloadDelegate(
        onFinished = { taskId, tmpUrl, error ->
            // Running on delegateQueue (background) — file I/O is safe here.
            val id = withLock { taskIdToId.remove(taskId) }
            if (id != null) {
                withLock { idToTask.remove(id) }
                val flow = withLock { stateFlows[id] }
                if (flow != null) {
                    if (error != null || tmpUrl == null) {
                        flow.value = DownloadState.Failed(error?.localizedDescription ?: "Download failed")
                    } else {
                        val dest = destPathFor(id)
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

    private val session: NSURLSession = NSURLSession.sessionWithConfiguration(
        configuration = NSURLSessionConfiguration.defaultSessionConfiguration(),
        delegate = delegate,
        delegateQueue = delegateQueue           // ← background, NOT mainQueue
    )

    private fun prefKey(id: String) = "vs_dl_$id"

    @Suppress("UNCHECKED_CAST")
    private fun docsPath(): String? =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true) as List<*>)
            .firstOrNull() as? String

    private fun destPathFor(id: String): String? = docsPath()?.let { "$it/vs_downloads/$id.mp4" }

    private fun flowFor(id: String): MutableStateFlow<DownloadState> =
        withLock {
            stateFlows.getOrPut(id) {
                MutableStateFlow(
                    if (prefs.stringForKey(prefKey(id)) != null) DownloadState.Completed
                    else DownloadState.Idle
                )
            }
        }

    actual fun download(id: String, url: String, title: String, artworkUrl: String) {
        val flow = flowFor(id)
        if (flow.value == DownloadState.Completed ||
            flow.value is DownloadState.Downloading ||
            flow.value == DownloadState.Queued
        ) return
        val nsUrl = NSURL.URLWithString(url) ?: run {
            flow.value = DownloadState.Failed("Invalid URL"); return
        }
        prefs.setObject(title, forKey = "vs_dl_t_$id")
        prefs.setObject(artworkUrl, forKey = "vs_dl_a_$id")
        flow.value = DownloadState.Downloading(0f)
        val task = session.downloadTaskWithURL(nsUrl)
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
        // Flush NSUserDefaults to disk so that isDownloaded(id) returns false
        // immediately in the same run-loop turn (avoids a stale-read race in
        // observeState / flowFor on the next recomposition).
        prefs.synchronize()
    }

    actual fun observeState(id: String): Flow<DownloadState> = flowFor(id)

    actual fun getLocalPath(id: String): String? =
        prefs.stringForKey(prefKey(id))?.let { path ->
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
            }
            .mapNotNull { entry ->
                val key  = entry.key as? String ?: return@mapNotNull null
                val raw  = entry.value as? String ?: return@mapNotNull null
                val id   = key.removePrefix("vs_dl_")
                // Always expose a plain POSIX path so PlaybackStateController can
                // call NSURL.fileURLWithPath() without double-encoding.
                val localPath = if (raw.startsWith("file://")) raw.removePrefix("file://") else raw
                DownloadItem(
                    id         = id,
                    title      = prefs.stringForKey("vs_dl_t_$id") ?: id,
                    url        = localPath,
                    artworkUrl = prefs.stringForKey("vs_dl_a_$id") ?: "",
                    state      = DownloadState.Completed,
                    localPath  = localPath
                )
            }
    }
}
