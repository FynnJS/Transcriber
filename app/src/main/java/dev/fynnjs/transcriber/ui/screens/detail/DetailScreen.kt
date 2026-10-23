package dev.fynnjs.transcriber.ui.screens.detail

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.ui.components.AudioPlayer
import dev.fynnjs.transcriber.ui.components.ErrorState
import dev.fynnjs.transcriber.ui.components.LabelChip
import dev.fynnjs.transcriber.ui.components.LabelEditorDialog
import dev.fynnjs.transcriber.ui.components.PrimaryButton
import dev.fynnjs.transcriber.ui.components.SecondaryButton
import dev.fynnjs.transcriber.ui.components.StatusChip
import dev.fynnjs.transcriber.ui.components.TranscriberTopBar
import dev.fynnjs.transcriber.ui.components.TranscriptionProgressBar
import dev.fynnjs.transcriber.ui.util.formatDate
import dev.fynnjs.transcriber.ui.util.formatDuration
import dev.fynnjs.transcriber.ui.util.formatTime
import dev.fynnjs.transcriber.ui.util.languageLabel
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val audioFile by viewModel.audioFile.collectAsStateWithLifecycle()
    val recentLabels by viewModel.recentLabels.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLabelDialog by remember { mutableStateOf(false) }
    var labelDraft by remember { mutableStateOf("") }

    val copiedText = stringResource(R.string.copied)
    val retryStarted = stringResource(R.string.detail_retry_started)
    val shareChooser = stringResource(R.string.detail_share_chooser)

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.detail_delete_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onDeleted = onBack)
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showLabelDialog) {
        LabelEditorDialog(
            initialValue = labelDraft,
            suggestions = recentLabels,
            onDismiss = { showLabelDialog = false },
            onSave = { label ->
                viewModel.setLabel(label)
                showLabelDialog = false
            },
        )
    }

    val current = entry
    Scaffold(
        topBar = {
            TranscriberTopBar(
                title = stringResource(R.string.detail_title),
                onBack = onBack,
                actions = {
                    if (current?.hasTranscript == true) {
                        IconButton(
                            onClick = {
                                val text = current.transcript.orEmpty()
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, shareChooser))
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.action_share),
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(current.transcript.orEmpty()))
                                scope.launch { snackbarHostState.showSnackbar(copiedText) }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.action_copy),
                            )
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (current == null) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${formatDate(current.createdAt)}, ${formatTime(context, current.createdAt)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatDuration(current.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(
                    status = current.status,
                    noText = current.status == TranscriptionStatus.DONE && !current.hasTranscript,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!current.label.isNullOrBlank()) {
                    LabelChip(
                        label = current.label.orEmpty(),
                        onClick = {
                            labelDraft = current.label.orEmpty()
                            showLabelDialog = true
                        },
                        onRemove = { viewModel.setLabel(null) },
                    )
                } else {
                    SecondaryButton(
                        text = stringResource(R.string.detail_label_add),
                        onClick = {
                            labelDraft = ""
                            showLabelDialog = true
                        },
                        icon = Icons.Outlined.Label,
                    )
                }
                SecondaryButton(
                    text = stringResource(R.string.detail_edit_label),
                    onClick = {
                        labelDraft = current.label.orEmpty()
                        showLabelDialog = true
                    },
                    icon = Icons.Outlined.Edit,
                )
            }

            Spacer(Modifier.height(16.dp))
            AudioPlayer(file = audioFile)
            if (current.audioPath == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.detail_audio_expired),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (current.status == TranscriptionStatus.QUEUED ||
                current.status == TranscriptionStatus.TRANSCRIBING
            ) {
                Spacer(Modifier.height(16.dp))
                TranscriptionProgressBar(
                    progress = current.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        current.status == TranscriptionStatus.QUEUED ->
                            stringResource(R.string.status_queued)

                        current.progress > 0 -> stringResource(
                            R.string.status_progress,
                            stringResource(R.string.status_transcribing),
                            current.progress,
                        )

                        else -> stringResource(R.string.status_transcribing)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (current.status == TranscriptionStatus.FAILED) {
                Spacer(Modifier.height(16.dp))
                ErrorState(
                    title = stringResource(R.string.detail_error_title),
                    message = current.errorMessage ?: stringResource(R.string.error_generic),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = {
                        viewModel.retry()
                        scope.launch { snackbarHostState.showSnackbar(retryStarted) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = stringResource(R.string.action_open_settings),
                    onClick = onOpenSettings,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.detail_transcript),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (current.hasTranscript) {
                        SelectionContainer {
                            Text(
                                text = current.transcript.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.detail_no_text),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.detail_no_text_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (current.hasTranscript) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.action_copy),
                        onClick = {
                            clipboard.setText(AnnotatedString(current.transcript.orEmpty()))
                            scope.launch { snackbarHostState.showSnackbar(copiedText) }
                        },
                        icon = Icons.Outlined.ContentCopy,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.action_share),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, current.transcript.orEmpty())
                            }
                            context.startActivity(Intent.createChooser(send, shareChooser))
                        },
                        icon = Icons.Outlined.Share,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.detail_model_info,
                    WhisperModel.fromId(current.modelUsed).displayName,
                    languageLabel(current.language),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.detail_created_at, formatDate(current.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

