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
 * ("Kein Text erkannt"). [partialTranscript] carries the live preview of a running
 * transcription and is cleared as soon as the entry leaves [TranscriptionStatus.TRANSCRIBING].
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
    val partialTranscript: String? = null,
) {
    val hasAudio: Boolean get() = audioPath != null
    val hasTranscript: Boolean get() = !transcript.isNullOrBlank()
    val isFinished: Boolean get() = status == TranscriptionStatus.DONE || status == TranscriptionStatus.FAILED

    /** True while [partialTranscript] holds the live preview of a running transcription. */
    val isLivePreview: Boolean
        get() = status == TranscriptionStatus.TRANSCRIBING && !partialTranscript.isNullOrBlank()

    /** Text to show for this entry: the live preview wins over an older finished transcript. */
    val displayedTranscript: String?
        get() = if (isLivePreview) partialTranscript else transcript
}
