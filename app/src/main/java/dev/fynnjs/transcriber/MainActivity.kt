package dev.fynnjs.transcriber

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dev.fynnjs.transcriber.domain.model.ThemeMode
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import dev.fynnjs.transcriber.domain.repository.SettingsRepository
import dev.fynnjs.transcriber.service.TranscriptionService
import dev.fynnjs.transcriber.ui.components.AppLockGate
import dev.fynnjs.transcriber.ui.screens.detail.DetailScreen
import dev.fynnjs.transcriber.ui.screens.history.HistoryScreen
import dev.fynnjs.transcriber.ui.screens.onboarding.OnboardingScreen
import dev.fynnjs.transcriber.ui.screens.privacy.PrivacyScreen
import dev.fynnjs.transcriber.ui.screens.settings.SettingsScreen
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var modelRepository: ModelRepository

    @Inject lateinit var historyRepository: HistoryRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Denial is handled in the UI; transcription still works. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialSettings = runBlocking { settingsRepository.current() }
        enableEdgeToEdge()
        applySecureFlag(initialSettings.secureScreen)

        setContent {
            val startDestination = remember {
                if (modelRepository.isReady(WhisperModel.DEFAULT)) Routes.HISTORY else Routes.ONBOARDING
            }
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = initialSettings)

            LaunchedEffect(settings.secureScreen) {
                applySecureFlag(settings.secureScreen)
            }

            TranscriberTheme(darkTheme = settings.theme == ThemeMode.DARK) {
                AppLockGate(enabled = settings.appLock) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable(Routes.ONBOARDING) {
                            OnboardingScreen(
                                onFinished = {
                                    navController.navigate(Routes.HISTORY) {
                                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(Routes.HISTORY) {
                            HistoryScreen(
                                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            )
                        }
                        composable(
                            route = Routes.DETAIL,
                            arguments = listOf(
                                navArgument(Routes.DETAIL_ARG) { type = NavType.LongType },
                            ),
                        ) {
                            DetailScreen(
                                onBack = { navController.popBackStack() },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                            )
                        }
                        composable(Routes.PRIVACY) {
                            PrivacyScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }

        requestNotificationPermissionOnce()
        resumeQueueIfNeeded()
    }

    private fun applySecureFlag(enabled: Boolean) {
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_PROMPTED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPTED, true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun resumeQueueIfNeeded() {
        lifecycleScope.launch {
            historyRepository.resetStuckEntries()
            if (historyRepository.nextQueued() != null) {
                TranscriptionService.resumeQueue(this@MainActivity)
            }
        }
    }

    private companion object {
        const val PREFS = "ui_state"
        const val KEY_NOTIFICATION_PROMPTED = "notification_prompted"
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
    const val DETAIL_ARG = "entryId"
    const val DETAIL = "detail/{$DETAIL_ARG}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun detail(id: Long): String = "detail/$id"
}
