package github.ponyhuang.gimi.data.workfiles.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 共享存储的权限、挂载卷与只读 URI 使用同一访问边界。 */
@Singleton
class SharedStorageAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val authority get() = "${context.packageName}.sharedfiles"

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    fun roots(): List<File> = context.getSystemService(StorageManager::class.java)
        .storageVolumes
        .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
        .mapNotNull { it.directory }

    fun uriForFile(file: File): Uri = Uri.Builder()
        .scheme("content")
        .authority(authority)
        .appendPath("file")
        .appendPath(file.path)
        .build()

    fun fileForUri(value: String): File? {
        if (!hasAccess()) return null
        val uri = Uri.parse(value)
        if (uri.scheme != "content" || uri.authority != authority ||
            uri.pathSegments.size != 2 || uri.pathSegments[0] != "file" ||
            uri.query != null || uri.fragment != null
        ) return null
        return SharedStorageFilePolicy(roots()).resolve(File(uri.pathSegments[1]))
            ?.takeIf { it.isFile && it.canRead() }
    }
}

/** 规范化后校验卷边界，排除根目录、私有数据和指向卷外的符号链接。 */
internal class SharedStorageFilePolicy(roots: List<File>) {
    private val roots = roots.map { it.canonicalFile.toPath() }

    fun resolve(candidate: File): File? = try {
        val canonical = candidate.canonicalFile
        val path = canonical.toPath()
        val root = roots.firstOrNull { path != it && path.startsWith(it) }
        if (root == null) {
            null
        } else {
            val relative = root.relativize(path)
            val privateDirectory = relative.nameCount >= 2 &&
                relative.getName(0).toString().equals("Android", ignoreCase = true) &&
                relative.getName(1).toString().lowercase(Locale.ROOT) in setOf("data", "obb", "sandbox")
            canonical.takeUnless { privateDirectory }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

internal fun sharedFileMimeType(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
        ?: "application/octet-stream"
