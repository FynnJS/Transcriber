package dev.fynnjs.transcriber.ui.screens.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fynnjs.transcriber.data.audio.AudioFileStore
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import dev.fynnjs.transcriber.service.TranscriptionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val _recentLabels = MutableStateFlow<List<String>>(emptyList())
    val recentLabels: StateFlow<List<String>> = _recentLabels

    init {
        refreshLabels()
    }

    fun setLabel(label: String?) {
        viewModelScope.launch {
            historyRepository.updateLabel(entryId, label)
            refreshLabels()
        }
    }

    fun retry() {
        viewModelScope.launch {
            val current = entry.value ?: return@launch
            val model = WhisperModel.fromId(current.modelUsed)
            if (!modelRepository.isReady(model)) return@launch
            historyRepository.updateStatus(current.id, TranscriptionStatus.QUEUED)
            TranscriptionService.enqueue(context, current.id)
        }
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
