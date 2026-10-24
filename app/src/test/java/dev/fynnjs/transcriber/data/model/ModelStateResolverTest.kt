package dev.fynnjs.transcriber.data.model

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import dev.fynnjs.transcriber.domain.model.ModelError
import dev.fynnjs.transcriber.domain.model.ModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.UUID

class ModelStateResolverTest {

    private val ready = ModelState.Ready(File("ggml-small-q5_1.bin"))

    @Test
    fun noWorkRecordKeepsTheCurrentState() {
        assertNull(resolveModelState(emptyList(), ModelState.NotDownloaded))
        assertNull(resolveModelState(emptyList(), ModelState.Downloading(0)))
    }

    @Test
    fun staleFailureDoesNotHideAReadyModel() {
        assertEquals(ready, resolveModelState(listOf(work(WorkInfo.State.FAILED, error = ModelError.NO_SPACE)), ready))
    }

    @Test
    fun staleFailureDoesNotHideAModelDownloadedByALaterRetry() {
        val infos = listOf(
            work(WorkInfo.State.FAILED, error = ModelError.NO_SPACE),
            work(WorkInfo.State.SUCCEEDED),
        )

        assertEquals(ModelState.NotDownloaded, resolveModelState(infos, ModelState.NotDownloaded))
    }

    @Test
    fun singleFailureIsReportedWhenNoFileExists() {
        assertEquals(
            ModelState.Failed(ModelError.NO_SPACE),
            resolveModelState(listOf(work(WorkInfo.State.FAILED, error = ModelError.NO_SPACE)), ModelState.NotDownloaded),
        )
    }

    @Test
    fun missingErrorPayloadFallsBackToDownloadFailed() {
        assertEquals(
            ModelState.Failed(ModelError.DOWNLOAD_FAILED),
            resolveModelState(listOf(work(WorkInfo.State.FAILED)), ModelState.NotDownloaded),
        )
    }

    @Test
    fun activeDownloadWinsOverOldFailures() {
        val infos = listOf(
            work(WorkInfo.State.FAILED, error = ModelError.NO_SPACE),
            work(WorkInfo.State.ENQUEUED, progress = 42),
        )

        assertEquals(ModelState.Downloading(42), resolveModelState(infos, ModelState.NotDownloaded))
    }

    @Test
    fun runningDownloadReportsProgress() {
        assertEquals(
            ModelState.Downloading(7),
            resolveModelState(listOf(work(WorkInfo.State.RUNNING, progress = 7)), ModelState.NotDownloaded),
        )
    }

    @Test
    fun cancelledDownloadFallsBackToDisk() {
        val corrupt = ModelState.Failed(ModelError.CHECKSUM_MISMATCH)

        assertEquals(corrupt, resolveModelState(listOf(work(WorkInfo.State.CANCELLED)), corrupt))
    }

    @Test
    fun succeededRecordWithoutFileFallsBackToDisk() {
        assertEquals(
            ModelState.NotDownloaded,
            resolveModelState(listOf(work(WorkInfo.State.SUCCEEDED)), ModelState.NotDownloaded),
        )
    }

    private fun work(
        state: WorkInfo.State,
        progress: Int = 0,
        error: ModelError? = null,
    ): WorkInfo = WorkInfo(
        id = UUID.randomUUID(),
        state = state,
        tags = emptySet(),
        outputData = error?.let {
            workDataOf(ModelDownloadWorker.KEY_ERROR to it.name)
        } ?: Data.EMPTY,
        progress = workDataOf(ModelDownloadWorker.KEY_PROGRESS to progress),
    )
}
