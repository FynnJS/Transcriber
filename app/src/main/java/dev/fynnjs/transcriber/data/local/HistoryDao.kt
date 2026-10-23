package dev.fynnjs.transcriber.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE id = :id")
    fun observeById(id: Long): Flow<HistoryEntryEntity?>

    @Query(
        """
        SELECT h.* FROM history_entries AS h
        INNER JOIN history_entries_fts AS fts ON h.id = fts.rowid
        WHERE history_entries_fts MATCH :ftsQuery
        ORDER BY h.createdAt DESC
        """,
    )
    fun search(ftsQuery: String): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE id = :id")
    suspend fun getById(id: Long): HistoryEntryEntity?

    @Query("SELECT * FROM history_entries WHERE audioHash = :hash ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByHash(hash: String): HistoryEntryEntity?

    @Query("SELECT * FROM history_entries WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): HistoryEntryEntity?

    @Insert
    suspend fun insert(entry: HistoryEntryEntity): Long

    @Query("UPDATE history_entries SET status = :status, errorMessage = :errorMessage, progress = 0 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorMessage: String?)

    @Query("UPDATE history_entries SET progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int)

    @Query(
        """
        UPDATE history_entries
        SET transcript = :transcript, language = :language, modelUsed = :modelUsed,
            durationMs = :durationMs, status = 'DONE', errorMessage = NULL, progress = 100
        WHERE id = :id
        """,
    )
    suspend fun complete(id: Long, transcript: String?, language: String, modelUsed: String, durationMs: Long)

    @Query("UPDATE history_entries SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String?)

    @Query("UPDATE history_entries SET audioPath = :audioPath WHERE id = :id")
    suspend fun updateAudioPath(id: Long, audioPath: String?)

    @Query("UPDATE history_entries SET status = 'QUEUED', errorMessage = NULL, progress = 0 WHERE status = 'TRANSCRIBING'")
    suspend fun resetStuckEntries(): Int

    @Query(
        """
        SELECT label FROM history_entries
        WHERE label IS NOT NULL AND label != ''
        GROUP BY label
        ORDER BY MAX(createdAt) DESC
        LIMIT :limit
        """,
    )
    suspend fun recentLabels(limit: Int): List<String>

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_entries")
    suspend fun deleteAll()

    @Query("SELECT audioPath FROM history_entries WHERE audioPath IS NOT NULL AND createdAt < :cutoff")
    suspend fun audioPathsOlderThan(cutoff: Long): List<String>

    @Query("SELECT audioPath FROM history_entries WHERE audioPath IS NOT NULL")
    suspend fun allAudioPaths(): List<String>

    @Query("SELECT audioPath FROM history_entries WHERE audioPath IS NOT NULL AND status = 'DONE'")
    suspend fun audioPathsOfCompleted(): List<String>

    @Query("UPDATE history_entries SET audioPath = NULL WHERE audioPath IS NOT NULL AND status = 'DONE'")
    suspend fun clearAudioPathsOfCompleted(): Int

    @Query("UPDATE history_entries SET audioPath = NULL WHERE audioPath IS NOT NULL AND createdAt < :cutoff")
    suspend fun clearAudioPathOlderThan(cutoff: Long): Int

    @Query("UPDATE history_entries SET audioPath = NULL WHERE audioPath IS NOT NULL")
    suspend fun clearAllAudioPaths(): Int

    @Query("SELECT * FROM history_entries WHERE createdAt < :cutoff")
    suspend fun entriesOlderThan(cutoff: Long): List<HistoryEntryEntity>

    @Query("DELETE FROM history_entries WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
