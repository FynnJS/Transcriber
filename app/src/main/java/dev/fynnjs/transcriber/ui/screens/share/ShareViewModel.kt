package dev.fynnjs.transcriber.ui.screens.share

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.data.audio.AudioFileStore
import dev.fynnjs.transcriber.data.audio.AudioImportError
import dev.fynnjs.transcriber.data.audio.AudioImportException
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import dev.fynnjs.transcriber.domain.repository.SettingsRepository
import dev.fynnjs.transcriber.service.TranscriptionService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShareUiState {
    data object Preparing : ShareUiState

    data class Ready(
        val entry: HistoryEntry,
        val duplicate: Boolean,
        val modelMissing: Boolean,
    ) : ShareUiState

    data class Failed(val error: ShareError) : ShareUiState
}

enum class ShareError {
    NO_AUDIO,
    UNREADABLE,
    NO_SPACE,
    TOO_LARGE,
    UNKNOWN,
    MISSING,
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val audioFileStore: AudioFileStore,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareUiState>(ShareUiState.Preparing)
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val duplicate = MutableStateFlow(false)
    private var started = false
    private var observeJob: Job? = null

    private val entryId: Long
        get() = savedStateHandle.get<Long>(KEY_ENTRY_ID) ?: 0L

    /** Called once from the activity; survives rotation and restores after process death. */
    fun start(uri: Uri?) {
        if (started) return
        started = true
        val existing = entryId
        if (existing > 0L) {
            observe(existing)
            return
        }
        if (uri == null) {
            _uiState.value = ShareUiState.Failed(ShareError.NO_AUDIO)
            return
        }
        importAudio(uri)
    }

    private val _recentLabels = MutableStateFlow<List<String>>(emptyList())
    val recentLabels: StateFlow<List<String>> = _recentLabels.asStateFlow()

    fun saveLabel(label: String?) {
        viewModelScope.launch {
            historyRepository.updateLabel(entryId, label)
            _recentLabels.value = historyRepository.recentLabels()
        }
    }

    fun retry() {
        viewModelScope.launch {
            val id = entryId
            val entry = historyRepository.get(id) ?: return@launch
            val model = WhisperModel.fromId(entry.modelUsed)
            if (!modelRepository.isReady(model)) return@launch
            duplicate.value = false
            historyRepository.updateStatus(id, TranscriptionStatus.QUEUED)
            TranscriptionService.enqueue(context, id)
        }
    }

    private fun importAudio(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ShareUiState.Preparing
            var importedPath: String? = null
            try {
                val imported = audioFileStore.import(uri)
                importedPath = imported.file.absolutePath

                val existing = historyRepository.findByHash(imported.sha256)
                if (existing != null) {
                    audioFileStore.delete(imported.file.absolutePath)
                    duplicate.value = true
                    savedStateHandle[KEY_ENTRY_ID] = existing.id
                    observe(existing.id)
                    return@launch
                }

                val settings = settingsRepository.current()
                val id = historyRepository.insert(
                    HistoryEntry(
                        createdAt = System.currentTimeMillis(),
                        sourceFileName = imported.sourceFileName,
                        durationMs = 0L,
                        audioPath = imported.file.absolutePath,
                        audioHash = imported.sha256,
                        transcript = null,
                        language = settings.language.code,
                        modelUsed = settings.model.id,
                        status = TranscriptionStatus.QUEUED,
                    ),
                )
                savedStateHandle[KEY_ENTRY_ID] = id
                observe(id)

                if (modelRepository.isReady(settings.model)) {
                    TranscriptionService.enqueue(context, id)
                } else {
                    historyRepository.updateStatus(
                        id,
                        TranscriptionStatus.FAILED,
                        context.getString(R.string.error_model_missing),
                    )
                }
            } catch (e: AudioImportException) {
                importedPath?.let { audioFileStore.delete(it) }
                _uiState.value = ShareUiState.Failed(e.error.toShareError())
            } catch (e: Exception) {
                importedPath?.let { audioFileStore.delete(it) }
                _uiState.value = ShareUiState.Failed(ShareError.UNKNOWN)
            }
        }
    }

    private fun observe(id: Long) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _recentLabels.value = historyRepository.recentLabels()
            combine(
                historyRepository.observeEntry(id),
                modelRepository.states,
                duplicate,
            ) { entry, states, isDuplicate ->
                if (entry == null) {
                    // The entry was deleted while the overlay was open (or restored after
                    // it disappeared); stop spinning instead of waiting forever.
                    ShareUiState.Failed(ShareError.MISSING)
                } else {
                    val model = WhisperModel.fromId(entry.modelUsed)
                    ShareUiState.Ready(
                        entry = entry,
                        duplicate = isDuplicate,
                        modelMissing = !(states[model]?.isReady ?: false),
                    )
                }
            }.collect { _uiState.value = it }
        }
    }

    private fun AudioImportError.toShareError(): ShareError = when (this) {
        AudioImportError.NO_AUDIO -> ShareError.NO_AUDIO
        AudioImportError.UNREADABLE -> ShareError.UNREADABLE
        AudioImportError.NOT_ENOUGH_SPACE -> ShareError.NO_SPACE
        AudioImportError.TOO_LARGE -> ShareError.TOO_LARGE
    }

    companion object {
        private const val KEY_ENTRY_ID = "shareEntryId"
    }
}
