package dev.fynnjs.transcriber.ui.screens.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fynnjs.transcriber.data.audio.AudioFileStore
import dev.fynnjs.transcriber.data.local.TranscriberDatabase
import dev.fynnjs.transcriber.data.repository.HistoryRepositoryImpl
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Keeps the retry feedback of the detail screen honest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DetailRetryTest {

    private lateinit var database: TranscriberDatabase
    private lateinit var repository: HistoryRepositoryImpl
    private lateinit var context: Context
    private val models = FakeModelRepository()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, TranscriberDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HistoryRepositoryImpl(database.historyDao(), AudioFileStore(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun viewModel() = DetailViewModel(
        historyRepository = repository,
        modelRepository = models,
        audioFileStore = AudioFileStore(context),
        context = context,
        savedStateHandle = SavedStateHandle(mapOf(DetailViewModel.DETAIL_ID_ARG to 1L)),
    )

    private fun failedEntry() = HistoryEntry(
        id = 1L,
        createdAt = 0L,
        sourceFileName = "PTT.opus",
        durationMs = 1000L,
        audioPath = null,
        audioHash = "hash",
        transcript = null,
        language = "de",
        modelUsed = WhisperModel.SMALL.id,
        status = TranscriptionStatus.FAILED,
        errorMessage = null,
    )

    @Test
    fun `retry without a downloaded model reports that nothing started`() {
        assertFalse(viewModel().retry(failedEntry()))
    }

    @Test
    fun `retry while the model is downloading reports that nothing started`() {
        models.mark(WhisperModel.SMALL, ModelState.Downloading(progress = 40))
        assertFalse(viewModel().retry(failedEntry()))
    }

    @Test
    fun `retry with a ready model starts the transcription`() {
        models.mark(WhisperModel.SMALL, ModelState.Ready(File(context.filesDir, "model.bin")))
        assertTrue(viewModel().retry(failedEntry()))
    }

    private class FakeModelRepository : ModelRepository {
        private val state = MutableStateFlow<Map<WhisperModel, ModelState>>(
            WhisperModel.entries.associateWith { ModelState.NotDownloaded },
        )

        override val states: StateFlow<Map<WhisperModel, ModelState>> = state

        fun mark(model: WhisperModel, modelState: ModelState) {
            state.value = state.value + (model to modelState)
        }

        override fun state(model: WhisperModel): ModelState =
            state.value.getValue(model)

        override fun isReady(model: WhisperModel): Boolean =
            state.value[model]?.isReady ?: false

        override fun modelFile(model: WhisperModel): File = File(model.fileName)

        override suspend fun refresh() = Unit

        override fun download(model: WhisperModel) = Unit

        override fun delete(model: WhisperModel) = Unit
    }
}
