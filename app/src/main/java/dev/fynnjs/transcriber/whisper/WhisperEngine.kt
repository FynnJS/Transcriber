package dev.fynnjs.transcriber.whisper

import dev.fynnjs.transcriber.audio.PcmAudio
import dev.fynnjs.transcriber.domain.model.WhisperModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds at most one loaded whisper context (one model) and serializes transcriptions so the
 * device is never asked to run two heavy jobs at once.
 *
 * Long messages are decoded in overlapping windows: the finished text of every window is
 * reported through [transcribe]'s `onPartial` so the UI can show the transcript as it grows.
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
        onPartial: suspend (String) -> Unit = {},
    ): WhisperResult = mutex.withLock {
        val ctx = ensureLoaded(model, modelFile)
        withContext(Dispatchers.Default) {
            transcribeInWindows(
                samples = audio.samples,
                language = language,
                onProgress = onProgress,
                onPartial = onPartial,
                isCancelled = { ctx.isCancelled },
                transcribeWindow = { window, windowLanguage, audioContextFrames, windowProgress ->
                    ctx.transcribe(
                        samples = window,
                        language = windowLanguage,
                        threads = WhisperContext.defaultThreadCount(),
                        audioContextFrames = audioContextFrames,
                        onProgress = windowProgress,
                    )
                },
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

    internal suspend fun transcribeInWindows(
        samples: FloatArray,
        language: String,
        onProgress: (Int) -> Unit,
        onPartial: suspend (String) -> Unit,
        isCancelled: () -> Boolean,
        transcribeWindow: suspend (
            samples: FloatArray,
            language: String,
            audioContextFrames: Int,
            onProgress: (Int) -> Unit,
        ) -> WhisperResult,
    ): WhisperResult {
        val chunks = TranscriptChunks.plan(samples.size)
        if (chunks.isEmpty()) throw WhisperException("No audio samples")

        val streaming = chunks.size > 1
        var transcript = ""
        var spokenLanguage = language
        var detectedLanguage = LANGUAGE_UNKNOWN

        for (chunk in chunks) {
            currentCoroutineContext().ensureActive()
            if (isCancelled()) throw CancellationException("Transcription canceled")

            val window = if (chunk.startSample == 0 && chunk.endSample == samples.size) {
                samples
            } else {
                samples.copyOfRange(chunk.startSample, chunk.endSample)
            }

            val result = transcribeWindow(
                window,
                spokenLanguage,
                if (streaming) TranscriptChunks.audioContextFrames(chunk) else 0,
            ) { localProgress ->
                onProgress(globalProgress(chunk, samples.size, localProgress))
            }

            if (detectedLanguage == LANGUAGE_UNKNOWN && result.language != LANGUAGE_UNKNOWN) {
                detectedLanguage = result.language
            }
            if (spokenLanguage == LANGUAGE_AUTO &&
                detectedLanguage != LANGUAGE_AUTO &&
                detectedLanguage != LANGUAGE_UNKNOWN
            ) {
                // Reuse the first detection instead of detecting again for every window.
                spokenLanguage = detectedLanguage
            }

            transcript = TranscriptMerger.merge(transcript, result.text)
            if (streaming && transcript.isNotBlank()) onPartial(transcript)
        }

        if (detectedLanguage == LANGUAGE_UNKNOWN && language != LANGUAGE_AUTO && language.isNotBlank()) {
            detectedLanguage = language
        }
        return WhisperResult(text = transcript.trim(), language = detectedLanguage)
    }

    /** Maps the progress inside one window onto the whole message; 100 belongs to the service. */
    private fun globalProgress(chunk: AudioChunk, totalSamples: Int, localPercent: Int): Int {
        if (totalSamples <= 0) return 0
        val decoded = chunk.startSample + (chunk.sampleCount.toLong() * localPercent / 100).toInt()
        return (decoded * 100 / totalSamples).coerceIn(0, 99)
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

    private companion object {
        const val LANGUAGE_AUTO = "auto"
        const val LANGUAGE_UNKNOWN = "unknown"
    }
}
