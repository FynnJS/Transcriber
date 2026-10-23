package dev.fynnjs.transcriber.ui.util

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatTime(context: Context, epochMillis: Long): String =
    DateFormat.getTimeFormat(context).format(epochMillis)

fun formatDate(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.GERMAN))
}

fun formatDayHeader(context: Context, epochMillis: Long, todayLabel: String, yesterdayLabel: String): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> todayLabel
        today.minusDays(1) -> yesterdayLabel
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.GERMAN))
    }
}

fun dayKey(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

/** 0:07, 3:42, 12:05 */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatSizeMb(bytes: Long): Int = ((bytes + 1024 * 1024 - 1) / (1024 * 1024)).toInt()

fun languageLabel(code: String): String = when (code.lowercase(Locale.ROOT)) {
    "de" -> "Deutsch"
    "en" -> "Englisch"
    "auto" -> "Automatisch"
    "unknown" -> "Unbekannt"
    else -> code.uppercase(Locale.ROOT)
}
