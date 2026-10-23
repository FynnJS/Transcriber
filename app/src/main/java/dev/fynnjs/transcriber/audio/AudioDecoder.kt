package dev.fynnjs.transcriber.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class AudioDecodingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Decodes a shared voice message (OGG/Opus, but any audio container MediaExtractor knows)
 * to 16 kHz mono float PCM, the format whisper.cpp expects.
 */
@Singleton
class AudioDecoder @Inject constructor() {

    suspend fun decode(file: File): PcmAudio = withContext(Dispatchers.Default) {
        decodeBlocking(file)
    }

    private fun decodeBlocking(file: File): PcmAudio {
        if (!file.isFile) {
            throw AudioDecodingException("Audio file missing")
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    inputFormat = format
                    break
                }
            }
            if (trackIndex < 0 || inputFormat == null) {
                throw AudioDecodingException("No audio track")
            }

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw AudioDecodingException("No mime type")
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val builder = FloatBufferBuilder()
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, 16_000)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            var inputDone = false
            var outputDone = false
            var idleIterations = 0

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = if (inputBuffer == null) -1 else extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        idleIterations = 0
                        if (info.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null) {
                                outputBuffer.position(info.offset)
                                outputBuffer.limit(info.offset + info.size)
                                appendMonoSamples(outputBuffer, channels, pcmEncoding, builder)
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        pcmEncoding = runCatching {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }.getOrDefault(AudioFormat.ENCODING_PCM_16BIT)
                    }

                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        idleIterations++
                        if (idleIterations > 200) {
                            throw AudioDecodingException("Decoder stalled")
                        }
                    }
                }
            }

            val decoded = builder.toFloatArray()
            if (decoded.isEmpty()) {
                throw AudioDecodingException("Decoder returned no samples")
            }
            val mono = resampleLinear(decoded, sampleRate, TARGET_SAMPLE_RATE)
            return PcmAudio(
                samples = mono,
                sampleRate = TARGET_SAMPLE_RATE,
                durationMs = durationMillis(mono.size, TARGET_SAMPLE_RATE),
            )
        } catch (e: AudioDecodingException) {
            throw e
        } catch (e: Exception) {
            throw AudioDecodingException("Could not decode audio", e)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
        private const val TIMEOUT_US = 10_000L
    }
}
