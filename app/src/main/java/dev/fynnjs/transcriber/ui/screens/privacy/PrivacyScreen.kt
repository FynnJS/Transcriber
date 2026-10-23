package dev.fynnjs.transcriber.ui.screens.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.ui.components.TranscriberTopBar

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TranscriberTopBar(
                title = stringResource(R.string.privacy_title),
                onBack = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.privacy_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
