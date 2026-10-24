package dev.fynnjs.transcriber.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the live preview column for running transcriptions. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history_entries ADD COLUMN partialTranscript TEXT")
    }
}
