package dev.fynnjs.transcriber.ui.screens.settings

import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fynnjs.transcriber.BuildConfig
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.AppSettings
import dev.fynnjs.transcriber.domain.model.AudioRetention
import dev.fynnjs.transcriber.domain.model.ModelError
import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.ThemeMode
import dev.fynnjs.transcriber.domain.model.TranscriptionLanguage
import dev.fynnjs.transcriber.domain.model.TranscriptRetention
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.ui.components.InfoRow
import dev.fynnjs.transcriber.ui.components.PrimaryButton
import dev.fynnjs.transcriber.ui.components.SecondaryButton
import dev.fynnjs.transcriber.ui.components.SectionHeader
import dev.fynnjs.transcriber.ui.components.TranscriberTopBar
import dev.fynnjs.transcriber.ui.components.findFragmentActivity
import dev.fynnjs.transcriber.ui.components.promptForAuthentication
import dev.fynnjs.transcriber.ui.util.formatSizeMb
import kotlinx.coroutines.launch

private const val BIOMETRIC_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    var retentionDialog by remember { mutableStateOf<RetentionDialog?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val audioDeletedMessage = stringResource(R.string.settings_audio_deleted_now)
    val allDeletedMessage = stringResource(R.string.settings_data_deleted)
    val modelDeletedMessage = stringResource(R.string.settings_model_deleted)
    val appLockEnableTitle = stringResource(R.string.settings_app_lock_enable_title)
    val appLockEnableSubtitle = stringResource(R.string.settings_app_lock_enable_subtitle)
    val appLockDisableTitle = stringResource(R.string.settings_app_lock_disable_title)
    val appLockDisableSubtitle = stringResource(R.string.settings_app_lock_disable_subtitle)
    val appLockConfirmFailedMessage = stringResource(R.string.settings_app_lock_confirm_failed)

    // Enabling and disabling the lock both require one device confirmation.
    val confirmAppLockChange: (Boolean) -> Unit = { enable ->
        val activity = context.findFragmentActivity()
        if (activity == null) {
            scope.launch { snackbarHostState.showSnackbar(appLockConfirmFailedMessage) }
        } else {
            promptForAuthentication(
                activity = activity,
                title = if (enable) appLockEnableTitle else appLockDisableTitle,
                subtitle = if (enable) appLockEnableSubtitle else appLockDisableSubtitle,
                onSuccess = { viewModel.setAppLock(enable) },
                onError = {
                    scope.launch { snackbarHostState.showSnackbar(appLockConfirmFailedMessage) }
                },
            )
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.settings_delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.deleteAllData {
                            scope.launch { snackbarHostState.showSnackbar(allDeletedMessage) }
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    retentionDialog?.let { dialog ->
        ChoiceDialog(
            title = dialog.title,
            options = dialog.options,
            selected = dialog.selected,
            onSelect = { value ->
                dialog.onSelected(value)
                retentionDialog = null
                scope.launch { snackbarHostState.showSnackbar(audioDeletedMessage) }
            },
            onDismiss = { retentionDialog = null },
        )
    }

    Scaffold(
        topBar = {
            TranscriberTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_design))
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.theme == ThemeMode.DARK,
                        onClick = { viewModel.setTheme(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.settings_theme_dark)) },
                    )
                    FilterChip(
                        selected = settings.theme == ThemeMode.LIGHT,
                        onClick = { viewModel.setTheme(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.settings_theme_light)) },
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_section_transcription))
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_model),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                WhisperModel.entries.forEach { model ->
                    ModelRow(
                        model = model,
                        active = settings.model == model,
                        state = modelStates[model] ?: ModelState.NotDownloaded,
                        onSelect = { viewModel.setModel(model) },
                        onDownload = { viewModel.download(model) },
                        onDelete = {
                            viewModel.deleteModel(model)
                            scope.launch { snackbarHostState.showSnackbar(modelDeletedMessage) }
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.language == TranscriptionLanguage.AUTO,
                        onClick = { viewModel.setLanguage(TranscriptionLanguage.AUTO) },
                        label = { Text(stringResource(R.string.settings_language_auto)) },
                    )
                    FilterChip(
                        selected = settings.language == TranscriptionLanguage.GERMAN,
                        onClick = { viewModel.setLanguage(TranscriptionLanguage.GERMAN) },
                        label = { Text(stringResource(R.string.settings_language_de)) },
                    )
                }

                if (!notificationsEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_notifications_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_section_retention))
            SettingsCard {
                SettingValueRow(
                    title = stringResource(R.string.settings_audio_retention),
                    value = context.getString(settings.audioRetention.labelRes()),
                    onClick = {
                        val options = AudioRetention.entries.map { context.getString(it.labelRes()) }
                        retentionDialog = RetentionDialog(
                            title = context.getString(R.string.settings_audio_retention),
                            options = options,
                            selected = context.getString(settings.audioRetention.labelRes()),
                            onSelected = { label ->
                                AudioRetention.entries.firstOrNull {
                                    context.getString(it.labelRes()) == label
                                }?.let(viewModel::setAudioRetention)
                            },
                        )
                    },
                )
                SettingValueRow(
                    title = stringResource(R.string.settings_transcript_retention),
                    value = context.getString(settings.transcriptRetention.labelRes()),
                    onClick = {
                        val options = TranscriptRetention.entries.map {
                            context.getString(it.labelRes())
                        }
                        retentionDialog = RetentionDialog(
                            title = context.getString(R.string.settings_transcript_retention),
                            options = options,
                            selected = context.getString(settings.transcriptRetention.labelRes()),
                            onSelected = { label ->
                                TranscriptRetention.entries.firstOrNull {
                                    context.getString(it.labelRes()) == label
                                }?.let(viewModel::setTranscriptRetention)
                            },
                        )
                    },
                )
            }

            SectionHeader(stringResource(R.string.settings_section_privacy))
            SettingsCard {
                SwitchRow(
                    icon = { Icon(Icons.Outlined.Lock, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_app_lock),
                    subtitle = if (biometricAvailable) {
                        stringResource(R.string.settings_app_lock_desc)
                    } else {
                        stringResource(R.string.settings_app_lock_unavailable)
                    },
                    checked = settings.appLock,
                    enabled = biometricAvailable,
                    onCheckedChange = confirmAppLockChange,
                )
                SwitchRow(
                    icon = { Icon(Icons.Outlined.Security, null, Modifier.size(20.dp)) },
                    title = stringResource(R.string.settings_secure_screen),
                    subtitle = stringResource(R.string.settings_secure_screen_desc),
                    checked = settings.secureScreen,
                    enabled = true,
                    onCheckedChange = viewModel::setSecureScreen,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.PrivacyTip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.settings_incognito_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.settings_incognito_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = stringResource(R.string.settings_delete_all),
                    onClick = { showDeleteAllDialog = true },
                    icon = Icons.Outlined.Delete,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader(stringResource(R.string.settings_about))
            SettingsCard {
                InfoRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.settings_privacy_policy),
                    trailing = {
                        TextButton(onClick = onOpenPrivacy) {
                            Text(stringResource(R.string.action_open))
                        }
                    },
                )
                InfoRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                )
                Text(
                    text = stringResource(R.string.settings_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ModelRow(
    model: WhisperModel,
    active: Boolean,
    state: ModelState,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = active, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = model.displayName, style = MaterialTheme.typography.titleSmall)
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_model_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = if (model == WhisperModel.SMALL) {
                    stringResource(R.string.settings_model_small_desc)
                } else {
                    stringResource(R.string.settings_model_base_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_model_size, formatSizeMb(model.approxBytes)) +
                    " · " + stateLabel(state),
                style = MaterialTheme.typography.labelSmall,
                color = when (state) {
                    is ModelState.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (state is ModelState.Downloading) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when (state) {
            is ModelState.Downloading -> {
                CircularProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }

            is ModelState.Ready -> {
                if (!active) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.settings_model_delete),
                        )
                    }
                }
            }

            else -> {
                SecondaryButton(
                    text = stringResource(R.string.settings_model_download),
                    onClick = onDownload,
                    icon = Icons.Outlined.Download,
                )
            }
        }
    }
}

