package github.ponyhuang.gimi.feature.chat

import android.content.Context
import android.net.Uri
import github.ponyhuang.gimi.core.storage.AndroidAppDirectoryResolver
import github.ponyhuang.gimi.core.storage.ShareableFileUriFactory
import java.io.File

/**
 * 相机拍摄开始前创建的临时共享目标。
 *
 * @property uri 提供给系统相机写入的 FileProvider URI。
 * @property path 用于拍摄取消时回收临时文件的本地路径。
 */
internal data class PendingCameraAttachment(
    val uri: Uri,
    val path: String,
)

internal fun createPendingCameraAttachment(context: Context): PendingCameraAttachment {
    val shareableRoot = AndroidAppDirectoryResolver(context).resolve(
        chatShareableDirectorySpec,
        create = true,
    )
    val cameraDirectory = File(shareableRoot, CAMERA_DIRECTORY).apply {
        check(exists() || mkdirs()) { "Could not create camera cache directory" }
    }
    val file = File.createTempFile(CAMERA_FILE_PREFIX, CAMERA_FILE_SUFFIX, cameraDirectory)
    val uri = ShareableFileUriFactory.uriFor(context, file, chatShareableDirectorySpec)
    return PendingCameraAttachment(uri = uri, path = file.absolutePath)
}

internal fun deletePendingCameraAttachment(path: String?) {
    path?.let(::File)?.delete()
}

private const val CAMERA_DIRECTORY = "camera"
private const val CAMERA_FILE_PREFIX = "attachment_"
private const val CAMERA_FILE_SUFFIX = ".jpg"
