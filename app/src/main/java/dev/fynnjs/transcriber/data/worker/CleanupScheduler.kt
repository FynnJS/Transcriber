package dev.fynnjs.transcriber.data.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CleanupScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    /** Daily cleanup; KEEP so an existing schedule survives app restarts. */
    fun scheduleDaily() {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Applies a changed retention rule right away. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<CleanupWorker>().build()
        workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val PERIODIC_WORK = "retention-cleanup"
        const val NOW_WORK = "retention-cleanup-now"
    }
}
