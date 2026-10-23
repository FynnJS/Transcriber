package dev.fynnjs.transcriber.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

/**
 * Heading that steps its font size down until its longest word fits the available width.
 *
 * Compose breaks a word in the middle when it is wider than the line. On very narrow displays
 * with a large font scale that makes headings hard to read, so this reduces the size in one-sp
 * steps, never below [minFontSize].
 */
@Composable
fun HeadingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    minFontSize: TextUnit = 10.sp,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val fitted = remember(style, text, constraints.maxWidth, minFontSize) {
            fitLongestWord(measurer, text, style, constraints.maxWidth, minFontSize)
        }
        Text(text = text, style = fitted)
    }
}

private fun fitLongestWord(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Int,
    minFontSize: TextUnit,
): TextStyle {
    if (maxWidth == Constraints.Infinity || style.fontSize.isUnspecified) return style
    val longestWord = text.split(WORD_SEPARATOR).maxByOrNull { it.length }.orEmpty()
    if (longestWord.isEmpty()) return style

    val baseSize = style.fontSize
    var size = baseSize
    while (true) {
        val wordWidth = measurer.measure(longestWord, style.copy(fontSize = size)).size.width
        if (wordWidth <= maxWidth || size <= minFontSize) {
            return style.copy(fontSize = size, lineHeight = scaledLineHeight(style, size))
        }
        size = (size.value - 1f).coerceAtLeast(minFontSize.value).sp
    }
}

private fun scaledLineHeight(style: TextStyle, fontSize: TextUnit): TextUnit {
    if (style.lineHeight.isUnspecified || style.fontSize.isUnspecified) return style.lineHeight
    return style.lineHeight * (fontSize.value / style.fontSize.value)
}

private val WORD_SEPARATOR = Regex("[\\s\\u00AD-]+")
