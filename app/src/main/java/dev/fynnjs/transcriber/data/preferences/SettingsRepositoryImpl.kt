package dev.fynnjs.transcriber.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.fynnjs.transcriber.domain.model.AppSettings
import dev.fynnjs.transcriber.domain.model.AudioRetention
import dev.fynnjs.transcriber.domain.model.ThemeMode
import dev.fynnjs.transcriber.domain.model.TranscriptionLanguage
import dev.fynnjs.transcriber.domain.model.TranscriptRetention
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private val store: DataStore<Preferences> get() = context.settingsDataStore

    override val settings: Flow<AppSettings> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                theme = runCatching { ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.DARK.name) }
                    .getOrDefault(ThemeMode.DARK),
                model = WhisperModel.fromId(prefs[Keys.MODEL]),
                language = TranscriptionLanguage.fromCode(prefs[Keys.LANGUAGE] ?: TranscriptionLanguage.AUTO.code),
                audioRetention = AudioRetention.fromName(prefs[Keys.AUDIO_RETENTION]),
                transcriptRetention = TranscriptRetention.fromName(prefs[Keys.TRANSCRIPT_RETENTION]),
                appLock = prefs[Keys.APP_LOCK] ?: false,
                secureScreen = prefs[Keys.SECURE_SCREEN] ?: false,
            )
        }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setTheme(theme: ThemeMode) = edit { it[Keys.THEME] = theme.name }

    override suspend fun setModel(model: WhisperModel) = edit { it[Keys.MODEL] = model.id }

    override suspend fun setLanguage(language: TranscriptionLanguage) =
        edit { it[Keys.LANGUAGE] = language.code }

    override suspend fun setAudioRetention(retention: AudioRetention) =
        edit { it[Keys.AUDIO_RETENTION] = retention.name }

    override suspend fun setTranscriptRetention(retention: TranscriptRetention) =
        edit { it[Keys.TRANSCRIPT_RETENTION] = retention.name }

    override suspend fun setAppLock(enabled: Boolean) = edit { it[Keys.APP_LOCK] = enabled }

    override suspend fun setSecureScreen(enabled: Boolean) = edit { it[Keys.SECURE_SCREEN] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val MODEL = stringPreferencesKey("model")
        val LANGUAGE = stringPreferencesKey("language")
        val AUDIO_RETENTION = stringPreferencesKey("audio_retention")
        val TRANSCRIPT_RETENTION = stringPreferencesKey("transcript_retention")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
    }
}
