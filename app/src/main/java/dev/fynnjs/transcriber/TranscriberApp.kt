package dev.fynnjs.transcriber

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.fynnjs.transcriber.data.worker.CleanupScheduler
import javax.inject.Inject

@HiltAndroidApp
class TranscriberApp : Application() {

    @Inject lateinit var cleanupScheduler: CleanupScheduler

    override fun onCreate() {
        super.onCreate()
        cleanupScheduler.scheduleDaily()
        cleanupScheduler.runNow()
    }
}
