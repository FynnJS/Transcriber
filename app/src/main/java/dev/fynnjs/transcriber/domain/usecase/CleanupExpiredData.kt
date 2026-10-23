package dev.fynnjs.transcriber.domain.usecase

import dev.fynnjs.transcriber.domain.model.AudioRetention
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import dev.fynnjs.transcriber.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the retention settings. Audio expiry keeps the entry (audioPath becomes null),
 * transcript expiry removes the whole entry including its audio file.
 */
@Singleton
class CleanupExpiredData @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun run(now: Long = System.currentTimeMillis()) {
        val settings = settingsRepository.current()

        when (val retention = settings.audioRetention) {
            AudioRetention.IMMEDIATELY -> historyRepository.clearAudioOfCompleted()
            AudioRetention.NEVER -> Unit
            else -> retention.days?.let { days ->
                historyRepository.expireAudio(now - days * DAY_MS)
            }
        }

        val transcriptDays = settings.transcriptRetention.days
        if (transcriptDays != null) {
            historyRepository.deleteOlderThan(now - transcriptDays * DAY_MS)
        }
    }

    companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
