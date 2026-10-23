package dev.fynnjs.transcriber.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.ModelError
import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.ui.components.PrimaryButton
import dev.fynnjs.transcriber.ui.util.formatSizeMb

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val baseState by viewModel.baseState.collectAsStateWithLifecycle()
    val model = viewModel.model
    var downloadBase by rememberSaveable { mutableStateOf(false) }
    val selectedBytes = if (downloadBase) {
        model.approxBytes + WhisperModel.BASE.approxBytes
    } else {
        model.approxBytes
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
            FeatureRow(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(R.string.onboarding_feature_offline),
                text = stringResource(R.string.onboarding_feature_offline_text),
            )
            Spacer(Modifier.height(16.dp))
            FeatureRow(
                icon = Icons.Outlined.Share,
                title = stringResource(R.string.onboarding_feature_share),
                text = stringResource(R.string.onboarding_feature_share_text),
            )

            Spacer(Modifier.height(32.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.onboarding_download_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.onboarding_download_text,
                            stringResource(
                                R.string.settings_model_size,
                                formatSizeMb(selectedBytes),
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))

                    when (val current = state) {
                        is ModelState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { current.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                drawStopIndicator = {},
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.onboarding_downloading,
                                        current.progress,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }

                        is ModelState.Ready -> {
                            PrimaryButton(
                                text = stringResource(R.string.onboarding_continue),
                                onClick = onFinished,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (downloadBase && baseState !is ModelState.Ready) {
                                BaseModelStatus(baseState)
                            }
                        }

                        else -> {
                            if (current is ModelState.Failed) {
                                Text(
                                    text = when (current.error) {
                                        ModelError.CHECKSUM_MISMATCH ->
                                            stringResource(R.string.onboarding_error_checksum)

                                        ModelError.NO_SPACE ->
                                            stringResource(R.string.onboarding_error_space)

                                        else -> stringResource(R.string.onboarding_error_failed)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = downloadBase,
                                        role = Role.Checkbox,
                                        onValueChange = { downloadBase = it },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = downloadBase, onCheckedChange = null)
                                Spacer(Modifier.size(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(
                                            R.string.onboarding_base_option,
                                            stringResource(
                                                R.string.settings_model_size,
                                                formatSizeMb(WhisperModel.BASE.approxBytes),
                                            ),
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_model_base_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = stringResource(
                                    if (downloadBase) {
                                        R.string.onboarding_download_button_multi
                                    } else {
                                        R.string.onboarding_download_button
                                    },
                                    stringResource(
                                        R.string.settings_model_size,
                                        formatSizeMb(selectedBytes),
                                    ),
                                ),
                                onClick = { viewModel.download(includeBase = downloadBase) },
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Outlined.Download,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.onboarding_wifi_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BaseModelStatus(state: ModelState) {
    when (state) {
        is ModelState.Downloading -> {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_base_downloading, state.progress),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        is ModelState.Failed -> {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_base_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        else -> Unit
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
