package dev.fynnjs.transcriber.domain.repository

import dev.fynnjs.transcriber.domain.model.UpdateState
import kotlinx.coroutines.flow.StateFlow

interface UpdateRepository {

    val state: StateFlow<UpdateState>

    /**
     * Reads the latest GitHub release. [manual] ignores the daily interval and reports failures
     * in [state]; automatic checks stay silent on errors and at most run once per day.
     */
    suspend fun check(manual: Boolean)

    /** Downloads the available APK and verifies that it belongs to this app and signature. */
    fun download()

    /** Hides the offered version for good; called when the user postpones the update. */
    fun skip()

    /** Closes the current update UI without remembering a version. */
    fun dismiss()
}
