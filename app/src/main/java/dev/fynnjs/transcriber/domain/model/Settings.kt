package dev.fynnjs.transcriber.domain.model

enum class ThemeMode { DARK, LIGHT }

/** Whisper language selection. */
enum class TranscriptionLanguage(val code: String) {
    AUTO("auto"),
    GERMAN("de"),
    ;

    companion object {
        fun fromCode(code: String?): TranscriptionLanguage =
            entries.firstOrNull { it.code == code } ?: AUTO
    }
}

/** How long audio files are kept. Transcripts have their own retention. */
enum class AudioRetention(val days: Int?) {
    IMMEDIATELY(null),
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90),
    NEVER(null),
    ;

    companion object {
        fun fromName(name: String?): AudioRetention =
            entries.firstOrNull { it.name == name } ?: DAYS_30
    }
}

enum class TranscriptRetention(val days: Int?) {
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90),
    NEVER(null),
    ;

    companion object {
        fun fromName(name: String?): TranscriptRetention =
            entries.firstOrNull { it.name == name } ?: NEVER
    }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val model: WhisperModel = WhisperModel.SMALL,
    val language: TranscriptionLanguage = TranscriptionLanguage.AUTO,
    val audioRetention: AudioRetention = AudioRetention.DAYS_30,
    val transcriptRetention: TranscriptRetention = TranscriptRetention.NEVER,
    val appLock: Boolean = false,
    val secureScreen: Boolean = false,
)
