package dev.fynnjs.transcriber.whisper

import dev.fynnjs.transcriber.audio.PcmAudio
import dev.fynnjs.transcriber.domain.model.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds at most one loaded whisper context (one model) and serializes transcriptions so the
 * device is never asked to run two heavy jobs at once.
 */
@Singleton
class WhisperEngine @Inject constructor() {

    private val mutex = Mutex()
    private var loadedModel: WhisperModel? = null
    private var context: WhisperContext? = null

    suspend fun transcribe(
        model: WhisperModel,
        modelFile: File,
        audio: PcmAudio,
        language: String,
        onProgress: (Int) -> Unit,
    ): WhisperResult = mutex.withLock {
        val ctx = ensureLoaded(model, modelFile)
        withContext(Dispatchers.Default) {
            ctx.transcribe(
                samples = audio.samples,
                language = language,
                threads = WhisperContext.defaultThreadCount(),
                onProgress = onProgress,
            )
        }
    }

    /** Aborts a running transcription; the service calls this on cancellation. */
    fun cancel() {
        context?.cancel()
    }

    fun close() {
        context?.close()
        context = null
        loadedModel = null
    }

    private suspend fun ensureLoaded(model: WhisperModel, modelFile: File): WhisperContext {
        val existing = context
        if (existing != null && loadedModel == model && modelFile.isFile) {
            return existing
        }
        existing?.close()
        context = null
        loadedModel = null
        val loaded = withContext(Dispatchers.Default) {
            WhisperContext.load(modelFile, WhisperContext.defaultThreadCount())
        }
        context = loaded
        loadedModel = model
        return loaded
    }
}
