package dev.fynnjs.transcriber.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fynnjs.transcriber.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelEditorDialog(
    initialValue: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_label_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= 40) value = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.detail_label_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.detail_label_suggestions),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { suggestion ->
                            LabelChip(
                                label = suggestion,
                                onClick = { value = suggestion },
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim().takeIf { it.isNotEmpty() }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
