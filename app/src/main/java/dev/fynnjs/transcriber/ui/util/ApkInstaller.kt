package dev.fynnjs.transcriber.ui.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/**
 * Opens the system installer for a downloaded update. The installer itself asks for the
 * "Install unknown apps" permission when the app has not been allowed to install packages yet.
 */
fun installApk(context: Context, apk: File): Boolean {
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    }.getOrNull() ?: return false

    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, APK_MIME_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}
