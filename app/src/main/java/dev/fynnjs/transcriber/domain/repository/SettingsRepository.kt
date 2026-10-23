package dev.fynnjs.transcriber.domain.repository

import dev.fynnjs.transcriber.domain.model.AppSettings
import dev.fynnjs.transcriber.domain.model.AudioRetention
import dev.fynnjs.transcriber.domain.model.ThemeMode
import dev.fynnjs.transcriber.domain.model.TranscriptionLanguage
import dev.fynnjs.transcriber.domain.model.TranscriptRetention
import dev.fynnjs.transcriber.domain.model.WhisperModel
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** One-shot read, also used synchronously for the first theme frame. */
    suspend fun current(): AppSettings

    suspend fun setTheme(theme: ThemeMode)

    suspend fun setModel(model: WhisperModel)

    suspend fun setLanguage(language: TranscriptionLanguage)

    suspend fun setAudioRetention(retention: AudioRetention)

    suspend fun setTranscriptRetention(retention: TranscriptRetention)

    suspend fun setAppLock(enabled: Boolean)

    suspend fun setSecureScreen(enabled: Boolean)
}
