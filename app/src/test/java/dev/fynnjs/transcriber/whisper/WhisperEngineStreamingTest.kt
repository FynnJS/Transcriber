package dev.fynnjs.transcriber.whisper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperEngineStreamingTest {

    private val engine = WhisperEngine()

    @Test
    fun `long audio reports every window as a growing preview`() = runTest {
        val samples = FloatArray(20 * TranscriptChunks.SAMPLE_RATE)
        val previews = mutableListOf<String>()
        val languages = mutableListOf<String>()
        val windowSizes = mutableListOf<Int>()
        val audioContexts = mutableListOf<Int>()
        val progress = mutableListOf<Int>()
        val words = listOf("eins", "zwei", "drei")

        val result = engine.transcribeInWindows(
            samples = samples,
            language = "auto",
            onProgress = { progress += it },
            onPartial = { previews += it },
            isCancelled = { false },
            transcribeWindow = { window, language, audioContext, onWindowProgress ->
                languages += language
                windowSizes += window.size
                audioContexts += audioContext
                onWindowProgress(50)
                onWindowProgress(99)
                WhisperResult(text = words[windowSizes.size - 1], language = "de")
            },
        )

        assertEquals(listOf(160_000, 160_000, 64_000), windowSizes)
        assertEquals(listOf("auto", "de", "de"), languages)
        assertEquals(listOf(550, 550, 250), audioContexts)
        assertEquals(listOf("eins", "eins zwei", "eins zwei drei"), previews)
        assertEquals("eins zwei drei", result.text)
        assertEquals("de", result.language)
        assertEquals(25, progress.first())
        assertEquals(99, progress.max())
        assertTrue(progress.all { it in 0..99 })
    }

    @Test
    fun `short audio stays a single pass without a preview`() = runTest {
        val samples = FloatArray(10 * TranscriptChunks.SAMPLE_RATE)
        val previews = mutableListOf<String>()
        val audioContexts = mutableListOf<Int>()

        val result = engine.transcribeInWindows(
            samples = samples,
            language = "de",
            onProgress = {},
            onPartial = { previews += it },
            isCancelled = { false },
            transcribeWindow = { window, _, audioContext, _ ->
                audioContexts += audioContext
                WhisperResult(text = "Kurze Nachricht", language = "de")
            },
        )

        assertEquals(0, audioContexts.single())
        assertTrue(previews.isEmpty())
        assertEquals("Kurze Nachricht", result.text)
    }

    @Test
    fun `a canceled transcription stops before the next window`() = runTest {
        var calls = 0

        val failure = runCatching {
            engine.transcribeInWindows(
                samples = FloatArray(20 * TranscriptChunks.SAMPLE_RATE),
                language = "de",
                onProgress = {},
                onPartial = {},
                isCancelled = { true },
                transcribeWindow = { _, _, _, _ ->
                    calls++
                    WhisperResult(text = "Text", language = "de")
                },
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(0, calls)
    }

    @Test
    fun `audio without samples fails`() = runTest {
        val failure = runCatching {
            engine.transcribeInWindows(
                samples = FloatArray(0),
                language = "de",
                onProgress = {},
                onPartial = {},
                isCancelled = { false },
                transcribeWindow = { _, _, _, _ -> WhisperResult(text = "", language = "de") },
            )
        }.exceptionOrNull()

        assertTrue(failure is WhisperException)
    }
}
