package dev.fynnjs.transcriber.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntryEntity::class, HistoryEntryFts::class],
    version = 2,
    exportSchema = true,
)
abstract class TranscriberDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
