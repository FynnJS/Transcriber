package dev.fynnjs.transcriber.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import androidx.compose.ui.res.stringResource

/** Visual gallery of all reusable components in both themes. */
@Composable
private fun Gallery() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TranscriberTopBar(title = "Transcriber")
        SearchField(value = "", onValueChange = {})
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TranscriptionStatus.entries.forEach { status ->
                StatusChip(
                    status = status,
                    noText = status == TranscriptionStatus.DONE,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LabelChip(label = "Arbeit", onClick = {})
            LabelChip(label = "Familie", onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = stringResource(R.string.action_download),
                onClick = {},
                icon = Icons.Outlined.Download,
            )
            SecondaryButton(text = stringResource(R.string.action_retry), onClick = {})
        }
        HistoryCard(
            entry = sampleEntry(),
            timeText = "14:32",
            onClick = {},
        )
        HistoryCard(
            entry = sampleEntry(
                status = TranscriptionStatus.TRANSCRIBING,
                transcript = null,
                label = null,
            ),
            timeText = "12:05",
            onClick = {},
        )
        ErrorState(
            title = stringResource(R.string.share_error_title),
            message = stringResource(R.string.share_error_unreadable),
            actionLabel = stringResource(R.string.action_retry),
            onAction = {},
        )
        EmptyState(
            icon = Icons.Outlined.History,
            title = stringResource(R.string.history_empty_title),
            message = stringResource(R.string.history_empty_text),
        )
        AudioMissingHint()
        Text(
            text = "Typography · titleMedium / bodyMedium / labelSmall",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(name = "Komponenten dunkel", showBackground = true, widthDp = 380)
@Composable
private fun GalleryDarkPreview() {
    TranscriberTheme(darkTheme = true) { Gallery() }
}

@Preview(name = "Komponenten hell", showBackground = true, widthDp = 380)
@Composable
private fun GalleryLightPreview() {
    TranscriberTheme(darkTheme = false) { Gallery() }
}
