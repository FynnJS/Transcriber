package dev.fynnjs.transcriber.whisper

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fynnjs.transcriber.audio.AudioDecoder
import dev.fynnjs.transcriber.audio.PcmAudio
import dev.fynnjs.transcriber.domain.model.WhisperModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs only when a real ggml model is present in the app files (pushed before the test run).
 * This exercises the JNI bridge, the model loader and the 16 kHz PCM hand-off.
 */
@RunWith(AndroidJUnit4::class)
class WhisperEngineInstrumentedTest {

    @Test
    fun transcribesJfkSpeechWithBaseModel() = runBlocking {
        val modelFile = requireBaseModel()
        val audio = jfkAudio()

        val progress = AtomicBoolean(false)
        val engine = WhisperEngine()
        val result = engine.transcribe(
            model = WhisperModel.BASE,
            modelFile = modelFile,
            audio = audio,
            language = "en",
            onProgress = { progress.set(true) },
        )
        engine.close()

        assertTrue(progress.get())
        val text = result.text.lowercase()
        assertTrue("Unexpected transcript: ${result.text}", text.contains("fellow"))
        assertTrue(result.language == "en" || result.language == "de")
    }

    /** Exercises the windowed path, including the shortened encoder context per window. */
    @Test
    fun streamsLongAudioInWindows() = runBlocking {
        val modelFile = requireBaseModel()
        val audio = jfkAudio()
        val longAudio = PcmAudio(
            samples = audio.samples + audio.samples + audio.samples,
            sampleRate = audio.sampleRate,
            durationMs = audio.durationMs * 3,
        )

        val partials = mutableListOf<String>()
        val engine = WhisperEngine()
        val result = engine.transcribe(
            model = WhisperModel.BASE,
            modelFile = modelFile,
            audio = longAudio,
            language = "en",
            onProgress = {},
            onPartial = { partial -> partials += partial },
        )
        engine.close()

        assertTrue("No live preview was reported", partials.isNotEmpty())
        assertTrue("Final transcript is empty", result.text.isNotBlank())
        assertTrue(result.text.length >= partials.first().length)
    }

    private fun requireBaseModel(): File {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val modelFile = File(context.filesDir, "models/${WhisperModel.BASE.fileName}")
        assumeTrue("Base model not installed, skipping native test", modelFile.isFile)
        return modelFile
    }

    private suspend fun jfkAudio(): PcmAudio {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wav = File(context.cacheDir, "jfk-whisper.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("jfk.wav").use { input ->
            wav.outputStream().use { output -> input.copyTo(output) }
        }
        return AudioDecoder().decode(wav)
    }
}
