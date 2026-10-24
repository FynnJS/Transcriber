package dev.fynnjs.transcriber.ui.screens.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.AppUpdate
import dev.fynnjs.transcriber.domain.model.UpdateError
import dev.fynnjs.transcriber.domain.model.UpdateState
import dev.fynnjs.transcriber.ui.util.formatSizeMb

/** One dialog for every update step, so the state flow stays the single source of truth. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    installedVersion: String,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onRetryCheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onSkip,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { AvailableText(state.update, installedVersion) },
            confirmButton = {
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.update_later))
                }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading_title)) },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.update_downloading, state.percent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
        )

        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_ready_title)) },
            text = {
                Text(stringResource(R.string.update_ready_text, state.update.versionName))
            },
            confirmButton = {
                TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.update_install))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
            },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_failed_title)) },
            text = { Text(stringResource(failureText(state.error, state.update))) },
            confirmButton = {
                TextButton(onClick = if (state.update != null) onDownload else onRetryCheck) {
                    Text(
                        stringResource(
                            if (state.update != null) {
                                R.string.update_retry_download
                            } else {
                                R.string.update_retry_check
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_close))
                }
            },
        )

        UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> Unit
    }
}

@Composable
private fun AvailableText(update: AppUpdate, installedVersion: String) {
    Column {
        Text(stringResource(R.string.update_available_text, update.versionName, installedVersion))
        if (update.sizeBytes > 0) {
            Text(
                text = stringResource(R.string.update_size, formatSizeMb(update.sizeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (update.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.update_notes_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = update.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun failureText(error: UpdateError, update: AppUpdate?): Int = when (error) {
    UpdateError.NETWORK ->
        if (update == null) R.string.update_failed_check else R.string.update_failed_network

    UpdateError.MISSING_APK -> R.string.update_failed_missing_apk
    UpdateError.INVALID_APK -> R.string.update_failed_invalid_apk
}
