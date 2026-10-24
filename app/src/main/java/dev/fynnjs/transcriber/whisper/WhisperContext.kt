package dev.fynnjs.transcriber.whisper

import kotlinx.coroutines.CancellationException
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class WhisperException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class WhisperResult(
    val text: String,
    val language: String,
)

/**
 * Kotlin wrapper around the JNI bridge. A context is not thread-safe; [WhisperEngine]
 * serializes access.
 */
class WhisperContext private constructor(private var handle: Long) : Closeable {

    private val cancelRequested = AtomicBoolean(false)

    /** True once [cancel] was requested; the chunked loop stops before the next window. */
    val isCancelled: Boolean get() = cancelRequested.get()

    /**
     * Transcribes [samples] in one pass. [audioContextFrames] shrinks whisper's encoder context
     * to the given window (0 keeps the default 30 s), which is what makes short windows cheap.
     */
    fun transcribe(
        samples: FloatArray,
        language: String,
        threads: Int,
        audioContextFrames: Int = 0,
        onProgress: (Int) -> Unit,
    ): WhisperResult {
        check(handle != 0L) { "Whisper context is closed" }
        if (!libraryAvailable) {
            throw WhisperException("Whisper native library is not available for this ABI")
        }
        cancelRequested.set(false)
        val listener = object : ProgressListener {
            override fun onProgress(percent: Int) {
                onProgress(percent.coerceIn(0, 100))
            }
        }
        val output = try {
            nativeTranscribe(handle, samples, language, threads, audioContextFrames, listener)
        } catch (e: UnsatisfiedLinkError) {
            throw WhisperException("Whisper native library is not available", e)
        }
        if (output == null) {
            if (cancelRequested.get()) throw CancellationException("Transcription canceled")
            throw WhisperException("Transcription failed")
        }
        if (output.size < 2) {
            throw WhisperException("Malformed transcription result")
        }
        return WhisperResult(text = output[0].trim(), language = output[1])
    }

    /** Requests an abort; safe to call from another thread. */
    fun cancel() {
        cancelRequested.set(true)
        if (handle != 0L && libraryAvailable) {
            runCatching { nativeCancel(handle) }
        }
    }

    override fun close() {
        val current = handle
        handle = 0L
        if (current != 0L && libraryAvailable) {
            runCatching { nativeFree(current) }
        }
    }

    companion object {
        val libraryAvailable: Boolean by lazy {
            runCatching { System.loadLibrary("transcriber_whisper") }.isSuccess
        }

        /** Blocks while the model is loaded; call from a background dispatcher. */
        fun load(modelFile: File, threads: Int): WhisperContext {
            if (!libraryAvailable) {
                throw WhisperException("Whisper native library is not available for this ABI")
            }
            if (!modelFile.isFile) {
                throw WhisperException("Model file missing")
            }
            val handle = runCatching { nativeInit(modelFile.absolutePath, threads) }
                .getOrElse { throw WhisperException("Could not load whisper model", it) }
            if (handle == 0L) {
                throw WhisperException("Could not load whisper model")
            }
            return WhisperContext(handle)
        }

        /** Leaves one core for the UI and system; whisper scales badly past four threads. */
        internal fun defaultThreadCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return (cores - 1).coerceIn(1, 4)
        }

        @JvmStatic
        private external fun nativeInit(modelPath: String, threads: Int): Long

        @JvmStatic
        private external fun nativeFree(handle: Long)

        @JvmStatic
        private external fun nativeCancel(handle: Long)

        @JvmStatic
        private external fun nativeTranscribe(
            handle: Long,
            samples: FloatArray,
            language: String,
            threads: Int,
            audioContextFrames: Int,
            listener: ProgressListener?,
        ): Array<String>?
    }

    private interface ProgressListener {
        @Suppress("unused")
        fun onProgress(percent: Int)
    }
}
