package dev.fynnjs.transcriber.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import androidx.compose.ui.tooling.preview.Preview

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Optional biometric lock shown before the history. When no device credential is configured
 * the app stays usable and explains why the lock cannot be enabled.
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    var unlocked by rememberSaveable { mutableStateOf(!enabled) }
    if (!enabled || unlocked) {
        content()
        return
    }

    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val unavailableText = stringResource(R.string.lock_unavailable)
    val failedText = stringResource(R.string.lock_failed)

    val canAuthenticate = remember(activity) {
        activity != null &&
            BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(attempt) {
        if (!canAuthenticate) {
            error = unavailableText
            return@LaunchedEffect
        }
        promptForUnlock(
            activity = activity!!,
            onSuccess = { unlocked = true },
            onError = { error = failedText },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
            error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (!canAuthenticate) {
                Text(
                    text = stringResource(R.string.settings_app_lock_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                PrimaryButton(
                    text = stringResource(R.string.lock_button),
                    onClick = { attempt++ },
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

private fun promptForUnlock(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.lock_title))
        .setSubtitle(activity.getString(R.string.lock_subtitle))
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
    prompt.authenticate(info)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@Preview(name = "App-Sperre dunkel", showBackground = true)
@Composable
private fun AppLockPreview() {
    TranscriberTheme(darkTheme = true) {
        AppLockGate(enabled = true) {}
    }
}
