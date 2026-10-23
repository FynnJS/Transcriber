package dev.fynnjs.transcriber.ui.screens.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fynnjs.transcriber.data.audio.AudioFileStore
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import dev.fynnjs.transcriber.service.TranscriptionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val modelRepository: ModelRepository,
    private val audioFileStore: AudioFileStore,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val entryId: Long = savedStateHandle.get<Long>(DETAIL_ID_ARG) ?: 0L

    val entry: StateFlow<HistoryEntry?> = historyRepository
        .observeEntry(entryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val audioFile: StateFlow<File?> = historyRepository
        .observeEntry(entryId)
        .map { audioFileStore.resolve(it?.audioPath) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val modelStates: StateFlow<Map<WhisperModel, ModelState>> = modelRepository.states

    private val _recentLabels = MutableStateFlow<List<String>>(emptyList())
    val recentLabels: StateFlow<List<String>> = _recentLabels

    private var autoStartJob: Job? = null

    init {
        refreshLabels()
    }

    fun setLabel(label: String?) {
        viewModelScope.launch {
            historyRepository.updateLabel(entryId, label)
            refreshLabels()
        }
    }

    /**
     * Restarts the transcription of [entry].
     *
     * Returns false when the model is not downloaded yet; nothing is queued in that case, so
     * the caller must not report a started transcription.
     */
    fun retry(entry: HistoryEntry): Boolean {
        val model = WhisperModel.fromId(entry.modelUsed)
        if (!modelRepository.isReady(model)) return false
        viewModelScope.launch { startTranscription(entry.id) }
        return true
    }

    /**
     * Uses [model] for the next transcription of this entry.
     *
     * A model that is not on disk yet is downloaded right away; once it is ready the
     * transcription starts on its own, so the user does not have to trigger it again.
     */
    fun selectModel(model: WhisperModel) {
        viewModelScope.launch {
            historyRepository.updateModelUsed(entryId, model)
            autoStartJob?.cancel()
            if (modelRepository.isReady(model)) return@launch
            autoStartJob = launch {
                modelRepository.download(model)
                modelRepository.states.first { states -> states[model]?.isReady == true }
                if (historyRepository.get(entryId)?.status == TranscriptionStatus.FAILED) {
                    startTranscription(entryId)
                }
            }
        }
    }

    private suspend fun startTranscription(id: Long) {
        historyRepository.updateStatus(id, TranscriptionStatus.QUEUED)
        TranscriptionService.enqueue(context, id)
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            historyRepository.delete(entryId)
            onDeleted()
        }
    }

    private fun refreshLabels() {
        viewModelScope.launch {
            _recentLabels.value = historyRepository.recentLabels()
        }
    }

    companion object {
        const val DETAIL_ID_ARG = "entryId"
    }
}
