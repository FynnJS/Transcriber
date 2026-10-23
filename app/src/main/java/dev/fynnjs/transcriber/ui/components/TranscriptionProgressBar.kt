package dev.fynnjs.transcriber.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * Whisper reports progress per segment window, so voice messages shorter than one window only
 * produce a value once transcription is done. Until a real percentage is known the bar runs
 * indeterminate, so the user always sees that work is happening.
 */
@Composable
fun TranscriptionProgressBar(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0) {
        LinearProgressIndicator(modifier = modifier)
    } else {
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0, 100) / 100f,
            animationSpec = tween(durationMillis = 600),
            label = "transcriptionProgress",
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = modifier,
            drawStopIndicator = {},
        )
    }
}
