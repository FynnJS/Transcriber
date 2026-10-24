package dev.fynnjs.transcriber.ui.screens.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.model.TranscriptionStatus
import dev.fynnjs.transcriber.ui.theme.TranscriberTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Keeps the share overlay usable on displays narrower than a regular phone. */
@RunWith(AndroidJUnit4::class)
class ShareOverlayLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val longLabel =
        "Arbeit – Projekt Apollo Kickoff mit dem gesamten Kernteam und allen Stakeholdern"

    private val entry = HistoryEntry(
        id = 1,
        createdAt = 0L,
        sourceFileName = "WhatsApp Sprachnachricht 2026-10-23.opus",
        durationMs = 42_000L,
        audioPath = null,
        audioHash = "hash",
        transcript = "Ein kurzes Beispieltranskript für das Teilen-Overlay.",
        language = "de",
        modelUsed = "small",
        status = TranscriptionStatus.DONE,
        label = longLabel,
    )

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun showOverlay(widthDp: Int, fontScale: Float = 1f, heightDp: Int? = null) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                TranscriberTheme(darkTheme = true) {
                    val size = if (heightDp == null) {
                        Modifier.width(widthDp.dp).fillMaxHeight()
                    } else {
                        Modifier.width(widthDp.dp).height(heightDp.dp)
                    }
                    Box(size) {
                        ShareOverlayScreen(
                            uiState = ShareUiState.Ready(entry = entry, duplicate = false, modelMissing = false),
                            recentLabels = emptyList(),
                            onSaveLabel = {},
                            onRetry = {},
                            onClose = {},
                            onOpenApp = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertLabelAndActionsVisible(widthDp: Int) {
        showOverlay(widthDp)
        val label = composeRule.onNodeWithText(longLabel).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "label text collapsed at ${widthDp}dp",
            label.width >= with(composeRule.density) { 48.dp.toPx() },
        )
        assertTrue(
            "label chip overflows ${widthDp}dp",
            label.right <= with(composeRule.density) { widthDp.dp.toPx() },
        )
        composeRule.onNodeWithContentDescription(context().getString(R.string.action_copy))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context().getString(R.string.action_share))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context().getString(R.string.share_open_in_app))
            .assertIsDisplayed()
    }

    @Test
    fun keepsLabelReadableOnNarrowDisplay() {
        assertLabelAndActionsVisible(widthDp = 320)
    }

    @Test
    fun keepsLabelReadableOnVeryNarrowDisplay() {
        assertLabelAndActionsVisible(widthDp = 240)
    }

    @Test
    fun keepsContentReachableWhenTheSheetIsTallerThanTheDisplay() {
        // Short viewport with a large font scale: the sheet content cannot fit on screen.
        showOverlay(widthDp = 411, fontScale = 2f, heightDp = 240)
        composeRule.onNodeWithText(context().getString(R.string.share_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context().getString(R.string.action_close))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun headingBreaksBetweenWordsAtLargeFontScale() {
        showOverlay(widthDp = 240, fontScale = 2f)
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(context().getString(R.string.share_title), useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        val layout = layouts.single()
        val text = layout.layoutInput.text.text
        fun breaksBetweenWords(index: Int): Boolean {
            if (index <= 0 || index >= text.length) return true
            val before = text[index - 1]
            val after = text[index]
            return before.isWhitespace() || after.isWhitespace() || before == '-' || after == '-'
        }
        for (line in 0 until layout.lineCount - 1) {
            assertTrue(
                "heading breaks a word at line $line: '${text.take(layout.getLineEnd(line))}'",
                breaksBetweenWords(layout.getLineEnd(line)),
            )
        }
    }

    @Test
    fun keepsButtonLabelsUnclippedAtLargeFontScale() {
        showOverlay(widthDp = 240, fontScale = 2f)
        val closeLabel = context().getString(R.string.action_close)
        // The sheet scrolls at this font scale; only the visible part of a node carries bounds,
        // so scroll the sheet to its end before measuring the bottom buttons.
        val sheet = composeRule
            .onNode(hasScrollAction() and hasAnyDescendant(hasText(closeLabel)))
            .fetchSemanticsNode()
        val scrollBy = sheet.config[SemanticsActions.ScrollBy].action
        repeat(4) {
            scrollBy?.invoke(0f, 2_000f)
            composeRule.waitForIdle()
        }
        listOf(R.string.share_open_in_app, R.string.action_close).forEach { resId ->
            val label = context().getString(resId)
            val button = composeRule.onNode(hasText(label) and hasClickAction())
                .fetchSemanticsNode().boundsInRoot
            val text = composeRule.onAllNodesWithText(label, useUnmergedTree = true)
                .fetchSemanticsNodes().single().boundsInRoot
            assertTrue(
                "button label '$label' is clipped: text=$text button=$button",
                text.top > button.top && text.bottom < button.bottom,
            )
        }
    }
}
