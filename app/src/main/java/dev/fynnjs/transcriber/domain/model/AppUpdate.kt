package dev.fynnjs.transcriber.domain.model

import java.io.File

/** A newer release of the app, published as a GitHub release asset. */
data class AppUpdate(
    val versionName: String,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

enum class UpdateError {
    /** GitHub could not be reached or answered with an error. */
    NETWORK,

    /** The latest release has no APK asset. */
    MISSING_APK,

    /** The downloaded file is not an installable update for this app. */
    INVALID_APK,
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Ready(val update: AppUpdate, val file: File) : UpdateState

    /** [update] is set when the download failed, so the dialog can offer a retry. */
    data class Failed(val error: UpdateError, val update: AppUpdate?) : UpdateState
}
