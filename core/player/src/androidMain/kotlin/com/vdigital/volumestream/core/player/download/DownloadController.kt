package com.vdigital.volumestream.core.player.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "VS_DL_Controller"

actual class DownloadController(private val context: Context) {

    actual fun download(id: String, url: String, title: String, artworkUrl: String, wifiOnly: Boolean) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_ID      to id,
                    DownloadWorker.KEY_URL     to url,
                    DownloadWorker.KEY_TITLE   to title,
                    DownloadWorker.KEY_ARTWORK to artworkUrl
                )
            )
            .setConstraints(Constraints(requiredNetworkType = networkType))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(id, ExistingWorkPolicy.KEEP, request)
    }

    actual fun cancel(id: String) {
        Log.d(TAG, "cancel($id) — cancelling WorkManager work + clearing prefs")
        WorkManager.getInstance(context).cancelUniqueWork(id)
        context.getSharedPreferences(DownloadWorker.PREFS, Context.MODE_PRIVATE)
            .edit().remove(id).remove("_t_$id").remove("_a_$id").apply()
        Log.d(TAG, "cancel($id) — prefs cleared")
    }

    actual fun remove(id: String) {
        Log.d(TAG, "remove($id)")
        cancel(id)
        File(context.filesDir, "downloads/$id.mp4").takeIf { it.exists() }?.delete()
    }

    actual fun observeState(id: String): Flow<DownloadState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(id)
            .map { infos ->
                val info = infos.firstOrNull()
                val rawState = info?.state?.name ?: "null"
                val isDownloaded = isDownloaded(id)
                val mapped = when {
                    // No WorkInfo at all — WorkManager has pruned the record.
                    // Fall back to SharedPreferences: if a local path exists the
                    // download completed successfully in a previous session.
                    info == null -> {
                        if (isDownloaded) DownloadState.Completed else DownloadState.Idle
                    }
                    info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.BLOCKED  -> DownloadState.Queued
                    info.state == WorkInfo.State.RUNNING  -> DownloadState.Downloading(
                        info.progress.getFloat(DownloadWorker.KEY_PROGRESS, 0f) / 100f
                    )
                    info.state == WorkInfo.State.SUCCEEDED -> {
                        // Guard against the race where remove() has cleared prefs but
                        // WorkManager hasn't yet transitioned away from SUCCEEDED.
                        if (isDownloaded) DownloadState.Completed else DownloadState.Idle
                    }
                    info.state == WorkInfo.State.FAILED    -> {
                        // Even on failure, if the file was written in a prior attempt,
                        // treat it as Completed rather than showing a failed state.
                        if (isDownloaded) DownloadState.Completed
                        else DownloadState.Failed("Download failed")
                    }
                    info.state == WorkInfo.State.CANCELLED -> {
                        // cancel()/remove() clears SharedPreferences synchronously before
                        // WorkManager fires the CANCELLED event, so isDownloaded() will
                        // already return false here.  Always treat CANCELLED as Idle to
                        // prevent the UI from flickering back to a Completed (tick) state
                        // after the user deletes a download.
                        DownloadState.Idle
                    }
                    else -> DownloadState.Idle
                }
                Log.d(TAG, "observeState($id): WorkInfo.state=$rawState isDownloaded=$isDownloaded -> $mapped")
                mapped
            }

    actual fun getLocalPath(id: String): String? =
        context.getSharedPreferences(DownloadWorker.PREFS, Context.MODE_PRIVATE)
            .getString(id, null)

    actual fun isDownloaded(id: String): Boolean = getLocalPath(id) != null

    @Suppress("UNCHECKED_CAST")
    actual fun listDownloads(): List<DownloadItem> {
        val prefs = context.getSharedPreferences(DownloadWorker.PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries
            .filter { !it.key.startsWith("_t_") && !it.key.startsWith("_a_") }
            .mapNotNull { entry ->
                val id        = entry.key
                val localPath = entry.value as? String ?: return@mapNotNull null
                DownloadItem(
                    id         = id,
                    title      = prefs.getString("_t_$id", null) ?: id,
                    url        = localPath,
                    artworkUrl = prefs.getString("_a_$id", null) ?: "",
                    state      = DownloadState.Completed,
                    localPath  = localPath
                )
            }
    }
}
