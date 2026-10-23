package dev.fynnjs.transcriber.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history_entries",
    indices = [
        Index("createdAt"),
        Index("audioHash"),
        Index("status"),
    ],
)
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val sourceFileName: String,
    val durationMs: Long,
    val audioPath: String?,
    val audioHash: String,
    val transcript: String?,
    val language: String,
    val modelUsed: String,
    val status: String,
    val errorMessage: String?,
    val label: String?,
    val progress: Int,
)
