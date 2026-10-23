package dev.fynnjs.transcriber.audio

import androidx.media3.common.util.UnstableApi
import dev.fynnjs.transcriber.data.repository.buildFtsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmConversionTest {

    @Test
    fun `resample 48k to 16k shrinks length by three`() {
        val input = FloatArray(48_000) { 0.5f }
        val output = resampleLinear(input, 48_000, 16_000)
        assertEquals(16_000, output.size)
        assertTrue(output.all { it in 0.49f..0.51f })
    }

    @Test
    fun `resample keeps 16k input untouched`() {
        val input = floatArrayOf(-1f, 0f, 1f)
        assertEquals(input, resampleLinear(input, 16_000, 16_000))
    }

    @Test
    fun `downmix averages stereo samples`() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        buffer.putShort(32767)
        buffer.putShort(32767)
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.flip()

        val builder = FloatBufferBuilder()
        appendMonoSamples(buffer, channels = 2, pcmEncoding = 2, out = builder)
        val samples = builder.toFloatArray()

        assertEquals(2, samples.size)
        assertTrue(samples[0] > 0.99f)
        assertEquals(0f, samples[1], 0.0001f)
    }

    @Test
    fun `duration is computed from sample count`() {
        assertEquals(1000L, durationMillis(16_000, 16_000))
        assertEquals(500L, durationMillis(8_000, 16_000))
    }

    @Test
    fun `fts query uses prefix tokens and drops punctuation`() {
        assertEquals("Projekt* Bericht*", buildFtsQuery("Projekt, Bericht!"))
        assertEquals("Hallo*", buildFtsQuery("  Hallo  "))
        assertNull(buildFtsQuery("   "))
        assertNull(buildFtsQuery("!!! ???"))
    }
}
