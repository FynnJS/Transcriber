package dev.fynnjs.transcriber.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDaoInstrumentedTest {

    private lateinit var database: TranscriberDatabase
    private lateinit var dao: HistoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TranscriberDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.historyDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ftsSearchFindsTranscriptAndLabel() = runBlocking {
        dao.insert(entity(id = 1, transcript = "Der Bericht ist fertig", label = "Arbeit"))
        dao.insert(entity(id = 2, transcript = "Milch und Brot kaufen", label = null))

        val byTranscript = dao.search("Bericht*").first()
        val byLabel = dao.search("Arbeit*").first()

        assertEquals(listOf(1L), byTranscript.map { it.id })
        assertEquals(listOf(1L), byLabel.map { it.id })
    }

    @Test
    fun updatingTranscriptUpdatesFtsIndex() = runBlocking {
        dao.insert(entity(id = 1, transcript = "alt", label = null))
        dao.complete(1, "neuer Wortlaut", "de", "small", 1000)

        val hits = dao.search("Wortlaut*").first()

        assertEquals(1, hits.size)
    }

    private fun entity(id: Long, transcript: String?, label: String?) = HistoryEntryEntity(
        id = id,
        createdAt = id * 1000,
        sourceFileName = "PTT.opus",
        durationMs = 1000,
        audioPath = null,
        audioHash = "hash-$id",
        transcript = transcript,
        language = "de",
        modelUsed = "small",
        status = "DONE",
        errorMessage = null,
        label = label,
        progress = 100,
    )
}
