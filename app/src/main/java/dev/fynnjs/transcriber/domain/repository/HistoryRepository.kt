package dev.fynnjs.transcriber.domain.repository

import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for history entries. Used by every ViewModel and by the
 * transcription service; nothing else touches Room directly.
 */
interface HistoryRepository {
    /** All entries, newest first. */
    val entries: Flow<List<HistoryEntry>>

    fun observeEntry(id: Long): Flow<HistoryEntry?>

    /** Full-text search over transcript and label. Blank queries return all entries. */
    fun search(query: String): Flow<List<HistoryEntry>>

    suspend fun get(id: Long): HistoryEntry?

    /** Latest entry with the same audio checksum, used for duplicate detection. */
    suspend fun findByHash(hash: String): HistoryEntry?

    suspend fun insert(entry: HistoryEntry): Long

    suspend fun updateStatus(
        id: Long,
        status: TranscriptionStatus,
        errorMessage: String? = null,
    )

    suspend fun updateProgress(id: Long, progress: Int)

    /** Stores the live preview of a running transcription; only applies to `TRANSCRIBING`. */
    suspend fun updatePartialTranscript(id: Long, text: String?)

    /** Chooses the model for the next transcription of this entry. */
    suspend fun updateModelUsed(id: Long, model: WhisperModel)

    suspend fun complete(
        id: Long,
        transcript: String?,
        language: String,
        model: WhisperModel,
        durationMs: Long,
    )

    suspend fun updateLabel(id: Long, label: String?)

    suspend fun updateAudioPath(id: Long, audioPath: String?)

    /** Oldest queued entry, or null when the queue is empty. */
    suspend fun nextQueued(): HistoryEntry?

    /** Entries left in TRANSCRIBING by a killed process go back to QUEUED. */
    suspend fun resetStuckEntries(): Int

    suspend fun recentLabels(limit: Int = 6): List<String>

    /** Deletes the entry and its audio file. */
    suspend fun delete(id: Long)

    suspend fun deleteAll()

    /** Removes audio files older than [cutoff] and clears their audioPath. */
    suspend fun expireAudio(cutoff: Long): Int

    /** Removes audio for every entry (audio retention "sofort"). */
    suspend fun clearAllAudio(): Int

    /** Removes audio only from finished entries (retention "sofort nach Transkription"). */
    suspend fun clearAudioOfCompleted(): Int

    /** Deletes entries created before [cutoff], including their audio files. */
    suspend fun deleteOlderThan(cutoff: Long): Int
}
