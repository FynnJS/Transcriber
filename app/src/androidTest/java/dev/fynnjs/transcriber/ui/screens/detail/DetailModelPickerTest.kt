package dev.fynnjs.transcriber.ui.screens.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.ModelError
import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Covers the model dropdown of the detail screen. */
@RunWith(AndroidJUnit4::class)
class DetailModelPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selected: WhisperModel? = null

    private fun string(resId: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun string(resId: Int, arg: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId, arg)

    private fun showPicker(
        selectedModel: WhisperModel,
        states: Map<WhisperModel, ModelState>,
    ) {
        composeRule.setContent {
            TranscriberTheme(darkTheme = true) {
                DetailModelPicker(
                    selected = selectedModel,
                    states = states,
                    onSelect = { selected = it },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theDownloadedModelIsShownAndAnotherModelCanBeChosen() {
        showPicker(
            selectedModel = WhisperModel.SMALL,
            states = mapOf(
                WhisperModel.SMALL to ModelState.Ready(File("small.bin")),
                WhisperModel.BASE to ModelState.NotDownloaded,
            ),
        )
        composeRule.onNodeWithText(string(R.string.detail_model_picker)).assertIsDisplayed()
        composeRule.onNodeWithText(WhisperModel.SMALL.displayName).assertIsDisplayed()
        composeRule.onNodeWithText(WhisperModel.SMALL.displayName).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.settings_model_not_downloaded), substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(WhisperModel.BASE.displayName).performClick()
        composeRule.waitForIdle()

        assertEquals(WhisperModel.BASE, selected)
    }

    @Test
    fun aFailedDownloadCanBeRestartedFromThePicker() {
        showPicker(
            selectedModel = WhisperModel.BASE,
            states = mapOf(
                WhisperModel.SMALL to ModelState.NotDownloaded,
                WhisperModel.BASE to ModelState.Failed(ModelError.DOWNLOAD_FAILED),
            ),
        )
        composeRule.onNodeWithText(string(R.string.settings_model_download)).performClick()
        composeRule.waitForIdle()

        assertEquals(WhisperModel.BASE, selected)
    }

    @Test
    fun aRunningDownloadShowsItsProgress() {
        showPicker(
            selectedModel = WhisperModel.BASE,
            states = mapOf(
                WhisperModel.SMALL to ModelState.NotDownloaded,
                WhisperModel.BASE to ModelState.Downloading(progress = 40),
            ),
        )
        composeRule.onNodeWithText(string(R.string.settings_model_downloading, 40))
            .assertIsDisplayed()
    }
}
