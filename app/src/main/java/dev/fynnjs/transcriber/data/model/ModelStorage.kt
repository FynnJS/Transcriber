package dev.fynnjs.transcriber.data.model

import android.content.Context
import dev.fynnjs.transcriber.domain.model.WhisperModel
import java.io.File
import java.security.MessageDigest

/** Filesystem layout for whisper models: filesDir/models/ggml-*.bin. */
class ModelStorage(context: Context) {
    val directory: File = File(context.filesDir, "models")

    fun file(model: WhisperModel): File = File(directory, model.fileName)

    fun partFile(model: WhisperModel): File = File(directory, "${model.fileName}.part")

    fun isPresent(model: WhisperModel): Boolean {
        val file = file(model)
        return file.isFile && file.length() == model.approxBytes
    }

    fun delete(model: WhisperModel) {
        file(model).delete()
        partFile(model).delete()
    }

    fun directoryReady(): Boolean = directory.exists() || directory.mkdirs()

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
