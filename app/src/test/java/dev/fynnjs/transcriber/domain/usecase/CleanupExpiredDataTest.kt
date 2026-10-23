package dev.fynnjs.transcriber.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fynnjs.transcriber.data.audio.AudioFileStore
import dev.fynnjs.transcriber.data.local.TranscriberDatabase
import dev.fynnjs.transcriber.data.repository.HistoryRepositoryImpl
import dev.fynnjs.transcriber.domain.model.AppSettings
import dev.fynnjs.transcriber.domain.model.AudioRetention
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.ThemeMode
import dev.fynnjs.transcriber.domain.model.TranscriptionLanguage
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.TranscriptRetention
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class CleanupExpiredDataTest {

    private lateinit var database: TranscriberDatabase
    private lateinit var repository: HistoryRepositoryImpl
    private lateinit var audioStore: AudioFileStore
    private lateinit var context: Context
    private lateinit var settings: FakeSettingsRepository
    private lateinit var cleanup: CleanupExpiredData

    private val now = 10_000L * 24 * 60 * 60 * 1000

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TranscriberDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audioStore = AudioFileStore(context)
        repository = HistoryRepositoryImpl(database.historyDao(), audioStore)
        settings = FakeSettingsRepository()
        cleanup = CleanupExpiredData(repository, settings)
    }

    @After
    fun tearDown() {
        database.close()
        audioStore.deleteAllFiles()
    }

    @Test
    fun `audio older than retention is removed, entry stays`() = runTest {
        settings.value = settings.value.copy(audioRetention = AudioRetention.DAYS_30)
        val oldAudio = audioFile("old.opus")
        val newAudio = audioFile("new.opus")
        val oldId = repository.insert(entry(now - 31 * DAY, "old", oldAudio))
        val newId = repository.insert(entry(now - 2 * DAY, "new", newAudio))

        cleanup.run(now)

        assertFalse(oldAudio.exists())
        assertTrue(newAudio.exists())
        assertNull(repository.get(oldId)?.audioPath)
        assertNotNull(repository.get(newId)?.audioPath)
    }

    @Test
    fun `transcript retention deletes old entries entirely`() = runTest {
        settings.value = settings.value.copy(
            audioRetention = AudioRetention.NEVER,
            transcriptRetention = TranscriptRetention.DAYS_7,
        )
        val oldAudio = audioFile("old.opus")
        val oldId = repository.insert(entry(now - 8 * DAY, "old", oldAudio))
        val newId = repository.insert(entry(now - 1 * DAY, "new", null))

        cleanup.run(now)

        assertNull(repository.get(oldId))
        assertFalse(oldAudio.exists())
        assertNotNull(repository.get(newId))
    }

    @Test
    fun `immediate audio retention only deletes audio of completed entries`() = runTest {
        settings.value = settings.value.copy(audioRetention = AudioRetention.IMMEDIATELY)
        val doneAudio = audioFile("done.opus")
        val failedAudio = audioFile("failed.opus")
        val doneId = repository.insert(entry(now, "done", doneAudio, TranscriptionStatus.DONE))
        val failedId = repository.insert(entry(now, "failed", failedAudio, TranscriptionStatus.FAILED))

        cleanup.run(now)

        assertFalse(doneAudio.exists())
        assertTrue(failedAudio.exists())
        assertNull(repository.get(doneId)?.audioPath)
        assertNotNull(repository.get(failedId)?.audioPath)
    }

    @Test
    fun `never retention keeps everything`() = runTest {
        settings.value = settings.value.copy(
            audioRetention = AudioRetention.NEVER,
            transcriptRetention = TranscriptRetention.NEVER,
        )
        val audio = audioFile("keep.opus")
        val id = repository.insert(entry(now - 365 * DAY, "keep", audio))

        cleanup.run(now)

        assertTrue(audio.exists())
        assertNotNull(repository.get(id))
    }

    private fun audioFile(name: String): File {
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        return File(dir, name).apply { writeText("fake audio") }
    }

    private fun entry(
        createdAt: Long,
        hash: String,
        audio: File?,
        status: TranscriptionStatus = TranscriptionStatus.DONE,
    ) = HistoryEntry(
        createdAt = createdAt,
        sourceFileName = "PTT.opus",
        durationMs = 1000,
        audioPath = audio?.absolutePath,
        audioHash = hash,
        transcript = "Text",
        language = "de",
        modelUsed = "small",
        status = status,
    )

    private class FakeSettingsRepository : SettingsRepository {
        val state = MutableStateFlow(AppSettings())
        var value: AppSettings
            get() = state.value
            set(v) {
                state.value = v
            }

        override val settings: StateFlow<AppSettings> = state
        override suspend fun current(): AppSettings = state.value
        override suspend fun setTheme(theme: ThemeMode) = Unit
        override suspend fun setModel(model: WhisperModel) = Unit
        override suspend fun setLanguage(language: TranscriptionLanguage) = Unit
        override suspend fun setAudioRetention(retention: AudioRetention) = Unit
        override suspend fun setTranscriptRetention(retention: TranscriptRetention) = Unit
        override suspend fun setAppLock(enabled: Boolean) = Unit
        override suspend fun setSecureScreen(enabled: Boolean) = Unit
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
