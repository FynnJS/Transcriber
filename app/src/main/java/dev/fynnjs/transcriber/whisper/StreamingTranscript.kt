package dev.fynnjs.transcriber.whisper

/** One window of 16 kHz mono PCM that is transcribed in a single whisper pass. */
internal data class AudioChunk(val startSample: Int, val endSample: Int) {
    val sampleCount: Int get() = endSample - startSample
}

/**
 * Splits a decoded voice message into overlapping windows.
 *
 * whisper always decodes in windows of at most 30 s. Shorter windows trade a little context for
 * a preview that grows while the transcription runs; the overlap keeps words at the window
 * borders intact, and [TranscriptMerger] removes the repeated text.
 */
internal object TranscriptChunks {
    const val SAMPLE_RATE = 16_000

    const val WINDOW_SAMPLES = 10 * SAMPLE_RATE
    const val OVERLAP_SAMPLES = 2 * SAMPLE_RATE

    /** Messages up to this length are transcribed in one pass, exactly as before. */
    const val SINGLE_PASS_SAMPLES = 15 * SAMPLE_RATE

    /** whisper's encoder runs at 50 frames per second and its default context covers 30 s. */
    private const val FRAMES_PER_SECOND = 50
    private const val DEFAULT_AUDIO_CONTEXT_FRAMES = 1500

    fun plan(totalSamples: Int): List<AudioChunk> {
        if (totalSamples <= 0) return emptyList()
        if (totalSamples <= SINGLE_PASS_SAMPLES) return listOf(AudioChunk(0, totalSamples))

        val stride = WINDOW_SAMPLES - OVERLAP_SAMPLES
        val chunks = mutableListOf<AudioChunk>()
        var start = 0
        while (true) {
            val end = minOf(start + WINDOW_SAMPLES, totalSamples)
            chunks += AudioChunk(start, end)
            if (end == totalSamples) break
            start += stride
        }
        return chunks
    }

    /**
     * Encoder context that covers [chunk] plus one second of padding. Without this every call
     * would run whisper's encoder over the full 30 s context and multiply the work per chunk.
     */
    fun audioContextFrames(chunk: AudioChunk): Int {
        val seconds = chunk.sampleCount / SAMPLE_RATE.toDouble() + 1.0
        return (seconds * FRAMES_PER_SECOND).toInt()
            .coerceIn(1, DEFAULT_AUDIO_CONTEXT_FRAMES)
    }
}

/**
 * Appends a transcribed window to the transcript so far without repeating the overlap.
 *
 * whisper cuts a window at a word border it chose itself, so either window can end in the middle
 * of a word. The overlap is matched word by word, tolerating a fragment on either side. At the
 * junction the merger keeps punctuation that only the new window heard and drops a period that
 * is followed by a lowercase continuation, because that period marks the window end and not the
 * end of the sentence.
 */
internal object TranscriptMerger {
    private const val MAX_OVERLAP_WORDS = 10
    private const val MIN_PARTIAL_WORD = 3
    private const val MIN_SENTENCE_END_WORD = 3
    private val whitespace = Regex("\\s+")
    private val trailingPunctuation = Regex("[.!?,;:]+$")

    /** How many words to drop on each side to remove the repeated overlap. */
    private data class Overlap(val accDrop: Int, val chunkStart: Int)

    private enum class WordRelation {
        NONE,

        /** The accumulated word was cut; the chunk has the complete word. */
        ACC_FRAGMENT,

        /** The chunk only hears the end of a word that is already complete. */
        CHUNK_FRAGMENT,
    }

    fun merge(accumulated: String, chunk: String): String {
        val acc = accumulated.trim()
        val add = chunk.trim()
        if (add.isEmpty()) return acc
        if (acc.isEmpty()) return add

        val accWords = acc.split(whitespace)
        val addWords = add.split(whitespace)

        var prefix = accWords
        var suffix = addWords
        val overlap = findOverlap(accWords, addWords)
        if (overlap != null) {
            prefix = accWords.dropLast(overlap.accDrop)
            suffix = addWords.drop(overlap.chunkStart)
            // The last overlapping word sits in the middle of the new window, so its punctuation
            // is genuine and may replace the punctuation of the previous window's copy.
            if (suffix.isNotEmpty() && overlap.accDrop == 0 && overlap.chunkStart > 0) {
                prefix = adoptPunctuation(prefix, addWords[overlap.chunkStart - 1])
            }
        }

        if (prefix.isNotEmpty() && suffix.isNotEmpty()) {
            prefix = dropFalseSentenceEnd(prefix, suffix.first())
        }
        return join(prefix + suffix)
    }

