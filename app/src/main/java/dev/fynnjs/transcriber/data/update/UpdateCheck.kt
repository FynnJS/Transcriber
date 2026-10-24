package dev.fynnjs.transcriber.data.update

import dev.fynnjs.transcriber.domain.model.AppUpdate

internal const val UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"
}

private val VERSION_PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?""")

/** Accepts `1.2.3`, `v1.2.3` and the debug suffix `1.2.3-debug`. */
internal fun parseVersion(raw: String): AppVersion? {
    val match = VERSION_PATTERN.find(raw.trim()) ?: return null
    return AppVersion(
        major = match.groupValues[1].toIntOrNull() ?: return null,
        minor = match.groupValues[2].toIntOrNull() ?: return null,
        patch = match.groupValues[3].toIntOrNull() ?: 0,
    )
}

internal fun isUpdateCheckDue(nowMillis: Long, lastCheckMillis: Long): Boolean =
    lastCheckMillis <= 0L || nowMillis - lastCheckMillis >= UPDATE_CHECK_INTERVAL_MS

internal sealed interface ReleaseCheck {
    data class Newer(val update: AppUpdate) : ReleaseCheck
    data object UpToDate : ReleaseCheck
    data object MissingApk : ReleaseCheck
}

internal fun evaluateRelease(release: GitHubRelease, currentVersion: String): ReleaseCheck {
    val tagged = parseVersion(release.tagName) ?: return ReleaseCheck.UpToDate
    val current = parseVersion(currentVersion) ?: return ReleaseCheck.UpToDate
    if (tagged <= current) return ReleaseCheck.UpToDate

    val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        ?: return ReleaseCheck.MissingApk
    return ReleaseCheck.Newer(
        AppUpdate(
            versionName = tagged.toString(),
            notes = release.body.orEmpty().trim(),
            downloadUrl = apk.downloadUrl,
            sizeBytes = apk.size,
        ),
    )
}
