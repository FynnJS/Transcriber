package dev.fynnjs.transcriber.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.ModelError
import dev.fynnjs.transcriber.domain.model.ModelState

/** Short label for the state of a whisper model on disk. */
@Composable
fun modelStateLabel(state: ModelState): String = when (state) {
    is ModelState.Ready -> stringResource(R.string.settings_model_ready)
    is ModelState.Downloading ->
        stringResource(R.string.settings_model_downloading, state.progress)

    is ModelState.Failed -> when (state.error) {
        ModelError.CHECKSUM_MISMATCH -> stringResource(R.string.settings_model_checksum)
        ModelError.NO_SPACE -> stringResource(R.string.settings_model_no_space)
        else -> stringResource(R.string.settings_model_failed)
    }

    ModelState.NotDownloaded -> stringResource(R.string.settings_model_not_downloaded)
}
