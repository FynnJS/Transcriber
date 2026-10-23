package dev.fynnjs.transcriber

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.fynnjs.transcriber.domain.model.ThemeMode
import dev.fynnjs.transcriber.domain.repository.SettingsRepository
import dev.fynnjs.transcriber.ui.screens.share.ShareOverlayScreen
import dev.fynnjs.transcriber.ui.screens.share.ShareViewModel
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Slim overlay for the Android share sheet. Shows a bottom sheet, imports the audio into
 * app-private storage and starts transcription.
 */
@AndroidEntryPoint
class ShareReceiverActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val viewModel: ShareViewModel by viewModels()
    private var darkTheme by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = runBlocking { settingsRepository.current() }
        darkTheme = settings.theme == ThemeMode.DARK
        if (settings.secureScreen) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        enableEdgeToEdge()

        val uri = intent?.let { incoming ->
            if (incoming.action == Intent.ACTION_SEND) {
                incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                null
            }
        }
        viewModel.start(uri)

        setContent {
            TranscriberTheme(darkTheme = darkTheme) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val labels by viewModel.recentLabels.collectAsStateWithLifecycle()
                BackHandler { closeOverlay() }
                ShareOverlayScreen(
                    uiState = state,
                    recentLabels = labels,
                    onSaveLabel = viewModel::saveLabel,
                    onRetry = viewModel::retry,
                    onClose = ::closeOverlay,
                    onOpenApp = ::openApp,
                )
            }
        }
    }

    private fun closeOverlay() {
        finishAndRemoveTask()
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
        finishAndRemoveTask()
    }
}
