package dev.fynnjs.transcriber.data.model

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.fynnjs.transcriber.domain.model.ModelError
import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val workManager: WorkManager,
) : ModelRepository {

    private val storage = ModelStorage(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _states = MutableStateFlow(scanDisk())

    override val states: StateFlow<Map<WhisperModel, ModelState>> = _states.asStateFlow()

    init {
        WhisperModel.entries.forEach { model -> observeWork(model) }
    }

    override fun state(model: WhisperModel): ModelState =
        _states.value[model] ?: ModelState.NotDownloaded

    // The file on disk is the only reliable source: WorkManager keeps records of replaced
    // downloads, so a cached failure must never make a present model look unavailable (the
    // start destination would otherwise fall back to onboarding).
    override fun isReady(model: WhisperModel): Boolean = storage.isPresent(model)

    override fun modelFile(model: WhisperModel): File = storage.file(model)

    override suspend fun refresh() {
        _states.value = scanDisk()
    }

    override fun download(model: WhisperModel) {
        if (isReady(model)) return
        if (state(model) is ModelState.Downloading) return

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL to model.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        _states.update { it + (model to ModelState.Downloading(0)) }
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.workName(model),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun delete(model: WhisperModel) {
        workManager.cancelUniqueWork(ModelDownloadWorker.workName(model))
        storage.delete(model)
        _states.update { it + (model to ModelState.NotDownloaded) }
    }

    private fun observeWork(model: WhisperModel) {
        scope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.workName(model))
                .collect { infos ->
                    val state = resolveModelState(infos, diskState(model)) ?: return@collect
                    setState(model, state)
                }
        }
    }

    private fun setState(model: WhisperModel, state: ModelState) {
        _states.update { it + (model to state) }
    }

    private fun scanDisk(): Map<WhisperModel, ModelState> =
        WhisperModel.entries.associateWith { diskState(it) }

    private fun diskState(model: WhisperModel): ModelState {
        val file = storage.file(model)
        if (!file.isFile) return ModelState.NotDownloaded
        if (file.length() != model.approxBytes) return ModelState.Failed(ModelError.CHECKSUM_MISMATCH)
        return ModelState.Ready(file)
    }

    private companion object {
        const val TAG = "model-download"
    }
}
