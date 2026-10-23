package dev.fynnjs.transcriber.audio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioDecoderInstrumentedTest {

    @Test
    fun decodesSpeechSampleTo16kMonoPcm() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "jfk.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("jfk.wav").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }

        val audio = AudioDecoder().decode(file)

        assertEquals(16_000, audio.sampleRate)
        assertTrue(audio.durationMs in 10_000L..12_000L)
        assertTrue(audio.samples.size > 160_000)
        assertTrue(audio.samples.rms() > 0.001f)
    }
}
