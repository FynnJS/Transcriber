package dev.fynnjs.transcriber.data.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fynnjs.transcriber.domain.model.WhisperModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.RandomAccessFile
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ModelStorageTest {

    private lateinit var context: Context
    private lateinit var storage: ModelStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = ModelStorage(context)
        storage.directory.deleteRecursively()
    }

    @After
    fun tearDown() {
        ShadowStatFs.unregisterStats(storage.directory.path)
        storage.directory.deleteRecursively()
    }

    /** Two downloads started together used to see `mkdirs()` fail for the second one. */
    @Test
    fun directoryReadySucceedsForConcurrentDownloads() {
        val threads = 8
        val barrier = CyclicBarrier(threads)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val results = (1..threads).map {
                executor.submit<Boolean> {
                    barrier.await(5, TimeUnit.SECONDS)
                    storage.directoryReady()
                }
            }.map { it.get(5, TimeUnit.SECONDS) }

            assertTrue("directoryReady() failed for ${results.count { !it }} callers", results.all { it })
            assertTrue(storage.directory.isDirectory)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun directoryReadyAcceptsAnExistingFolder() {
        storage.directory.mkdirs()

        assertTrue(storage.directoryReady())
        assertTrue(storage.directoryReady())
    }

    @Test
    fun hasRoomForRejectsAModelThatDoesNotFit() {
        assertTrue(storage.directoryReady())
        ShadowStatFs.registerStats(storage.directory.path, 1000, 1000, 1)

        assertFalse(storage.hasRoomFor(WhisperModel.SMALL))
    }

    @Test
    fun hasRoomForIgnoresBytesThatAreAlreadyDownloaded() {
        assertTrue(storage.directoryReady())
        ShadowStatFs.registerStats(storage.directory.path, 1000, 1000, 1)
        RandomAccessFile(storage.partFile(WhisperModel.BASE), "rw").use {
            it.setLength(WhisperModel.BASE.approxBytes)
        }

        assertTrue(storage.hasRoomFor(WhisperModel.BASE))
    }
}
