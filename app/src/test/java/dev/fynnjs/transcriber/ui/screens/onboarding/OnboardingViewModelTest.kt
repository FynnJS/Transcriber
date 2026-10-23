package dev.fynnjs.transcriber.ui.screens.onboarding

import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun downloadWithBaseRequestsDefaultAndBaseModel() = runTest(dispatcher) {
        val repository = RecordingModelRepository()
        val viewModel = OnboardingViewModel(repository)

        viewModel.download(includeBase = true)

        assertEquals(listOf(WhisperModel.SMALL, WhisperModel.BASE), repository.downloads)
    }

    @Test
    fun downloadWithoutBaseRequestsOnlyDefaultModel() = runTest(dispatcher) {
        val repository = RecordingModelRepository()
        val viewModel = OnboardingViewModel(repository)

        viewModel.download(includeBase = false)

        assertEquals(listOf(WhisperModel.SMALL), repository.downloads)
    }

    private class RecordingModelRepository : ModelRepository {

        private val modelStates = MutableStateFlow(
            WhisperModel.entries.associateWith { ModelState.NotDownloaded },
        )

        val downloads = mutableListOf<WhisperModel>()

        override val states: StateFlow<Map<WhisperModel, ModelState>> = modelStates.asStateFlow()

        override fun state(model: WhisperModel): ModelState = modelStates.value.getValue(model)

        override fun isReady(model: WhisperModel): Boolean = state(model).isReady

        override fun modelFile(model: WhisperModel): File = File(model.fileName)

        override suspend fun refresh() = Unit

        override fun download(model: WhisperModel) {
            downloads += model
        }

        override fun delete(model: WhisperModel) = Unit
    }
}
