package dev.fynnjs.transcriber.ui.screens.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Keeps the label row of the detail screen usable on narrow displays. */
@RunWith(AndroidJUnit4::class)
class DetailLabelActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val longLabel =
        "Arbeit – Projekt Apollo Kickoff mit dem gesamten Kernteam und allen Stakeholdern"

    private fun showRow(widthDp: Int, label: String?) {
        composeRule.setContent {
            TranscriberTheme(darkTheme = true) {
                Box(
                    Modifier
                        .width(widthDp.dp)
                        .padding(16.dp),
                ) {
                    DetailLabelActions(
                        label = label,
                        onAddLabel = {},
                        onEditLabel = {},
                        onRemoveLabel = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun string(resId: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    @Test
    fun longLabelStaysReadableAndKeepsActionsVisible() {
        showRow(widthDp = 320, label = longLabel)
        val label = composeRule.onNodeWithText(longLabel).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "label text collapsed",
            label.width >= with(composeRule.density) { 48.dp.toPx() },
        )
        assertTrue(
            "label text overflows the row",
            label.right <= with(composeRule.density) { 320.dp.toPx() },
        )
        composeRule.onNodeWithContentDescription(string(R.string.detail_label_remove))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_edit_label)).assertIsDisplayed()
    }

    @Test
    fun missingLabelKeepsBothActionsVisible() {
        showRow(widthDp = 320, label = null)
        composeRule.onNodeWithText(string(R.string.detail_label_add)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.detail_edit_label)).assertIsDisplayed()
    }
}
