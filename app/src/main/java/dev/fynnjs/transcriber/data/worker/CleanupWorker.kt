package dev.fynnjs.transcriber.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.fynnjs.transcriber.domain.usecase.CleanupExpiredData

class CleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CleanupEntryPoint {
        fun cleanupExpiredData(): CleanupExpiredData
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CleanupEntryPoint::class.java,
        )
        return runCatching {
            entryPoint.cleanupExpiredData().run()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
