package dev.fynnjs.transcriber.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun parsesTagWithAndWithoutPrefix() {
        assertEquals(AppVersion(1, 2, 3), parseVersion("v1.2.3"))
        assertEquals(AppVersion(1, 2, 3), parseVersion("1.2.3"))
        assertEquals(AppVersion(1, 2, 0), parseVersion("1.2"))
        assertEquals(AppVersion(2, 0, 1), parseVersion("2.0.1"))
        assertNull(parseVersion("nightly"))
    }

    @Test
    fun debugSuffixKeepsTheReleaseVersion() {
        assertEquals(AppVersion(1, 0, 0), parseVersion("1.0.0-debug"))
    }

    @Test
    fun newerTagWithApkBecomesAnAvailableUpdate() {
        val release = release(
            tag = "v1.3.0",
            body = "  Neue Funktionen  ",
            assets = listOf(GitHubAsset("transcriber-1.3.0-arm64-v8a.apk", "https://example.test/app.apk", 12_345)),
        )

        val result = evaluateRelease(release, currentVersion = "1.2.9")

        assertEquals(
            ReleaseCheck.Newer(
                dev.fynnjs.transcriber.domain.model.AppUpdate(
                    versionName = "1.3.0",
                    notes = "Neue Funktionen",
                    downloadUrl = "https://example.test/app.apk",
                    sizeBytes = 12_345,
                ),
            ),
            result,
        )
    }

    @Test
    fun sameAndOlderReleasesAreUpToDate() {
        assertEquals(ReleaseCheck.UpToDate, evaluateRelease(release(tag = "v1.3.0"), "1.3.0"))
        assertEquals(ReleaseCheck.UpToDate, evaluateRelease(release(tag = "v1.3.0"), "1.4.0"))
    }

    @Test
    fun newerReleaseWithoutApkIsReportedSeparately() {
        val result = evaluateRelease(release(tag = "v2.0.0", assets = emptyList()), "1.0.0")
        assertEquals(ReleaseCheck.MissingApk, result)
    }

    @Test
    fun apkAssetIsFoundRegardlessOfCase() {
        val release = release(
            tag = "v2.0.0",
            assets = listOf(GitHubAsset("Transcriber.APK", "https://example.test/app.apk")),
        )
        assertTrue(evaluateRelease(release, "1.0.0") is ReleaseCheck.Newer)
    }

    @Test
    fun unreadableTagNeverOffersAnUpdate() {
        assertEquals(ReleaseCheck.UpToDate, evaluateRelease(release(tag = "nightly"), "1.0.0"))
    }

    @Test
    fun checkIsDueOncePerDay() {
        val now = 10 * UPDATE_CHECK_INTERVAL_MS
        assertTrue(isUpdateCheckDue(now, lastCheckMillis = 0L))
        assertFalse(isUpdateCheckDue(now, lastCheckMillis = now - UPDATE_CHECK_INTERVAL_MS + 1))
        assertTrue(isUpdateCheckDue(now, lastCheckMillis = now - UPDATE_CHECK_INTERVAL_MS))
    }

    private fun release(
        tag: String,
        body: String? = null,
        assets: List<GitHubAsset> = listOf(
            GitHubAsset("transcriber.apk", "https://example.test/app.apk"),
        ),
    ) = GitHubRelease(tagName = tag, body = body, assets = assets)
}
