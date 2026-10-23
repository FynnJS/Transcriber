package dev.fynnjs.transcriber

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dev.fynnjs.transcriber.domain.model.HistoryEntry
import dev.fynnjs.transcriber.domain.repository.HistoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var historyRepository: HistoryRepository

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        runBlocking { historyRepository.deleteAll() }
    }

    @Test
    fun sharingAudioCreatesEntryAndCopiesFile() {
        val file = sampleFile("share-once.wav")

        ActivityScenario.launch<ShareReceiverActivity>(shareIntent(file)).use {
            val entry = awaitEntry()
            assertNotNull(entry)
            assertEquals(64, entry!!.audioHash.length)
            assertTrue(entry.audioPath != null)
            assertTrue(File(entry.audioPath!!).isFile)
            assertTrue(File(entry.audioPath!!).canonicalPath.contains("/files/audio/"))
        }
    }

    @Test
    fun sharingSameFileTwiceReusesEntry() {
        val file = sampleFile("share-twice.wav")

        ActivityScenario.launch<ShareReceiverActivity>(shareIntent(file)).use {
            val first = awaitEntry()
            assertNotNull(first)
        }

        val countAfterFirst = runBlocking { historyRepository.entries.first().size }
        assertEquals(1, countAfterFirst)

        ActivityScenario.launch<ShareReceiverActivity>(shareIntent(file)).use {
            Thread.sleep(1_500)
        }

        val entries = runBlocking { historyRepository.entries.first() }
        assertEquals(1, entries.size)
    }

    private fun shareIntent(file: File): Intent =
        Intent(context, ShareReceiverActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
        }

    private fun sampleFile(name: String): File {
        val file = File(context.cacheDir, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open("jfk.wav").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    private fun awaitEntry(): HistoryEntry? = runBlocking {
        withTimeoutOrNull(15_000) {
            while (true) {
                val entries = historyRepository.entries.first()
                if (entries.isNotEmpty()) return@withTimeoutOrNull entries.first()
                delay(150)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }
}
