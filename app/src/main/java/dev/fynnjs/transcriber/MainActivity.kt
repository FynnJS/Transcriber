package dev.fynnjs.transcriber

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import dev.fynnjs.transcriber.ui.screens.onboarding.NotificationOnboardingScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialSettings = runBlocking { settingsRepository.current() }
        enableEdgeToEdge()
        applySecureFlag(initialSettings.secureScreen)

        setContent {
            val contentDestination = remember { initialRoute(modelRepository) }
            val initialDestination = remember {
                startDestination(contentDestination, notificationOnboardingNeeded())
            }
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = initialSettings)

            LaunchedEffect(settings.secureScreen) {
                applySecureFlag(settings.secureScreen)
            }

            TranscriberTheme(darkTheme = settings.theme == ThemeMode.DARK) {
                AppLockGate(enabled = settings.appLock) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = initialDestination) {
                        composable(Routes.NOTIFICATIONS) {
                            NotificationOnboardingScreen(
                                onFinished = {
                                    markNotificationOnboardingShown()
                                    navController.navigate(contentDestination) {
                                        popUpTo(Routes.NOTIFICATIONS) { inclusive = true }
                                    }
                                },
                            )
                        }
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

        resumeQueueIfNeeded()
    }

    private fun applySecureFlag(enabled: Boolean) {
        if (enabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun notificationOnboardingNeeded(): Boolean =
        notificationOnboardingNeeded(
            sdkInt = Build.VERSION.SDK_INT,
            permissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
            alreadyShown = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATION_PROMPTED, false),
        )

    private fun markNotificationOnboardingShown() {
        // The key name predates the explainer screen; its value is kept so an update does not
        // prompt users again who already answered the old, unexplained dialog.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIFICATION_PROMPTED, true)
            .apply()
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

/**
 * Skips onboarding as soon as any model is usable. The active model can be Base instead of the
 * default Small, so tying the start destination to the default model alone would send users who
 * deleted Small back to the download screen after the activity was recreated.
 */
internal fun initialRoute(modelRepository: ModelRepository): String =
    if (WhisperModel.entries.any { modelRepository.isReady(it) }) Routes.HISTORY else Routes.ONBOARDING

/**
 * Android 13 and newer ask for the notification permission at runtime. The explainer runs once
 * before that dialog; older versions grant it without a prompt and a granted permission needs no
 * explanation.
 */
internal fun notificationOnboardingNeeded(
    sdkInt: Int,
    permissionGranted: Boolean,
    alreadyShown: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU && !permissionGranted && !alreadyShown

/** Runs the explainer in front of the model onboarding, so no dialog interrupts the download. */
internal fun startDestination(contentDestination: String, notificationsNeeded: Boolean): String =
    if (notificationsNeeded) Routes.NOTIFICATIONS else contentDestination

object Routes {
    const val NOTIFICATIONS = "notifications"
    const val ONBOARDING = "onboarding"
    const val HISTORY = "history"
    const val DETAIL_ARG = "entryId"
    const val DETAIL = "detail/{$DETAIL_ARG}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun detail(id: Long): String = "detail/$id"
}
