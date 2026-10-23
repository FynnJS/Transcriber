package dev.fynnjs.transcriber.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fynnjs.transcriber.data.audio.AudioFileStore
import dev.fynnjs.transcriber.data.local.TranscriberDatabase
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HistoryRepositoryTest {

    private lateinit var database: TranscriberDatabase
    private lateinit var repository: HistoryRepositoryImpl
    private lateinit var audioStore: AudioFileStore
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TranscriberDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audioStore = AudioFileStore(context)
        repository = HistoryRepositoryImpl(database.historyDao(), audioStore)
    }

    @After
    fun tearDown() {
        database.close()
        audioStore.deleteAllFiles()
    }

    @Test
    fun `entries are ordered newest first`() = runTest {
        repository.insert(entry(createdAt = 1000, hash = "a", transcript = "Erste"))
        repository.insert(entry(createdAt = 2000, hash = "b", transcript = "Zweite"))

        val entries = repository.entries.first()

        assertEquals(listOf("Zweite", "Erste"), entries.map { it.transcript })
    }

    @Test
    fun `full text search finds transcript and label by prefix`() = runTest {
        repository.insert(
            entry(
                createdAt = 1000,
                hash = "a",
                transcript = "Das Protokoll ist fertig",
                label = "Arbeit",
            ),
        )
        repository.insert(
            entry(createdAt = 2000, hash = "b", transcript = "Einkaufsliste für morgen", label = null),
        )

        val transcriptHits = repository.search("Proto").first()
        val labelHits = repository.search("Arbeit").first()
        val bothTerms = repository.search("Protokoll Arbeit").first()
        val noHits = repository.search("Urlaub").first()

        assertEquals(1, transcriptHits.size)
        assertEquals(1, labelHits.size)
        assertEquals(1, bothTerms.size)
        assertTrue(noHits.isEmpty())
    }

    @Test
    fun `duplicate hash is found before inserting again`() = runTest {
        val id = repository.insert(entry(createdAt = 1000, hash = "same", transcript = "Hallo"))
        val found = repository.findByHash("same")

        assertNotNull(found)
        assertEquals(id, found?.id)
    }

    @Test
    fun `delete removes entry and audio file`() = runTest {
        val audio = audioFile("delete-me.opus")
        val id = repository.insert(
            entry(createdAt = 1000, hash = "a", transcript = "Text", audioPath = audio.absolutePath),
        )

        repository.delete(id)

        assertNull(repository.get(id))
        assertFalse(audio.exists())
    }

    @Test
    fun `expiring audio keeps the entry and clears its path`() = runTest {
        val audio = audioFile("old.opus")
        val id = repository.insert(
            entry(createdAt = 1000, hash = "a", transcript = "Alt", audioPath = audio.absolutePath),
        )

        val deleted = repository.expireAudio(cutoff = 2000)

        assertEquals(1, deleted)
        val reloaded = repository.get(id)
        assertNotNull(reloaded)
        assertNull(reloaded?.audioPath)
        assertFalse(audio.exists())
    }

    @Test
    fun `soundless result is stored as null transcript`() = runTest {
        val id = repository.insert(entry(createdAt = 1000, hash = "a", transcript = null))
        repository.complete(id, transcript = "   ", language = "de", model = dev.fynnjs.transcriber.domain.model.WhisperModel.BASE, durationMs = 1234)

        val reloaded = repository.get(id)
        assertEquals(TranscriptionStatus.DONE, reloaded?.status)
        assertNull(reloaded?.transcript)
        assertEquals(1234L, reloaded?.durationMs)
    }

    @Test
    fun `stuck transcriptions are queued again`() = runTest {
        val id = repository.insert(
            entry(
                createdAt = 1000,
                hash = "a",
                transcript = null,
                status = TranscriptionStatus.TRANSCRIBING,
            ),
        )

        val count = repository.resetStuckEntries()

        assertEquals(1, count)
        assertEquals(TranscriptionStatus.QUEUED, repository.get(id)?.status)
    }

    private fun audioFile(name: String): File {
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        return File(dir, name).apply { writeText("fake audio") }
    }

    private fun entry(
        createdAt: Long,
        hash: String,
        transcript: String?,
        label: String? = null,
        audioPath: String? = null,
        status: TranscriptionStatus = TranscriptionStatus.DONE,
    ) = HistoryEntry(
        createdAt = createdAt,
        sourceFileName = "PTT.opus",
        durationMs = 42_000,
        audioPath = audioPath,
        audioHash = hash,
        transcript = transcript,
        language = "de",
        modelUsed = "small",
        status = status,
        label = label,
    )
}
