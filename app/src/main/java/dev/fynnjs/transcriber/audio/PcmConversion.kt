package dev.fynnjs.transcriber.audio

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class PcmAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val durationMs: Long,
)

/** Growable float array to collect decoded PCM without boxing. */
class FloatBufferBuilder(initialCapacity: Int = 1 shl 16) {
    private var data = FloatArray(initialCapacity.coerceAtLeast(16))
    var size: Int = 0
        private set

    fun append(value: Float) {
        if (size == data.size) {
            data = data.copyOf(data.size * 2)
        }
        data[size++] = value
    }

    fun appendAll(values: FloatArray) {
        values.forEach(::append)
    }

    fun toFloatArray(): FloatArray = data.copyOf(size)
}

/**
 * Converts an interleaved MediaCodec output buffer to mono float samples at the source rate.
 * Channel values are averaged; the buffer position and limit are respected.
 */
fun appendMonoSamples(
    buffer: ByteBuffer,
    channels: Int,
    pcmEncoding: Int,
    out: FloatBufferBuilder,
) {
    val safeChannels = channels.coerceAtLeast(1)
    val ordered = buffer.order(ByteOrder.nativeOrder())
    when (pcmEncoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> {
            val floats = ordered.asFloatBuffer()
            while (floats.remaining() >= safeChannels) {
                var sum = 0f
                repeat(safeChannels) { sum += floats.get() }
                out.append(sum / safeChannels)
            }
        }

        AudioFormat.ENCODING_PCM_8BIT -> {
            while (ordered.remaining() >= safeChannels) {
                var sum = 0f
                repeat(safeChannels) {
                    val unsigned = ordered.get().toInt() and 0xFF
                    sum += (unsigned - 128) / 128f
                }
                out.append(sum / safeChannels)
            }
        }

        else -> {
            // 16-bit is the default for audio decoders.
            while (ordered.remaining() >= safeChannels * 2) {
                var sum = 0f
                repeat(safeChannels) { sum += ordered.short / 32768f }
                out.append(sum / safeChannels)
            }
        }
    }
}

/** Linear resampling; good enough for speech at 16 kHz and cheap on device. */
fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
    if (fromRate == toRate || input.isEmpty()) return input
    val outputSize = (input.size.toLong() * toRate / fromRate).toInt().coerceAtLeast(1)
    val step = fromRate.toDouble() / toRate
    val output = FloatArray(outputSize)
    for (i in output.indices) {
        val position = i * step
        val index = position.toInt()
        val fraction = (position - index).toFloat()
        val current = input[index.coerceIn(0, input.lastIndex)]
        val next = input[(index + 1).coerceIn(0, input.lastIndex)]
        output[i] = current + (next - current) * fraction
    }
    return output
}

fun durationMillis(sampleCount: Int, sampleRate: Int): Long =
    if (sampleRate <= 0) 0L else (sampleCount * 1000L / sampleRate).toLong()

fun FloatArray.rms(): Float = if (isEmpty()) 0f else {
    var sum = 0.0
    forEach { sum += it.toDouble() * it }
    (sum / size).toFloat()
}

fun formatDurationShort(ms: Long): String {
    val totalSeconds = (ms / 1000.0).roundToInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
