package dev.fynnjs.transcriber.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    private val hiddenIds = MutableStateFlow<Set<Long>>(emptySet())

    private val allEntries: StateFlow<List<HistoryEntry>> = query
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { value ->
            if (value.isBlank()) historyRepository.entries else historyRepository.search(value)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isSearching: StateFlow<Boolean> = query
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val entries: StateFlow<List<HistoryEntry>> = combine(allEntries, hiddenIds) { entries, hidden ->
        entries.filterNot { it.id in hidden }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Hides the entry immediately; deletion happens when the snackbar is dismissed. */
    fun hideForDelete(id: Long) {
        hiddenIds.update { it + id }
    }

    fun restore(id: Long) {
        hiddenIds.update { it - id }
    }

    fun commitDelete(id: Long) {
        viewModelScope.launch {
            historyRepository.delete(id)
            hiddenIds.update { it - id }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
