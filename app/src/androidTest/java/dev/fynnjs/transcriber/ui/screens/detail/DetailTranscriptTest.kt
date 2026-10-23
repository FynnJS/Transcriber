package dev.fynnjs.transcriber.ui.screens.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Keeps the transcript area of the detail screen in sync with the entry status. */
@RunWith(AndroidJUnit4::class)
class DetailTranscriptTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val transcript = "Ein kurzes Beispieltranskript aus der Sprachnachricht."

    private fun string(resId: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun showArea(status: TranscriptionStatus, transcript: String? = null) {
        composeRule.setContent {
            TranscriberTheme(darkTheme = true) {
                TranscriptArea(status = status, transcript = transcript)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun queuedEntryShowsPendingHintInsteadOfEmptyState() {
        showArea(TranscriptionStatus.QUEUED)
        composeRule.onNodeWithText(string(R.string.detail_transcript_pending)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_no_text)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.detail_no_text_hint)).assertDoesNotExist()
    }

    @Test
    fun transcribingEntryShowsPendingHintInsteadOfEmptyState() {
        showArea(TranscriptionStatus.TRANSCRIBING)
        composeRule.onNodeWithText(string(R.string.detail_transcript_pending)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_no_text)).assertDoesNotExist()
    }

    @Test
    fun finishedEntryWithoutTextShowsEmptyState() {
        showArea(TranscriptionStatus.DONE)
        composeRule.onNodeWithText(string(R.string.detail_no_text)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_no_text_hint)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_transcript_pending)).assertDoesNotExist()
    }

    @Test
    fun finishedEntryShowsItsTranscript() {
        showArea(TranscriptionStatus.DONE, transcript)
        composeRule.onNodeWithText(transcript).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_no_text)).assertDoesNotExist()
    }

    @Test
    fun failedEntryWithoutTextLeavesTheAreaToTheErrorState() {
        showArea(TranscriptionStatus.FAILED)
        composeRule.onNodeWithText(string(R.string.detail_transcript)).assertDoesNotExist()
    }
}
