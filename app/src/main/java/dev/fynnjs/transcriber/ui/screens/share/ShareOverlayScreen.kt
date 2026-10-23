package dev.fynnjs.transcriber.ui.screens.share

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.ui.components.AudioPlayer
import dev.fynnjs.transcriber.ui.components.BottomSheetContainer
import dev.fynnjs.transcriber.ui.components.ErrorState
import dev.fynnjs.transcriber.ui.components.HeadingText
import dev.fynnjs.transcriber.ui.components.LabelChip
import dev.fynnjs.transcriber.ui.components.LabelEditorDialog
import dev.fynnjs.transcriber.ui.components.PrimaryButton
import dev.fynnjs.transcriber.ui.components.SecondaryButton
import dev.fynnjs.transcriber.ui.components.StatusChip
import dev.fynnjs.transcriber.ui.components.TranscriptionProgressBar
import dev.fynnjs.transcriber.ui.theme.LocalExtendedColors
import dev.fynnjs.transcriber.ui.util.formatDuration
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ShareOverlayScreen(
    uiState: ShareUiState,
    recentLabels: List<String>,
    onSaveLabel: (String?) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    var showLabelDialog by remember { mutableStateOf(false) }
    val copiedText = stringResource(R.string.copied)
    val shareChooser = stringResource(R.string.detail_share_chooser)

    LaunchedEffect(Unit) { shown = true }

    Box(
        Modifier
            .fillMaxSize()
            .background(LocalExtendedColors.current.overlayScrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = slideInVertically(initialOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomSheetContainer(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            ) {
                // Scrolls when the content exceeds the screen, e.g. on small displays with a
                // large font scale; short content keeps the sheet compact.
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            HeadingText(text = stringResource(R.string.share_title))
                            val subtitle = when (uiState) {
                                is ShareUiState.Ready -> uiState.entry.sourceFileName
                                is ShareUiState.Failed -> stringResource(R.string.share_error_title)
                                ShareUiState.Preparing -> stringResource(R.string.share_preparing)
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_close),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    when (uiState) {
                        ShareUiState.Preparing -> PreparingContent()
                        is ShareUiState.Failed -> FailedContent(
                            error = uiState.error,
                            onClose = onClose,
                            onOpenApp = onOpenApp,
                        )

                        is ShareUiState.Ready -> ReadyContent(
                            state = uiState,
                            onEditLabel = { showLabelDialog = true },
                            onRetry = onRetry,
                            onClose = onClose,
                            onOpenApp = onOpenApp,
                            copiedText = copiedText,
                            shareChooser = shareChooser,
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }

    if (showLabelDialog && uiState is ShareUiState.Ready) {
        LabelEditorDialog(
            initialValue = uiState.entry.label.orEmpty(),
            suggestions = recentLabels,
            onDismiss = { showLabelDialog = false },
            onSave = { label ->
                onSaveLabel(label)
                showLabelDialog = false
            },
        )
    }
}

@Composable
private fun PreparingContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.share_preparing),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FailedContent(
    error: ShareError,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val message = when (error) {
        ShareError.NO_AUDIO -> stringResource(R.string.share_error_no_audio)
        ShareError.UNREADABLE -> stringResource(R.string.share_error_unreadable)
        ShareError.NO_SPACE -> stringResource(R.string.share_error_no_space)
        ShareError.TOO_LARGE -> stringResource(R.string.share_error_too_large)
        ShareError.UNKNOWN -> stringResource(R.string.error_generic)
        ShareError.MISSING -> stringResource(R.string.share_error_missing)
    }
    ErrorState(
        title = stringResource(R.string.share_error_title),
        message = message,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SecondaryButton(text = stringResource(R.string.action_close), onClick = onClose)
        SecondaryButton(
            text = stringResource(R.string.share_open_in_app),
            onClick = onOpenApp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyContent(
    state: ShareUiState.Ready,
    onEditLabel: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    copiedText: String,
    shareChooser: String,
) {
    val entry = state.entry
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copyCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(copyCount) {
        if (copyCount > 0) {
            android.widget.Toast.makeText(context, copiedText, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatDuration(entry.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            StatusChip(
                status = entry.status,
                noText = entry.status == TranscriptionStatus.DONE && !entry.hasTranscript,
            )
        }

        if (state.duplicate) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.share_duplicate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        if (state.modelMissing && entry.status == TranscriptionStatus.FAILED) {
            Spacer(Modifier.height(12.dp))
            ErrorState(
                title = stringResource(R.string.share_model_missing_title),
                message = stringResource(R.string.share_model_missing_text),
                actionLabel = stringResource(R.string.share_model_button),
                onAction = onOpenApp,
            )
        }

        if (entry.status == TranscriptionStatus.QUEUED ||
            entry.status == TranscriptionStatus.TRANSCRIBING
        ) {
            Spacer(Modifier.height(12.dp))
            TranscriptionProgressBar(
                progress = entry.progress,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    entry.status == TranscriptionStatus.QUEUED ->
                        stringResource(R.string.status_queued)

                    entry.progress > 0 -> stringResource(
                        R.string.status_progress,
                        stringResource(R.string.status_transcribing),
                        entry.progress,
                    )

                    else -> stringResource(R.string.status_transcribing)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.share_background_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (entry.status == TranscriptionStatus.FAILED && !state.modelMissing) {
            Spacer(Modifier.height(12.dp))
            ErrorState(
                title = stringResource(R.string.detail_error_title),
                message = entry.errorMessage ?: stringResource(R.string.error_generic),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = stringResource(R.string.action_open_settings),
                onClick = onOpenApp,
            )
        }

        if (entry.hasAudio) {
            Spacer(Modifier.height(12.dp))
            AudioPlayer(file = File(entry.audioPath.orEmpty()))
        } else if (entry.status == TranscriptionStatus.DONE) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.detail_audio_expired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (entry.status == TranscriptionStatus.DONE) {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                if (entry.hasTranscript) {
                    SelectionContainer {
                        Text(
                            text = entry.transcript.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.share_no_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // The label sits on its own line so a long label stays readable on narrow screens.
        if (entry.label.isNullOrBlank()) {
            LabelChip(
                label = stringResource(R.string.detail_label_add),
                onClick = onEditLabel,
            )
        } else {
            LabelChip(
                label = entry.label.orEmpty(),
                onClick = onEditLabel,
            )
        }

        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (entry.hasTranscript) {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(entry.transcript.orEmpty()))
                    copyCount++
                }) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                    )
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, entry.transcript.orEmpty())
                    }
                    context.startActivity(Intent.createChooser(send, shareChooser))
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.action_share),
                    )
                }
            }
            SecondaryButton(
                text = stringResource(R.string.share_open_in_app),
                onClick = onOpenApp,
            )
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = stringResource(R.string.action_close),
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
