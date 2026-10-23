package dev.fynnjs.transcriber.domain.model

/** Processing state of a shared voice message. */
enum class TranscriptionStatus {
    QUEUED,
    TRANSCRIBING,
    DONE,
    FAILED,
}

/**
 * One shared voice message with its transcript.
 *
 * [audioPath] is null once the audio retention period removed the file or when the user never
 * had audio available. [transcript] is null while processing and for silent messages
 * ("Kein Text erkannt").
 */
data class HistoryEntry(
    val id: Long = 0,
    val createdAt: Long,
    val sourceFileName: String,
    val durationMs: Long,
    val audioPath: String?,
    val audioHash: String,
    val transcript: String?,
    val language: String,
    val modelUsed: String,
    val status: TranscriptionStatus,
    val errorMessage: String? = null,
    val label: String? = null,
    val progress: Int = 0,
) {
    val hasAudio: Boolean get() = audioPath != null
    val hasTranscript: Boolean get() = !transcript.isNullOrBlank()
    val isFinished: Boolean get() = status == TranscriptionStatus.DONE || status == TranscriptionStatus.FAILED
}
