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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit

actual class DownloadController(private val context: Context) {

    actual fun download(id: String, url: String, title: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_ID    to id,
                    DownloadWorker.KEY_URL   to url,
                    DownloadWorker.KEY_TITLE to title
                )
            )
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(id, ExistingWorkPolicy.KEEP, request)
    }

    actual fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(id)
        context.getSharedPreferences(DownloadWorker.PREFS, Context.MODE_PRIVATE)
            .edit().remove(id).apply()
    }

    actual fun remove(id: String) {
        cancel(id)
        File(context.filesDir, "downloads/$id.mp4").takeIf { it.exists() }?.delete()
    }

    actual fun observeState(id: String): Flow<DownloadState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(id)
            .map { infos ->
                val info = infos.firstOrNull() ?: return@map DownloadState.Idle
                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED  -> DownloadState.Queued
                    WorkInfo.State.RUNNING  -> DownloadState.Downloading(
                        info.progress.getFloat(DownloadWorker.KEY_PROGRESS, 0f) / 100f
                    )
                    WorkInfo.State.SUCCEEDED -> DownloadState.Completed
                    WorkInfo.State.FAILED    -> DownloadState.Failed("Download failed")
                    WorkInfo.State.CANCELLED -> DownloadState.Idle
                }
            }

    actual fun getLocalPath(id: String): String? =
        context.getSharedPreferences(DownloadWorker.PREFS, Context.MODE_PRIVATE)
            .getString(id, null)

    actual fun isDownloaded(id: String): Boolean = getLocalPath(id) != null
}
