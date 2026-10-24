package dev.fynnjs.transcriber.ui.screens.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fynnjs.transcriber.domain.model.UpdateState
import dev.fynnjs.transcriber.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    val state: StateFlow<UpdateState> = updateRepository.state

    /** Called on every start; the repository keeps it to one request per day. */
    fun checkAutomatically() {
        viewModelScope.launch { updateRepository.check(manual = false) }
    }

    fun checkManually() {
        viewModelScope.launch { updateRepository.check(manual = true) }
    }

    fun download() {
        updateRepository.download()
    }

    fun skip() {
        updateRepository.skip()
    }

    fun dismiss() {
        updateRepository.dismiss()
    }
}