@Composable
private fun stateLabel(state: ModelState): String = when (state) {
    is ModelState.Ready -> stringResource(R.string.settings_model_ready)
    is ModelState.Downloading -> stringResource(R.string.settings_model_downloading, state.progress)
    is ModelState.Failed -> when (state.error) {
        ModelError.CHECKSUM_MISMATCH -> stringResource(R.string.settings_model_checksum)
        ModelError.NO_SPACE -> stringResource(R.string.settings_model_no_space)
        else -> stringResource(R.string.settings_model_failed)
    }

    ModelState.NotDownloaded -> stringResource(R.string.settings_model_not_downloaded)
}

@Composable
private fun SwitchRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SettingValueRow(title: String, value: String, onClick: () -> Unit) {
    // FlowRow keeps the full title width when large font scales leave no room for the value.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) { Text(value) }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(option, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@StringRes
private fun AudioRetention.labelRes(): Int = when (this) {
    AudioRetention.IMMEDIATELY -> R.string.retention_immediately
    AudioRetention.DAYS_7 -> R.string.retention_7_days
    AudioRetention.DAYS_30 -> R.string.retention_30_days
    AudioRetention.DAYS_90 -> R.string.retention_90_days
    AudioRetention.NEVER -> R.string.retention_never
}

@StringRes
private fun TranscriptRetention.labelRes(): Int = when (this) {
    TranscriptRetention.DAYS_7 -> R.string.retention_7_days
    TranscriptRetention.DAYS_30 -> R.string.retention_30_days
    TranscriptRetention.DAYS_90 -> R.string.retention_90_days
    TranscriptRetention.NEVER -> R.string.retention_never
}

private data class RetentionDialog(
    val title: String,
    val options: List<String>,
    val selected: String,
    val onSelected: (String) -> Unit,
)
