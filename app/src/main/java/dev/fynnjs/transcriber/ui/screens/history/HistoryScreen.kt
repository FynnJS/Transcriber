package dev.fynnjs.transcriber.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fynnjs.transcriber.R
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.ui.components.EmptyState
import dev.fynnjs.transcriber.ui.components.HistoryCard
import dev.fynnjs.transcriber.ui.components.SearchField
import dev.fynnjs.transcriber.ui.components.TranscriberTopBar
import dev.fynnjs.transcriber.ui.util.dayKey
import dev.fynnjs.transcriber.ui.util.formatDayHeader
import dev.fynnjs.transcriber.ui.util.formatTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val todayLabel = stringResource(R.string.history_today)
    val yesterdayLabel = stringResource(R.string.history_yesterday)

    Scaffold(
        topBar = {
            TranscriberTopBar(
                title = stringResource(R.string.history_title),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isSearching) {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.history_search_empty_title),
                            message = stringResource(R.string.history_search_empty_text, query),
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.History,
                            title = stringResource(R.string.history_empty_title),
                            message = stringResource(R.string.history_empty_text),
                        )
                    }
                }
                return@Column
            }

            val groups = remember(entries) {
                entries.groupBy { dayKey(it.createdAt) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                groups.forEach { (day, dayEntries) ->
                    item(key = "header-$day") {
                        androidx.compose.material3.Text(
                            text = formatDayHeader(
                                context,
                                dayEntries.first().createdAt,
                                todayLabel,
                                yesterdayLabel,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        SwipeableHistoryCard(
                            entry = entry,
                            timeText = formatTime(context, entry.createdAt),
                            onClick = { onOpenDetail(entry.id) },
                            onDelete = {
                                viewModel.hideForDelete(entry.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = deletedMessage,
                                        actionLabel = undoLabel,
                                        withDismissAction = false,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restore(entry.id)
                                    } else {
                                        viewModel.commitDelete(entry.id)
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHistoryCard(
    entry: HistoryEntry,
    timeText: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        HistoryCard(
            entry = entry,
            timeText = timeText,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
