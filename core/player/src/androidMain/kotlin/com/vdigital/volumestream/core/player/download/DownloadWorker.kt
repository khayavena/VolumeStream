package com.vdigital.volumestream.core.player.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_URL     = "url"
        const val KEY_ID      = "id"
        const val KEY_TITLE   = "title"
        const val KEY_ARTWORK = "artwork"
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

        setForeground(createForegroundInfo(title, 0))

        val downloadsDir = File(context.filesDir, "downloads").also { it.mkdirs() }
        val outputFile   = File(downloadsDir, "$id.mp4")

        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 30_000
                connect()
            }
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
                            outputFile.delete()
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

            val localUri = Uri.fromFile(outputFile).toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(id, localUri)
                .putString("_t_$id", title)
                .putString("_a_$id", artwork)
                .apply()

            Result.success(workDataOf(KEY_LOCAL_PATH to localUri))
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            if (runAttemptCount < 2) Result.retry() else Result.failure()
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
