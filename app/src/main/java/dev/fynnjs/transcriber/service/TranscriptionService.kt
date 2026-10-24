package dev.fynnjs.transcriber.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.fynnjs.transcriber.MainActivity
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.audio.AudioDecoder
import dev.fynnjs.transcriber.audio.AudioDecodingException
import dev.fynnjs.transcriber.data.audio.AudioFileStore
import dev.fynnjs.transcriber.domain.model.AudioRetention
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionLanguage
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import dev.fynnjs.transcriber.domain.repository.SettingsRepository
import dev.fynnjs.transcriber.whisper.WhisperEngine
import dev.fynnjs.transcriber.whisper.WhisperException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Foreground service that works through queued entries one after another:
 * QUEUED -> TRANSCRIBING -> DONE or FAILED.
 */
@AndroidEntryPoint
class TranscriptionService : Service() {

    @Inject lateinit var historyRepository: HistoryRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var modelRepository: ModelRepository

    @Inject lateinit var audioFileStore: AudioFileStore

    @Inject lateinit var audioDecoder: AudioDecoder

    @Inject lateinit var whisperEngine: WhisperEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val processing = AtomicBoolean(false)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(buildNotification(0))
        wake.trySend(Unit)
        if (processing.compareAndSet(false, true)) {
            scope.launch { processQueue() }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        whisperEngine.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun processQueue() {
        try {
            while (true) {
                val entry = historyRepository.nextQueued()
                if (entry == null) {
                    if (wake.tryReceive().isSuccess) continue
                    break
                }
                processEntry(entry)
            }
        } finally {
            processing.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun processEntry(entry: HistoryEntry) {
        historyRepository.updateStatus(entry.id, TranscriptionStatus.TRANSCRIBING)
        updateNotification(0)

        try {
            val model = WhisperModel.fromId(entry.modelUsed)
            if (!modelRepository.isReady(model)) {
                fail(entry, getString(R.string.error_model_missing))
                return
            }
            val audioFile = audioFileStore.resolve(entry.audioPath)
                ?: run {
                    fail(entry, getString(R.string.error_audio_missing))
                    return
                }

            val pcm = audioDecoder.decode(audioFile)
            val language = TranscriptionLanguage.fromCode(entry.language)
            var lastPersisted = -1

            val result = whisperEngine.transcribe(
                model = model,
                modelFile = modelRepository.modelFile(model),
                audio = pcm,
                language = language.code,
                onProgress = { progress ->
                    if (progress - lastPersisted >= 5 || progress == 100) {
                        lastPersisted = progress
                        scope.launch {
                            historyRepository.updateProgress(entry.id, progress)
                            updateNotification(progress, entry.sourceFileName)
                        }
                    }
                },
                onPartial = { partial ->
                    historyRepository.updatePartialTranscript(entry.id, partial)
                },
            )

            historyRepository.complete(
                id = entry.id,
                transcript = result.text.takeIf { it.isNotBlank() },
                language = result.language,
                model = model,
                durationMs = pcm.durationMs,
            )

            val settings = settingsRepository.current()
            if (settings.audioRetention == AudioRetention.IMMEDIATELY) {
                audioFileStore.delete(entry.audioPath)
                historyRepository.updateAudioPath(entry.id, null)
            }
        } catch (e: CancellationException) {
            historyRepository.updateStatus(entry.id, TranscriptionStatus.QUEUED)
            throw e
        } catch (e: AudioDecodingException) {
            fail(entry, getString(R.string.error_decode_failed))
        } catch (e: WhisperException) {
            val message = if (!dev.fynnjs.transcriber.whisper.WhisperContext.libraryAvailable) {
                getString(R.string.error_native_unsupported)
            } else {
                getString(R.string.error_transcription_failed)
            }
            fail(entry, message)
        } catch (e: Exception) {
            fail(entry, getString(R.string.error_transcription_failed))
        }
    }

    private suspend fun fail(entry: HistoryEntry, message: String) {
        historyRepository.updateStatus(entry.id, TranscriptionStatus.FAILED, message)
        updateNotification(100)
    }

    private fun updateNotification(progress: Int, fileName: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Foreground services may run without the notification permission; the
            // notification is simply suppressed in that case.
            runCatching {
                startInForeground(buildNotification(progress, fileName))
            }
        } else {
            runCatching { startInForeground(buildNotification(progress, fileName)) }
        }
    }

    private fun startInForeground(notification: android.app.Notification) {
        when {
            Build.VERSION.SDK_INT >= 35 -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )

            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        progress: Int,
        fileName: String? = null,
    ): android.app.Notification {
        ensureChannel()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = fileName?.let { getString(R.string.transcription_notification_file, it) }
            ?: getString(R.string.transcription_notification_preparing)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_transcribe)
            .setContentTitle(getString(R.string.transcription_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        // Whisper reports nothing until the first segment window is decoded, so a determinate
        // 0 % bar would look stuck; run the bar indeterminate until a real value exists.
        if (progress > 0) {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.transcription_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    description = getString(R.string.transcription_channel_description)
                },
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "transcription"
        private const val NOTIFICATION_ID = 1002

        fun enqueue(context: Context, entryId: Long) {
            val intent = Intent(context, TranscriptionService::class.java)
                .putExtra(EXTRA_ENTRY_ID, entryId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Starts the service when queued work survived a restart. */
        fun resumeQueue(context: Context) {
            val intent = Intent(context, TranscriptionService::class.java)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        private const val EXTRA_ENTRY_ID = "entry_id"
    }
}
