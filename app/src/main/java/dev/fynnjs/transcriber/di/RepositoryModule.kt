package dev.fynnjs.transcriber.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fynnjs.transcriber.data.model.ModelRepositoryImpl
import dev.fynnjs.transcriber.data.preferences.SettingsRepositoryImpl
import dev.fynnjs.transcriber.data.repository.HistoryRepositoryImpl
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import dev.fynnjs.transcriber.domain.repository.ModelRepository
import dev.fynnjs.transcriber.domain.repository.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindModelRepository(impl: ModelRepositoryImpl): ModelRepository
}
