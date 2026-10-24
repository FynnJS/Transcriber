package dev.fynnjs.transcriber.ui.screens.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Keeps the explanation of the notification permission in front of the system dialog. */
@RunWith(AndroidJUnit4::class)
class NotificationOnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(resId: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    @Test
    fun explainsThePermissionAndOffersBothAnswers() {
        composeRule.setContent {
            TranscriberTheme(darkTheme = true) {
                NotificationOnboardingScreen(onFinished = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.notifications_onboarding_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.notifications_onboarding_progress_text)).assertExists()
        composeRule.onNodeWithText(string(R.string.notifications_onboarding_download_text)).assertExists()
        composeRule.onNodeWithText(string(R.string.notifications_onboarding_optional_text)).assertExists()
        composeRule.onNodeWithText(string(R.string.notifications_onboarding_allow)).assertExists()
        composeRule.onNodeWithText(string(R.string.notifications_onboarding_skip)).assertExists()
    }
}
