package dev.fynnjs.transcriber

import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MainActivityRouteTest {

    @Test
    fun startsOnOnboardingWithoutAnyModel() {
        assertEquals(Routes.ONBOARDING, initialRoute(FakeModelRepository(ready = emptySet())))
    }

    @Test
    fun startsOnHistoryWithTheDefaultModel() {
        assertEquals(Routes.HISTORY, initialRoute(FakeModelRepository(ready = setOf(WhisperModel.SMALL))))
    }

    @Test
    fun startsOnHistoryWhenOnlyADifferentModelIsReady() {
        assertEquals(Routes.HISTORY, initialRoute(FakeModelRepository(ready = setOf(WhisperModel.BASE))))
    }

    private class FakeModelRepository(private val ready: Set<WhisperModel>) : ModelRepository {

        private val modelStates = MutableStateFlow(
            WhisperModel.entries.associateWith { model ->
                if (model in ready) ModelState.Ready(File(model.fileName)) else ModelState.NotDownloaded
            },
        )

        override val states: StateFlow<Map<WhisperModel, ModelState>> = modelStates.asStateFlow()

        override fun state(model: WhisperModel): ModelState = modelStates.value.getValue(model)

        override fun isReady(model: WhisperModel): Boolean = model in ready

        override fun modelFile(model: WhisperModel): File = File(model.fileName)

        override suspend fun refresh() = Unit

        override fun download(model: WhisperModel) = Unit

        override fun delete(model: WhisperModel) = Unit
    }
}
