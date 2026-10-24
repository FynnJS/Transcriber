package dev.fynnjs.transcriber.data.model

import androidx.work.WorkInfo
import dev.fynnjs.transcriber.domain.model.ModelError
import dev.fynnjs.transcriber.domain.model.ModelState

/**
 * Maps the WorkManager records of one model to the state shown in the UI.
 *
 * WorkManager keeps the records of replaced downloads. After a failed attempt and a successful
 * retry, the unique work name therefore has several generations and the query returns them in an
 * unspecified order. A finished file on disk always wins so an old failure cannot hide a model
 * that is ready, and a single old failure cannot override a later success.
 *
 * Returns `null` while WorkManager has no record yet so an optimistic "downloading" state is kept.
 */
internal fun resolveModelState(
    infos: List<WorkInfo>,
    onDisk: ModelState,
): ModelState? {
    if (infos.isEmpty()) return null
    if (onDisk is ModelState.Ready) return onDisk

    val active = infos.firstOrNull { it.state in ACTIVE_STATES }
    if (active != null) {
        val progress = active.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
        return ModelState.Downloading(progress)
    }

    val failed = infos.firstOrNull { it.state == WorkInfo.State.FAILED }
    val succeededBefore = infos.any { it.state == WorkInfo.State.SUCCEEDED }
    if (failed != null && !succeededBefore) {
        val error = runCatching {
            ModelError.valueOf(failed.outputData.getString(ModelDownloadWorker.KEY_ERROR).orEmpty())
        }.getOrDefault(ModelError.DOWNLOAD_FAILED)
        return ModelState.Failed(error)
    }

    return onDisk
}

private val ACTIVE_STATES = setOf(
    WorkInfo.State.ENQUEUED,
    WorkInfo.State.BLOCKED,
    WorkInfo.State.RUNNING,
)
