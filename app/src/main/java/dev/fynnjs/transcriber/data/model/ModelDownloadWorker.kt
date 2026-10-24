package dev.fynnjs.transcriber.data.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.fynnjs.transcriber.MainActivity
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.ModelError
import dev.fynnjs.transcriber.domain.model.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads one model with HTTP range support so an interrupted download resumes instead of
 * starting over. The SHA-256 is verified before the file becomes usable.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val storage = ModelStorage(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val model = WhisperModel.fromId(inputData.getString(KEY_MODEL))
        if (!storage.directoryReady()) {
            return@withContext failure(ModelError.NO_SPACE)
        }
        if (storage.isPresent(model)) {
            return@withContext Result.success()
        }
        if (!storage.hasRoomFor(model)) {
            return@withContext failure(ModelError.NO_SPACE)
        }

        setForeground(createForegroundInfo(0))

        try {
            download(model)
        } catch (e: IOException) {
            val error = if (e.isDiskFull()) ModelError.NO_SPACE else ModelError.DOWNLOAD_FAILED
            Result.failure(workDataOf(KEY_ERROR to error.name))
        }
    }

    /** ENOSPC surfaces as a generic [IOException], so the message is the only reliable hint. */
    private fun IOException.isDiskFull(): Boolean {
        val text = message ?: return false
        return text.contains("ENOSPC", ignoreCase = true) ||
            text.contains("No space left", ignoreCase = true)
    }

    private suspend fun download(model: WhisperModel): Result {
        val target = storage.file(model)
        val part = storage.partFile(model)

        var existing = part.length()
        var connection = openConnection(model, existing)
        var responseCode = connection.responseCode

        // Server ignored the range request or the part file is unusable: start over once.
        if (existing > 0L && responseCode == HttpURLConnection.HTTP_OK) {
            existing = 0L
            connection.disconnect()
            connection = openConnection(model, 0L)
            responseCode = connection.responseCode
        } else if (responseCode == 416) {
            connection.disconnect()
            part.delete()
            return download(model)
        }

        if (responseCode != HttpURLConnection.HTTP_OK &&
            responseCode != HttpURLConnection.HTTP_PARTIAL
        ) {
            connection.disconnect()
            return Result.failure(workDataOf(KEY_ERROR to ModelError.DOWNLOAD_FAILED.name))
        }

        val append = responseCode == HttpURLConnection.HTTP_PARTIAL
        if (!append) existing = 0L

        try {
            val input = connection.inputStream
            FileOutputStream(part, append).buffered().use { output ->
                val buffer = ByteArray(128 * 1024)
                var downloaded = existing
                var lastReported = -1
                var lastProgressAt = 0L
                while (true) {
                    if (isStopped) {
                        return Result.failure()
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    val progress = ((downloaded * 100) / model.approxBytes).toInt().coerceIn(0, 100)
                    val now = System.currentTimeMillis()
                    if (progress != lastReported && (progress == 0 || now - lastProgressAt >= 750)) {
                        lastReported = progress
                        lastProgressAt = now
                        setProgress(workDataOf(KEY_PROGRESS to progress))
                        setForeground(createForegroundInfo(progress))
                    }
                }
                output.flush()
            }
        } finally {
            connection.disconnect()
        }

        if (part.length() != model.approxBytes) {
            return Result.failure(workDataOf(KEY_ERROR to ModelError.DOWNLOAD_FAILED.name))
        }

        val checksum = storage.sha256(part)
        if (!checksum.equals(model.sha256, ignoreCase = true)) {
            part.delete()
            return Result.failure(workDataOf(KEY_ERROR to ModelError.CHECKSUM_MISMATCH.name))
        }

        if (!part.renameTo(target)) {
            part.delete()
            return Result.failure(workDataOf(KEY_ERROR to ModelError.DOWNLOAD_FAILED.name))
        }
        return Result.success()
    }

    private fun openConnection(model: WhisperModel, offset: Long): HttpURLConnection {
        val connection = URL(model.downloadUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "Transcriber-Android/1.0")
        if (offset > 0L) {
            connection.setRequestProperty("Range", "bytes=$offset-")
        }
        return connection
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val context = applicationContext
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(context.getString(R.string.model_download_title))
            .setContentText(context.getString(R.string.model_download_progress, progress))
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }

    private fun failure(error: ModelError): Result =
        Result.failure(workDataOf(KEY_ERROR to error.name))

    companion object {
        const val KEY_MODEL = "model"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 1001

        fun workName(model: WhisperModel): String = "model-download-${model.id}"

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.model_download_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        setShowBadge(false)
                        description = context.getString(R.string.model_download_channel_description)
                    },
                )
            }
        }
    }
}
