package dev.fynnjs.transcriber.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryEntryTest {

    @Test
    fun `live preview wins while transcribing`() {
        val entry = entry(
            status = TranscriptionStatus.TRANSCRIBING,
            transcript = "Altes Transkript",
            partialTranscript = "Erste Worte",
        )

        assertTrue(entry.isLivePreview)
        assertEquals("Erste Worte", entry.displayedTranscript)
    }

    @Test
    fun `old transcript stays visible until the first preview arrives`() {
        val entry = entry(
            status = TranscriptionStatus.TRANSCRIBING,
            transcript = "Altes Transkript",
            partialTranscript = null,
        )

        assertFalse(entry.isLivePreview)
        assertEquals("Altes Transkript", entry.displayedTranscript)
    }

    @Test
    fun `finished entries ignore a leftover preview`() {
        val entry = entry(
            status = TranscriptionStatus.DONE,
            transcript = "Fertiges Transkript",
            partialTranscript = "Rest",
        )

        assertFalse(entry.isLivePreview)
        assertEquals("Fertiges Transkript", entry.displayedTranscript)
    }

    private fun entry(
        status: TranscriptionStatus,
        transcript: String?,
        partialTranscript: String?,
    ) = HistoryEntry(
        createdAt = 0L,
        sourceFileName = "PTT.opus",
        durationMs = 1000L,
        audioPath = null,
        audioHash = "hash",
        transcript = transcript,
        language = "de",
        modelUsed = WhisperModel.SMALL.id,
        status = status,
        partialTranscript = partialTranscript,
    )
}
