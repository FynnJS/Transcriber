package dev.fynnjs.transcriber.ui.screens.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import dev.fynnjs.transcriber.data.audio.AudioFileStore
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Choosing a model for one entry, including the download of a missing model. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DetailModelSelectionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private val models = FakeModelRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a downloaded model is stored for the entry without a download`() = runTest {
        models.mark(WhisperModel.BASE, ModelState.Ready(File(context.filesDir, "base.bin")))
        val repository = FakeHistoryRepository(entry())

        viewModel(repository).selectModel(WhisperModel.BASE)

        assertEquals(WhisperModel.BASE.id, repository.modelUsed)
        assertTrue(models.downloads.isEmpty())
        assertTrue(repository.statuses.isEmpty())
    }

    @Test
    fun `a missing model is downloaded for the entry`() = runTest {
        val repository = FakeHistoryRepository(entry())

        viewModel(repository).selectModel(WhisperModel.BASE)

        assertEquals(WhisperModel.BASE.id, repository.modelUsed)
        assertEquals(listOf(WhisperModel.BASE), models.downloads)
        assertTrue("transcription started before the download finished", repository.statuses.isEmpty())
    }

    @Test
    fun `the transcription starts on its own once the model is ready`() = runTest {
        val repository = FakeHistoryRepository(entry())

        viewModel(repository).selectModel(WhisperModel.BASE)
        models.mark(WhisperModel.BASE, ModelState.Ready(File(context.filesDir, "base.bin")))

        assertEquals(listOf(TranscriptionStatus.QUEUED), repository.statuses)
    }

    @Test
    fun `a finished entry is not transcribed again after the download`() = runTest {
        val repository = FakeHistoryRepository(entry(status = TranscriptionStatus.DONE))

        viewModel(repository).selectModel(WhisperModel.BASE)
        models.mark(WhisperModel.BASE, ModelState.Ready(File(context.filesDir, "base.bin")))

        assertTrue(repository.statuses.isEmpty())
    }

    private fun viewModel(repository: FakeHistoryRepository) = DetailViewModel(
        historyRepository = repository,
        modelRepository = models,
        audioFileStore = AudioFileStore(context),
        context = context,
        savedStateHandle = SavedStateHandle(mapOf(DetailViewModel.DETAIL_ID_ARG to ENTRY_ID)),
    )

    private fun entry(status: TranscriptionStatus = TranscriptionStatus.FAILED) = HistoryEntry(
        id = ENTRY_ID,
        createdAt = 0L,
        sourceFileName = "PTT.opus",
        durationMs = 1000L,
        audioPath = null,
        audioHash = "hash",
        transcript = null,
        language = "de",
        modelUsed = WhisperModel.SMALL.id,
        status = status,
    )

    private class FakeHistoryRepository(entry: HistoryEntry) : HistoryRepository {
        private val state = MutableStateFlow(entry)
        var modelUsed: String? = null
        val statuses = mutableListOf<TranscriptionStatus>()

        override val entries: Flow<List<HistoryEntry>> = flowOf(emptyList())

        override fun observeEntry(id: Long): Flow<HistoryEntry?> = state

        override fun search(query: String): Flow<List<HistoryEntry>> = flowOf(emptyList())

        override suspend fun get(id: Long): HistoryEntry? = state.value

        override suspend fun findByHash(hash: String): HistoryEntry? = null

        override suspend fun insert(entry: HistoryEntry): Long = entry.id

        override suspend fun updateStatus(
            id: Long,
            status: TranscriptionStatus,
            errorMessage: String?,
        ) {
            statuses += status
            state.value = state.value.copy(status = status)
        }

        override suspend fun updateProgress(id: Long, progress: Int) = Unit

        override suspend fun updatePartialTranscript(id: Long, text: String?) = Unit

        override suspend fun updateModelUsed(id: Long, model: WhisperModel) {
            modelUsed = model.id
            state.value = state.value.copy(modelUsed = model.id)
        }

        override suspend fun complete(
            id: Long,
            transcript: String?,
            language: String,
            model: WhisperModel,
            durationMs: Long,
        ) = Unit

        override suspend fun updateLabel(id: Long, label: String?) = Unit

        override suspend fun updateAudioPath(id: Long, audioPath: String?) = Unit

        override suspend fun nextQueued(): HistoryEntry? = null

        override suspend fun resetStuckEntries(): Int = 0

        override suspend fun recentLabels(limit: Int): List<String> = emptyList()

        override suspend fun delete(id: Long) = Unit

        override suspend fun deleteAll() = Unit

        override suspend fun expireAudio(cutoff: Long): Int = 0

        override suspend fun clearAllAudio(): Int = 0

        override suspend fun clearAudioOfCompleted(): Int = 0

        override suspend fun deleteOlderThan(cutoff: Long): Int = 0
    }

    private class FakeModelRepository : ModelRepository {
        private val state = MutableStateFlow<Map<WhisperModel, ModelState>>(
            WhisperModel.entries.associateWith { ModelState.NotDownloaded },
        )
        val downloads = mutableListOf<WhisperModel>()

        override val states: StateFlow<Map<WhisperModel, ModelState>> = state

        fun mark(model: WhisperModel, modelState: ModelState) {
            state.value = state.value + (model to modelState)
        }

        override fun state(model: WhisperModel): ModelState = state.value.getValue(model)

        override fun isReady(model: WhisperModel): Boolean =
            state.value[model]?.isReady ?: false

        override fun modelFile(model: WhisperModel): File = File(model.fileName)

        override suspend fun refresh() = Unit

        override fun download(model: WhisperModel) {
            downloads += model
        }

        override fun delete(model: WhisperModel) = Unit
    }

    private companion object {
        const val ENTRY_ID = 1L
    }
}
