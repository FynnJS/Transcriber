package dev.fynnjs.transcriber.whisper

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fynnjs.transcriber.audio.AudioDecoder
import dev.fynnjs.transcriber.audio.PcmAudio
import dev.fynnjs.transcriber.data.model.ModelStorage
import dev.fynnjs.transcriber.domain.model.WhisperModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the same German message through every installed catalog model and records the wall time
 * per transcription. It measures two calls per model: the first includes the model load, the
 * second is the steady-state cost used for the real-time factor.
 *
 * Models must be present in `files/models` (downloaded by the app or pushed before the run).
 */
@RunWith(AndroidJUnit4::class)
class WhisperModelComparisonTest {

    @Test
    fun comparesEveryInstalledModelOnGermanSpeech(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = ModelStorage(context)
        val audio = germanAudio(context)
        val report = StringBuilder("audio=${audio.durationMs}ms samples=${audio.samples.size}\n")

        for (model in WhisperModel.entries) {
            val file = storage.file(model)
            assumeTrue("${model.id} not installed, skipping", file.isFile)

            assertEquals("${model.id} size", model.approxBytes, file.length())
            assertEquals("${model.id} checksum", model.sha256, storage.sha256(file))

            val engine = WhisperEngine()
            val cold = measure(engine, model, file, audio)
            val warm = measure(engine, model, file, audio)
            engine.close()

            val ratio = warm.elapsedMs.toDouble() / audio.durationMs
            val line = "model=${model.id} bytes=${file.length()} sha256=${model.sha256} " +
                "coldMs=${cold.elapsedMs} warmMs=${warm.elapsedMs} " +
                "rtf=%.3f lang=${warm.language} text=${warm.text}".format(ratio)
            report.append(line).append('\n')
            Log.i(TAG, line)

            assertTrue("${model.id} produced no text", warm.text.isNotBlank())
        }

        val target = File(context.getExternalFilesDir(null), REPORT_NAME)
        target.writeText(report.toString())
        Log.i(TAG, "report written to ${target.absolutePath}")
    }

    private suspend fun measure(
        engine: WhisperEngine,
        model: WhisperModel,
        file: File,
        audio: PcmAudio,
    ): Run {
        val started = System.currentTimeMillis()
        val result = engine.transcribe(
            model = model,
            modelFile = file,
            audio = audio,
            language = "de",
            onProgress = {},
        )
        return Run(
            elapsedMs = System.currentTimeMillis() - started,
            language = result.language,
            text = result.text,
        )
    }

    private data class Run(val elapsedMs: Long, val language: String, val text: String)

    private suspend fun germanAudio(context: Context): PcmAudio {
        val wav = File(context.cacheDir, "german-sample.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("german_sample.wav")
            .use { input -> wav.outputStream().use { output -> input.copyTo(output) } }
        return AudioDecoder().decode(wav)
    }

    private companion object {
        const val TAG = "WhisperCompare"
        const val REPORT_NAME = "model-comparison.txt"
    }
}
