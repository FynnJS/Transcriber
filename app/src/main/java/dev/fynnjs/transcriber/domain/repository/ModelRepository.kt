package dev.fynnjs.transcriber.domain.repository

import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.WhisperModel
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface ModelRepository {
    /** State of every model, always complete for [WhisperModel.entries]. */
    val states: StateFlow<Map<WhisperModel, ModelState>>

    fun state(model: WhisperModel): ModelState

    fun isReady(model: WhisperModel): Boolean

    fun modelFile(model: WhisperModel): File

    /** Re-checks files on disk (after app start or manual deletion). */
    suspend fun refresh()

    /** Starts a resumable download through WorkManager; no-op if already running. */
    fun download(model: WhisperModel)

    fun delete(model: WhisperModel)
}
