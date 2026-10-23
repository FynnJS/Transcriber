package dev.fynnjs.transcriber.ui.screens.settings

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fynnjs.transcriber.data.worker.CleanupScheduler
import dev.fynnjs.transcriber.domain.model.AppSettings
import dev.fynnjs.transcriber.domain.model.AudioRetention
import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.ThemeMode
import dev.fynnjs.transcriber.domain.model.TranscriptionLanguage
import dev.fynnjs.transcriber.domain.model.TranscriptRetention
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import dev.fynnjs.transcriber.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository,
    private val historyRepository: HistoryRepository,
    private val cleanupScheduler: CleanupScheduler,
    @ApplicationContext context: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val modelStates: StateFlow<Map<WhisperModel, ModelState>> = modelRepository.states

    val notificationsEnabled = MutableStateFlow(
        NotificationManagerCompat.from(context).areNotificationsEnabled(),
    )

    fun setTheme(theme: ThemeMode) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setModel(model: WhisperModel) {
        viewModelScope.launch { settingsRepository.setModel(model) }
    }

    fun setLanguage(language: TranscriptionLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun setAudioRetention(retention: AudioRetention) {
        viewModelScope.launch {
            settingsRepository.setAudioRetention(retention)
            cleanupScheduler.runNow()
        }
    }

    fun setTranscriptRetention(retention: TranscriptRetention) {
        viewModelScope.launch {
            settingsRepository.setTranscriptRetention(retention)
            cleanupScheduler.runNow()
        }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLock(enabled) }
    }

    fun setSecureScreen(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSecureScreen(enabled) }
    }

    fun download(model: WhisperModel) {
        modelRepository.download(model)
    }

    fun deleteModel(model: WhisperModel) {
        modelRepository.delete(model)
    }

    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            historyRepository.deleteAll()
            onDone()
        }
    }
}
