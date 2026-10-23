package dev.fynnjs.transcriber.data.repository

import dev.fynnjs.transcriber.data.audio.AudioFileStore
import dev.fynnjs.transcriber.data.local.HistoryDao
import dev.fynnjs.transcriber.data.local.HistoryEntryEntity
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val dao: HistoryDao,
    private val audioFileStore: AudioFileStore,
) : HistoryRepository {

    override val entries: Flow<List<HistoryEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEntry(id: Long): Flow<HistoryEntry?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun search(query: String): Flow<List<HistoryEntry>> {
        val ftsQuery = buildFtsQuery(query)
        val source = if (ftsQuery == null) dao.observeAll() else dao.search(ftsQuery)
        return source.map { list -> list.map { it.toDomain() } }
    }

    override suspend fun get(id: Long): HistoryEntry? = dao.getById(id)?.toDomain()

    override suspend fun findByHash(hash: String): HistoryEntry? =
        dao.findByHash(hash)?.toDomain()

    override suspend fun insert(entry: HistoryEntry): Long =
        dao.insert(entry.toEntity())

    override suspend fun updateStatus(
        id: Long,
        status: TranscriptionStatus,
        errorMessage: String?,
    ) {
        dao.updateStatus(id, status.name, errorMessage)
    }

    override suspend fun updateProgress(id: Long, progress: Int) {
        dao.updateProgress(id, progress.coerceIn(0, 100))
    }

    override suspend fun updateModelUsed(id: Long, model: WhisperModel) {
        dao.updateModelUsed(id, model.id)
    }

    override suspend fun complete(
        id: Long,
        transcript: String?,
        language: String,
        model: WhisperModel,
        durationMs: Long,
    ) {
        dao.complete(
            id = id,
            transcript = transcript?.trim()?.takeIf { it.isNotEmpty() },
            language = language,
            modelUsed = model.id,
            durationMs = durationMs,
        )
    }

    override suspend fun updateLabel(id: Long, label: String?) {
        dao.updateLabel(id, label?.trim()?.takeIf { it.isNotEmpty() })
    }

    override suspend fun updateAudioPath(id: Long, audioPath: String?) {
        dao.updateAudioPath(id, audioPath)
    }

    override suspend fun nextQueued(): HistoryEntry? = dao.nextQueued()?.toDomain()

    override suspend fun resetStuckEntries(): Int = dao.resetStuckEntries()

    override suspend fun recentLabels(limit: Int): List<String> = dao.recentLabels(limit)

    override suspend fun delete(id: Long) {
        val entry = dao.getById(id) ?: return
        dao.deleteById(id)
        audioFileStore.delete(entry.audioPath)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
        audioFileStore.deleteAllFiles()
    }

    override suspend fun expireAudio(cutoff: Long): Int {
        val paths = dao.audioPathsOlderThan(cutoff)
        if (paths.isEmpty()) return 0
        dao.clearAudioPathOlderThan(cutoff)
        var deleted = 0
        paths.forEach { path -> if (audioFileStore.delete(path)) deleted++ }
        return deleted
    }

    override suspend fun clearAllAudio(): Int {
        val paths = dao.allAudioPaths()
        if (paths.isEmpty()) return 0
        dao.clearAllAudioPaths()
        var deleted = 0
        paths.forEach { path -> if (audioFileStore.delete(path)) deleted++ }
        return deleted
    }

    override suspend fun clearAudioOfCompleted(): Int {
        val paths = dao.audioPathsOfCompleted()
        if (paths.isEmpty()) return 0
        dao.clearAudioPathsOfCompleted()
        var deleted = 0
        paths.forEach { path -> if (audioFileStore.delete(path)) deleted++ }
        return deleted
    }

    override suspend fun deleteOlderThan(cutoff: Long): Int {
        val entries = dao.entriesOlderThan(cutoff)
        if (entries.isEmpty()) return 0
        val deleted = dao.deleteOlderThan(cutoff)
        entries.forEach { entry -> audioFileStore.delete(entry.audioPath) }
        return deleted
    }

    private fun HistoryEntryEntity.toDomain() = HistoryEntry(
        id = id,
        createdAt = createdAt,
        sourceFileName = sourceFileName,
        durationMs = durationMs,
        audioPath = audioPath,
        audioHash = audioHash,
        transcript = transcript,
        language = language,
        modelUsed = modelUsed,
        status = runCatching { TranscriptionStatus.valueOf(status) }
            .getOrDefault(TranscriptionStatus.FAILED),
        errorMessage = errorMessage,
        label = label,
        progress = progress,
    )

    private fun HistoryEntry.toEntity() = HistoryEntryEntity(
        id = id,
        createdAt = createdAt,
        sourceFileName = sourceFileName,
        durationMs = durationMs,
        audioPath = audioPath,
        audioHash = audioHash,
        transcript = transcript,
        language = language,
        modelUsed = modelUsed,
        status = status.name,
        errorMessage = errorMessage,
        label = label,
        progress = progress,
    )
}

/**
 * Turns free text into an FTS4 prefix query, e.g. "Projekt Bericht" -> "\"Projekt\"* \"Bericht\"*".
 * Returns null for blank input.
 */
internal fun buildFtsQuery(raw: String): String? {
    val tokens = raw
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    // Tokens are sanitized to letters/digits, so they cannot contain FTS operators.
    return tokens.joinToString(" ") { token -> "$token*" }
}