    /**
     * Finds the longest overlap between the end of [acc] and the start of [add].
     *
     * The first or the last word of the overlap may be a fragment: whisper cuts a window at a
     * boundary it chose, so either side can end in the middle of a word.
     */
    private fun findOverlap(acc: List<String>, add: List<String>): Overlap? {
        val max = minOf(MAX_OVERLAP_WORDS, acc.size, add.size)
        for (size in max downTo 1) {
            if (exactMatches(acc, add, size, from = 0, until = size)) {
                return Overlap(accDrop = 0, chunkStart = size)
            }

            val firstFragment = size > 1 &&
                relation(acc[acc.size - size], add[0]) == WordRelation.CHUNK_FRAGMENT
            val lastRelation = relation(acc[acc.size - 1], add[size - 1])
            if (lastRelation == WordRelation.NONE && !firstFragment) continue

            val from = if (firstFragment) 1 else 0
            val until = if (lastRelation == WordRelation.NONE) size else size - 1
            if (!exactMatches(acc, add, size, from, until)) continue

            return if (lastRelation == WordRelation.ACC_FRAGMENT) {
                Overlap(accDrop = 1, chunkStart = size - 1)
            } else {
                Overlap(accDrop = 0, chunkStart = size)
            }
        }
        return null
    }

    /** Compares pairs of the overlap; [from] and [until] select the range to check. */
    private fun exactMatches(
        acc: List<String>,
        add: List<String>,
        size: Int,
        from: Int,
        until: Int,
    ): Boolean {
        for (index in from until until) {
            val left = key(acc[acc.size - size + index])
            val right = key(add[index])
            if (left.isEmpty() || left != right) return false
        }
        return true
    }

    private fun relation(accWord: String, chunkWord: String): WordRelation {
        val accKey = key(accWord)
        val chunkKey = key(chunkWord)
        if (accKey.isEmpty() || chunkKey.isEmpty() || accKey == chunkKey) {
            return WordRelation.NONE
        }
        return when {
            accKey.length >= MIN_PARTIAL_WORD && chunkKey.startsWith(accKey) ->
                WordRelation.ACC_FRAGMENT

            accKey.length >= MIN_PARTIAL_WORD && chunkKey.endsWith(accKey) ->
                WordRelation.ACC_FRAGMENT

            chunkKey.length >= MIN_PARTIAL_WORD && accKey.startsWith(chunkKey) ->
                WordRelation.CHUNK_FRAGMENT

            chunkKey.length >= MIN_PARTIAL_WORD && accKey.endsWith(chunkKey) ->
                WordRelation.CHUNK_FRAGMENT

            else -> WordRelation.NONE
        }
    }

    /**
     * A window ends where whisper stopped, not where the sentence ended. A sentence terminator
     * followed by a lowercase continuation is therefore a window artifact and gets removed.
     */
    private fun dropFalseSentenceEnd(prefix: List<String>, next: String): List<String> {
        val last = prefix.last()
        val terminator = last.lastOrNull()
        if (terminator != '.' && terminator != '!' && terminator != '?') return prefix
        // "...", "!!" and "?!" carry meaning of their own; leave them alone.
        if (last.length >= 2 && last[last.length - 2] == terminator) return prefix
        // A short word before a period is usually an abbreviation ("z. B."), not a sentence end.
        if (terminator == '.' && key(last).length < MIN_SENTENCE_END_WORD) return prefix
        val firstLetter = next.firstOrNull { it.isLetter() } ?: return prefix
        if (!firstLetter.isLowerCase()) return prefix

        val trimmed = last.dropLast(1)
        val withoutLast = prefix.dropLast(1)
        return if (trimmed.isEmpty()) withoutLast else withoutLast + trimmed
    }

    /** Keeps punctuation that only the chunk heard, for example a comma inside the overlap. */
    private fun adoptPunctuation(prefix: List<String>, chunkWord: String): List<String> {
        if (prefix.isEmpty() || trailingPunctuation.containsMatchIn(prefix.last())) return prefix
        val punctuation = trailingPunctuation.find(chunkWord)?.value ?: return prefix
        return prefix.dropLast(1) + (prefix.last() + punctuation)
    }

    private fun key(word: String): String =
        word.filter { it.isLetterOrDigit() }.lowercase()

    private fun join(words: List<String>): String = words.joinToString(" ")
}
