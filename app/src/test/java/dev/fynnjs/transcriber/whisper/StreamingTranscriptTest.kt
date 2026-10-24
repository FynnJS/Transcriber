package dev.fynnjs.transcriber.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTranscriptTest {

    @Test
    fun `short messages stay a single pass`() {
        val chunks = TranscriptChunks.plan(TranscriptChunks.SINGLE_PASS_SAMPLES)

        assertEquals(1, chunks.size)
        assertEquals(0, chunks.single().startSample)
        assertEquals(TranscriptChunks.SINGLE_PASS_SAMPLES, chunks.single().endSample)
    }

    @Test
    fun `long messages are split into overlapping windows`() {
        val total = 20 * TranscriptChunks.SAMPLE_RATE

        val chunks = TranscriptChunks.plan(total)

        assertEquals(listOf(0, 128_000, 256_000), chunks.map { it.startSample })
        assertEquals(listOf(160_000, 288_000, 320_000), chunks.map { it.endSample })
        assertEquals(total, chunks.last().endSample)
        chunks.zipWithNext().forEach { (previous, next) ->
            assertEquals(
                TranscriptChunks.OVERLAP_SAMPLES,
                previous.endSample - next.startSample,
            )
        }
    }

    @Test
    fun `windows cover the whole message without gaps`() {
        val total = 61 * TranscriptChunks.SAMPLE_RATE

        val chunks = TranscriptChunks.plan(total)

        assertEquals(0, chunks.first().startSample)
        assertTrue(chunks.all { it.sampleCount > 0 })
        assertTrue(chunks.all { it.sampleCount <= TranscriptChunks.WINDOW_SAMPLES })
        chunks.zipWithNext().forEach { (previous, next) ->
            assertTrue(next.startSample < previous.endSample)
        }
    }

    @Test
    fun `empty audio has no windows`() {
        assertTrue(TranscriptChunks.plan(0).isEmpty())
    }

    @Test
    fun `encoder context follows the window length`() {
        assertEquals(100, TranscriptChunks.audioContextFrames(AudioChunk(0, 16_000)))
        assertEquals(550, TranscriptChunks.audioContextFrames(AudioChunk(0, 160_000)))
    }

    @Test
    fun `chunks are appended without overlap duplicates`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir treffen uns morgen",
            chunk = "uns morgen am Bahnhof",
        )

        assertEquals("Wir treffen uns morgen am Bahnhof", merged)
    }

    @Test
    fun `overlap matching ignores case and punctuation`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir treffen uns morgen",
            chunk = "Morgen am Bahnhof",
        )

        assertEquals("Wir treffen uns morgen am Bahnhof", merged)
    }

    @Test
    fun `a word cut at the window border is completed`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir fahren zum Bahnh",
            chunk = "Bahnhof und weiter",
        )

        assertEquals("Wir fahren zum Bahnhof und weiter", merged)
    }

    @Test
    fun `a fragment at the new window is dropped`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir fahren zum Bahnhof",
            chunk = "nhof und weiter",
        )

        assertEquals("Wir fahren zum Bahnhof und weiter", merged)
    }

    @Test
    fun `a window that ends mid-sentence loses its false period`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir treffen uns am Bahnhof.",
            chunk = "Bahnhof und fahren dann weiter",
        )

        assertEquals("Wir treffen uns am Bahnhof und fahren dann weiter", merged)
    }

    @Test
    fun `a question mark from the window end disappears before a continuation`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Hallo, wie geht's?",
            chunk = "wie geht's dir?",
        )

        assertEquals("Hallo, wie geht's dir?", merged)
    }

    @Test
    fun `a real sentence end survives before a new sentence`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir treffen uns am Bahnhof.",
            chunk = "Bahnhof. Wir fahren dann weiter",
        )

        assertEquals("Wir treffen uns am Bahnhof. Wir fahren dann weiter", merged)
    }

    @Test
    fun `a sentence end without overlap is kept`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Erster Satz.",
            chunk = "Zweiter Satz.",
        )

        assertEquals("Erster Satz. Zweiter Satz.", merged)
    }

    @Test
    fun `a question mark stays before an uppercase continuation`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wie geht's?",
            chunk = "Wie geht's? Mir gut.",
        )

        assertEquals("Wie geht's? Mir gut.", merged)
    }

    @Test
    fun `punctuation from inside the overlap is kept`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir treffen uns am Bahnhof",
            chunk = "Bahnhof, und fahren dann weiter",
        )

        assertEquals("Wir treffen uns am Bahnhof, und fahren dann weiter", merged)
    }

    @Test
    fun `a cut word is completed even after repeated words`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir treffen uns am Bahnh",
            chunk = "am Bahnhof und fahren weiter",
        )

        assertEquals("Wir treffen uns am Bahnhof und fahren weiter", merged)
    }

    @Test
    fun `a window that starts mid-word drops the repeated words`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir treffen uns am Bahnhof und wir",
            chunk = "nhof und wir fahren weiter",
        )

        assertEquals("Wir treffen uns am Bahnhof und wir fahren weiter", merged)
    }

    @Test
    fun `a fragment of the last word is dropped`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Wir treffen uns am Bahnhof",
            chunk = "Bahnh und fahren weiter",
        )

        assertEquals("Wir treffen uns am Bahnhof und fahren weiter", merged)
    }

    @Test
    fun `unrelated text is appended with a space`() {
        val merged = TranscriptMerger.merge(
            accumulated = "Hallo Welt",
            chunk = "wie geht es dir",
        )

        assertEquals("Hallo Welt wie geht es dir", merged)
    }

    @Test
    fun `empty parts keep the other side`() {
        assertEquals("Hallo", TranscriptMerger.merge("Hallo", "   "))
        assertEquals("Hallo", TranscriptMerger.merge("", " Hallo "))
    }
}
