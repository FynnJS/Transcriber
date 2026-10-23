package dev.fynnjs.transcriber.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus

@Composable
fun StatusChip(
    status: TranscriptionStatus,
    noText: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (label, container, content) = statusColors(status, noText)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(content, CircleShape),
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun statusColors(
    status: TranscriptionStatus,
    noText: Boolean,
): Triple<String, Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when {
        status == TranscriptionStatus.DONE && noText ->
            Triple(
                stringResource(R.string.status_no_text),
                scheme.tertiaryContainer,
                scheme.onTertiaryContainer,
            )

        status == TranscriptionStatus.DONE ->
            Triple(
                stringResource(R.string.status_done),
                scheme.primaryContainer,
                scheme.onPrimaryContainer,
            )

        status == TranscriptionStatus.TRANSCRIBING ->
            Triple(
                stringResource(R.string.status_transcribing),
                scheme.tertiaryContainer,
                scheme.onTertiaryContainer,
            )

        status == TranscriptionStatus.FAILED ->
            Triple(
                stringResource(R.string.status_failed),
                scheme.errorContainer,
                scheme.onErrorContainer,
            )

        else ->
            Triple(
                stringResource(R.string.status_queued),
                scheme.surfaceVariant,
                scheme.onSurfaceVariant,
            )
    }
}

@Composable
fun LabelChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Label,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            if (onRemove != null) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.detail_label_remove),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onRemove),
                )
            }
        }
    }
}
