package dev.fynnjs.transcriber.data.audio

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioImportError {
    NO_AUDIO,
    UNREADABLE,
    NOT_ENOUGH_SPACE,
    TOO_LARGE,
}

class AudioImportException(
    val error: AudioImportError,
    cause: Throwable? = null,
) : IOException(error.name, cause)

data class ImportedAudio(
    val file: File,
    val sha256: String,
    val sourceFileName: String,
)

/**
 * Owns the app-private audio folder. Shared content URIs are only readable for a short
 * time, so the stream is copied here before anything else happens.
 */
@Singleton
class AudioFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioDir: File
        get() = File(context.filesDir, "audio").apply { mkdirs() }

    val directory: File get() = audioDir

    suspend fun import(uri: Uri): ImportedAudio = withContext(Dispatchers.IO) {
        val metadata = readMetadata(uri)
        if (metadata.size != null && metadata.size > MAX_BYTES) {
            throw AudioImportException(AudioImportError.TOO_LARGE)
        }
        val needed = (metadata.size ?: (MAX_BYTES / 2)) + MIN_FREE_BYTES
        if (availableBytes() < needed) {
            throw AudioImportException(AudioImportError.NOT_ENOUGH_SPACE)
        }

        val output = File(audioDir, "${UUID.randomUUID()}.opus")
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw AudioImportException(AudioImportError.NO_AUDIO)
            input.use { stream ->
                output.outputStream().buffered().use { sink ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > MAX_BYTES) {
                            throw AudioImportException(AudioImportError.TOO_LARGE)
                        }
                        digest.update(buffer, 0, read)
                        sink.write(buffer, 0, read)
                    }
                }
            }
            if (written == 0L) {
                throw AudioImportException(AudioImportError.NO_AUDIO)
            }
        } catch (e: AudioImportException) {
            output.delete()
            throw e
        } catch (e: SecurityException) {
            output.delete()
            throw AudioImportException(AudioImportError.UNREADABLE, e)
        } catch (e: IOException) {
            output.delete()
            throw AudioImportException(AudioImportError.UNREADABLE, e)
        }

        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        ImportedAudio(
            file = output,
            sha256 = hash,
            sourceFileName = metadata.displayName ?: output.name,
        )
    }

    /** Resolves a stored path, refusing anything outside the app audio folder. */
    fun resolve(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        val base = audioDir.canonicalFile
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        if (!canonical.path.startsWith(base.path + File.separator)) return null
        return canonical.takeIf { it.isFile }
    }

    fun delete(path: String?): Boolean {
        val file = resolve(path) ?: return false
        return file.delete()
    }

    fun freeBytes(): Long = availableBytes()

    fun deleteAllFiles(): Int {
        val files = audioDir.listFiles() ?: return 0
        return files.count { it.isFile && it.delete() }
    }

    private fun availableBytes(): Long = runCatching {
        StatFs(context.filesDir.path).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    private data class Metadata(val displayName: String?, val size: Long?)

    private fun readMetadata(uri: Uri): Metadata {
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use Metadata(fallbackName, null)
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                Metadata(name ?: fallbackName, size)
            } ?: Metadata(fallbackName, null)
        }.getOrDefault(Metadata(fallbackName, null))
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_BYTES = 100L * 1024 * 1024
        const val MIN_FREE_BYTES = 32L * 1024 * 1024
    }
}
