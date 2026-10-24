package dev.fynnjs.transcriber.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import dev.fynnjs.transcriber.ui.util.formatDuration

@Composable
fun HistoryCard(
    entry: HistoryEntry,
    timeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownTranscript = entry.displayedTranscript
    val preview = when {
        !shownTranscript.isNullOrBlank() -> shownTranscript
        entry.status == TranscriptionStatus.FAILED ->
            entry.errorMessage ?: stringResource(R.string.error_generic)

        entry.status == TranscriptionStatus.TRANSCRIBING ->
            stringResource(R.string.status_transcribing)

        entry.status == TranscriptionStatus.QUEUED ->
            stringResource(R.string.status_queued)

        else -> stringResource(R.string.detail_no_text_hint)
    }
    val description = stringResource(
        R.string.history_card_description,
        timeText,
        formatDuration(entry.durationMs),
        preview,
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatDuration(entry.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                StatusChip(
                    status = entry.status,
                    noText = entry.status == TranscriptionStatus.DONE && !entry.hasTranscript,
                )
            }

            if (!entry.label.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                LabelChip(label = entry.label.orEmpty())
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = if (!shownTranscript.isNullOrBlank() ||
                    entry.status == TranscriptionStatus.FAILED
                ) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(name = "Verlaufskarte dunkel", showBackground = true)
@Composable
private fun HistoryCardDarkPreview() {
    TranscriberTheme(darkTheme = true) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            HistoryCard(sampleEntry(), timeText = "14:32", onClick = {})
        }
    }
}

@Preview(name = "Verlaufskarte hell", showBackground = true)
@Composable
private fun HistoryCardLightPreview() {
    TranscriberTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            HistoryCard(sampleEntry(), timeText = "14:32", onClick = {})
        }
    }
}

internal fun sampleEntry(
    status: TranscriptionStatus = TranscriptionStatus.DONE,
    transcript: String? = "Wir treffen uns morgen um zehn am Bahnhof und fahren dann gemeinsam weiter.",
    label: String? = "Treffen",
) = HistoryEntry(
    id = 1,
    createdAt = System.currentTimeMillis(),
    sourceFileName = "PTT-20261023-WA0001.opus",
    durationMs = 42_000,
    audioPath = "/data/user/0/dev.fynnjs.transcriber/files/audio/a.opus",
    audioHash = "abc",
    transcript = transcript,
    language = "de",
    modelUsed = WhisperModel.SMALL.id,
    status = status,
    label = label,
)
