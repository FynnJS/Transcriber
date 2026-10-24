package dev.fynnjs.transcriber.ui.screens.detail

import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptSectionTest {

    @Test
    fun `finished entry shows its transcript`() {
        assertEquals(
            TranscriptSection.Transcript,
            transcriptSection(TranscriptionStatus.DONE, hasTranscript = true),
        )
    }

    @Test
    fun `finished entry without text shows the empty state`() {
        assertEquals(
            TranscriptSection.NoText,
            transcriptSection(TranscriptionStatus.DONE, hasTranscript = false),
        )
    }

    @Test
    fun `queued entry shows a pending hint instead of the empty state`() {
        assertEquals(
            TranscriptSection.Pending,
            transcriptSection(TranscriptionStatus.QUEUED, hasTranscript = false),
        )
    }

    @Test
    fun `transcribing entry shows a pending hint instead of the empty state`() {
        assertEquals(
            TranscriptSection.Pending,
            transcriptSection(TranscriptionStatus.TRANSCRIBING, hasTranscript = false),
        )
    }

    @Test
    fun `failed entry without text leaves the transcript area to the error state`() {
        assertEquals(
            TranscriptSection.Hidden,
            transcriptSection(TranscriptionStatus.FAILED, hasTranscript = false),
        )
    }

    @Test
    fun `failed retry keeps the previous transcript`() {
        assertEquals(
            TranscriptSection.Transcript,
            transcriptSection(TranscriptionStatus.FAILED, hasTranscript = true),
        )
    }
}
