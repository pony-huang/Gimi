package github.ponyhuang.gimi.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 各应用存储区域的实际根目录。
 *
 * @property files 内部持久化文件根目录。
 * @property cache 内部缓存根目录。
 * @property codeCache 运行时代码缓存根目录。
 * @property externalFiles 应用专属外部文件根目录；不可用时为 null。
 */
data class StorageRoots(
    val files: File,
    val cache: File,
    val codeCache: File,
    val externalFiles: File?,
)

/** 将受管目录声明安全地解析为本机目录。 */
interface AppDirectoryResolver {
    fun resolve(spec: ManagedDirectorySpec, create: Boolean = false): File
}

/** 不依赖 Android Context 的安全路径解析实现。 */
class FileSystemAppDirectoryResolver(
    private val roots: StorageRoots,
) : AppDirectoryResolver {
    override fun resolve(spec: ManagedDirectorySpec, create: Boolean): File {
        validateSpec(spec)
        val root = rootFor(spec.area).canonicalFile
        val candidate = File(root, spec.relativePath).canonicalFile
        require(candidate != root && candidate.toPath().startsWith(root.toPath())) {
            "Managed directory '${spec.id}' escapes ${spec.area} root: ${spec.relativePath}"
        }
        if (create) {
            check(candidate.isDirectory || candidate.mkdirs()) {
                "Unable to create managed directory '${spec.id}' at ${candidate.path}"
            }
        }
        return candidate
    }

    private fun validateSpec(spec: ManagedDirectorySpec) {
        require(spec.id.isNotBlank() && '.' in spec.id) { "Managed directory id must be namespaced" }
        require(spec.owner.isNotBlank()) { "Managed directory owner must not be blank" }
        require(spec.relativePath.isNotBlank()) { "Managed directory path must not be blank" }
        require(!File(spec.relativePath).isAbsolute) { "Managed directory path must be relative" }
    }

    private fun rootFor(area: StorageArea): File = when (area) {
        StorageArea.FILES -> roots.files
        StorageArea.CACHE -> roots.cache
        StorageArea.CODE_CACHE -> roots.codeCache
        StorageArea.EXTERNAL_FILES -> roots.externalFiles ?: roots.files
    }
}

/** 从 Application Context 提供 Android 存储根目录的进程级 resolver。 */
@Singleton
class AndroidAppDirectoryResolver @Inject constructor(
    @ApplicationContext context: Context,
) : AppDirectoryResolver by FileSystemAppDirectoryResolver(
    StorageRoots(
        files = context.filesDir,
        cache = context.cacheDir,
        codeCache = context.codeCacheDir,
        externalFiles = context.getExternalFilesDir(null),
    ),
)
