package dev.fynnjs.transcriber.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.fynnjs.transcriber.data.local.HistoryDao
import dev.fynnjs.transcriber.data.local.MIGRATION_1_2
import dev.fynnjs.transcriber.data.local.TranscriberDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TranscriberDatabase =
        Room.databaseBuilder(context, TranscriberDatabase::class.java, "transcriber.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideHistoryDao(database: TranscriberDatabase): HistoryDao = database.historyDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
