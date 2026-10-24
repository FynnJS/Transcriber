package dev.fynnjs.transcriber.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.fynnjs.transcriber.BuildConfig
import dev.fynnjs.transcriber.domain.model.AppUpdate
import dev.fynnjs.transcriber.domain.model.UpdateError
import dev.fynnjs.transcriber.domain.model.UpdateState
import dev.fynnjs.transcriber.domain.repository.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the latest GitHub release and downloads its APK. The app has no update server of its
 * own, so the public repository is the single source for releases.
 */
@Singleton
class GitHubUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : UpdateRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val preferences by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    override suspend fun check(manual: Boolean) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Ready) return
        val now = System.currentTimeMillis()
        if (!manual && !isUpdateCheckDue(now, preferences.getLong(KEY_LAST_CHECK, 0L))) return

        _state.value = UpdateState.Checking
        val outcome = runCatching { fetchLatestRelease() }
            .mapCatching { release -> evaluateRelease(release, BuildConfig.VERSION_NAME) }
        // Also remember failed attempts so a missing connection does not trigger a check per start.
        preferences.edit().putLong(KEY_LAST_CHECK, now).apply()

        _state.value = outcome.fold(
            onSuccess = { result ->
                when (result) {
                    is ReleaseCheck.Newer -> {
                        // "Später" only silences the daily reminder; a manual check still offers
                        // the update, otherwise the version could never be installed in-app.
                        val skipped = preferences.getString(KEY_SKIPPED_VERSION, null)
                        if (!manual && result.update.versionName == skipped) {
                            UpdateState.Idle
                        } else {
                            UpdateState.Available(result.update)
                        }
                    }

                    ReleaseCheck.MissingApk ->
                        if (manual) UpdateState.Failed(UpdateError.MISSING_APK, null) else UpdateState.Idle

                    ReleaseCheck.UpToDate -> UpdateState.UpToDate
                }
            },
            onFailure = {
                if (manual) UpdateState.Failed(UpdateError.NETWORK, null) else UpdateState.Idle
            },
        )
    }

    override fun download() {
        val update = when (val current = _state.value) {
            is UpdateState.Available -> current.update
            is UpdateState.Failed -> current.update
            else -> null
        } ?: return
        if (_state.value is UpdateState.Downloading) return

        scope.launch {
            _state.value = UpdateState.Downloading(0)
            val target = File(updatesDirectory(), "transcriber-${update.versionName}.apk")
            val downloaded = runCatching { downloadApk(update, target) }

            if (downloaded.isFailure) {
                target.delete()
                _state.value = UpdateState.Failed(UpdateError.NETWORK, update)
                return@launch
            }
            if (!isMatchingApk(target)) {
                target.delete()
                _state.value = UpdateState.Failed(UpdateError.INVALID_APK, update)
                return@launch
            }
            _state.value = UpdateState.Ready(update, target)
        }
    }

    override fun skip() {
        val version = when (val current = _state.value) {
            is UpdateState.Available -> current.update.versionName
            is UpdateState.Ready -> current.update.versionName
            else -> null
        }
        if (version != null) {
            preferences.edit().putString(KEY_SKIPPED_VERSION, version).apply()
        }
        _state.value = UpdateState.Idle
    }

    override fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = URL("$API_BASE/repos/$REPOSITORY/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) error("GitHub antwortete mit $status")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<GitHubRelease>(body)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadApk(update: AppUpdate, target: File) = withContext(Dispatchers.IO) {
        val connection = URL(update.downloadUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) error("GitHub antwortete mit $status")

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            var received = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        if (total > 0) {
                            val percent = ((received * 100) / total).toInt().coerceIn(0, 100)
                            _state.value = UpdateState.Downloading(percent)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Android only replaces an app with the same package name and signing key. Checking both up
     * front turns a silent "App not installed" into a clear error in the dialog.
     */
    private fun isMatchingApk(file: File): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        if (archive.packageName != context.packageName) return false

        val installed = runCatching {
            manager.getPackageInfo(context.packageName, flags)
        }.getOrNull() ?: return false
        val archiveCertificates = certificateHashes(archive)
        return archiveCertificates.isNotEmpty() &&
            archiveCertificates == certificateHashes(installed)
    }

    private fun certificateHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            Base64.encodeToString(digest, Base64.NO_WRAP)
        }
    }

    private fun updatesDirectory(): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    private companion object {
        const val API_BASE = "https://api.github.com"
        const val REPOSITORY = "FynnJS/Transcriber"
        const val USER_AGENT = "Transcriber-Android"
        const val PREFS = "update_state"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_SKIPPED_VERSION = "skipped_version"
    }
}
