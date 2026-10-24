package dev.fynnjs.transcriber.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import dev.fynnjs.transcriber.domain.model.ModelState
import dev.fynnjs.transcriber.domain.model.WhisperModel
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
) : ViewModel() {

    val model = WhisperModel.SMALL

    val state: StateFlow<ModelState> = modelRepository.states
        .map { it[model] ?: ModelState.NotDownloaded }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelState.NotDownloaded)

    /** State of the optional base model, downloaded in addition to the default model. */
    val baseState: StateFlow<ModelState> = modelRepository.states
        .map { it[WhisperModel.BASE] ?: ModelState.NotDownloaded }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelState.NotDownloaded)

    fun download(includeBase: Boolean) {
        modelRepository.download(model)
        if (includeBase) modelRepository.download(WhisperModel.BASE)
    }
}
