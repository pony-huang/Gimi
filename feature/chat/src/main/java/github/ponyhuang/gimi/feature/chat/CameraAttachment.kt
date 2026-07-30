package github.ponyhuang.gimi.feature.chat

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal data class PendingCameraAttachment(
    val uri: Uri,
    val path: String,
)

internal fun createPendingCameraAttachment(context: Context): PendingCameraAttachment {
    val cameraDirectory = File(context.cacheDir, CAMERA_DIRECTORY).apply {
        check(exists() || mkdirs()) { "Could not create camera cache directory" }
    }
    val file = File.createTempFile(CAMERA_FILE_PREFIX, CAMERA_FILE_SUFFIX, cameraDirectory)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    return PendingCameraAttachment(uri = uri, path = file.absolutePath)
}

internal fun deletePendingCameraAttachment(path: String?) {
    path?.let(::File)?.delete()
}

internal fun deleteCameraAttachment(context: Context, uri: Uri) {
    if (uri.scheme != "content" || uri.authority != "${context.packageName}.fileprovider") return
    val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return
    File(File(context.cacheDir, CAMERA_DIRECTORY), fileName).delete()
}

private const val CAMERA_DIRECTORY = "camera"
private const val CAMERA_FILE_PREFIX = "attachment_"
private const val CAMERA_FILE_SUFFIX = ".jpg"
