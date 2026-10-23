package dev.fynnjs.transcriber.data.local

import androidx.room.Entity
import androidx.room.Fts4

/** External-content FTS table kept in sync by Room triggers. */
@Fts4(contentEntity = HistoryEntryEntity::class, tokenizer = "unicode61")
@Entity(tableName = "history_entries_fts")
data class HistoryEntryFts(
    val transcript: String?,
    val label: String?,
)
