package dev.fynnjs.transcriber.domain.model

import java.io.File

enum class ModelError {
    DOWNLOAD_FAILED,
    CHECKSUM_MISMATCH,
    NO_SPACE,
    UNKNOWN,
}

/** State of one whisper model on disk. */
sealed interface ModelState {
    data object NotDownloaded : ModelState

    data class Downloading(val progress: Int) : ModelState

    data class Ready(val file: File) : ModelState

    data class Failed(val error: ModelError) : ModelState

    val isReady: Boolean get() = this is Ready
}
