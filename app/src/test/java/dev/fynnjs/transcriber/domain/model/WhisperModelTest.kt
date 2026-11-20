package dev.fynnjs.transcriber.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperModelTest {

    @Test
    fun `every catalog entry has a usable download and checksum`() {
        WhisperModel.entries.forEach { model ->
            assertTrue("id: ${model.id}", model.id.isNotBlank())
            assertTrue("file: ${model.fileName}", model.fileName.startsWith("ggml-"))
            assertTrue("file: ${model.fileName}", model.fileName.endsWith(".bin"))
            assertTrue("size: ${model.id}", model.approxBytes > 0)
            assertTrue("sha: ${model.id}", Regex("^[0-9a-f]{64}$").matches(model.sha256))
            assertTrue("url: ${model.id}", model.downloadUrl.startsWith("https://"))
            assertTrue("url: ${model.id}", model.downloadUrl.endsWith("/${model.fileName}"))
        }
    }

    @Test
    fun `ids and file names stay unique`() {
        assertEquals(WhisperModel.entries.size, WhisperModel.entries.map { it.id }.toSet().size)
        assertEquals(WhisperModel.entries.size, WhisperModel.entries.map { it.fileName }.toSet().size)
    }

    @Test
    fun `small remains the default so no large download starts unasked`() {
        assertEquals(WhisperModel.SMALL, WhisperModel.DEFAULT)
    }

    @Test
    fun `turbo is selectable but far larger than small`() {
        assertTrue(WhisperModel.LARGE_V3_TURBO.approxBytes > WhisperModel.SMALL.approxBytes * 2)
        assertEquals("large-v3-turbo", WhisperModel.fromId("large-v3-turbo")?.id)
    }
}
