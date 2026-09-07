package github.ponyhuang.gimi.core.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 校验待共享文件确实位于已注册的 FileProvider 目录内。 */
@Singleton
class ShareableFileValidator @Inject constructor(
    private val registry: StorageRegistry,
) {
    fun requireShareable(file: File): File {
        val candidate = file.canonicalFile
        val allowed = registry.specs
            .asSequence()
            .filter { it.sharingPolicy == SharingPolicy.FILE_PROVIDER }
            .map { registry.resolve(it.id).canonicalFile }
            .any { root -> candidate != root && candidate.toPath().startsWith(root.toPath()) }
        require(allowed) { "File is outside managed FileProvider directories: ${candidate.path}" }
        return candidate
    }
}

/** 为受管共享目录中的文件生成应用 FileProvider content URI。 */
@Singleton
class ShareableFileUriFactory internal constructor(
    private val validator: ShareableFileValidator,
    private val uriProvider: (File) -> Uri,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        validator: ShareableFileValidator,
    ) : this(
        validator = validator,
        uriProvider = { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        },
    )

    fun uriFor(file: File): Uri = uriProvider(validator.requireShareable(file))

    companion object {
        /** Compose/UI 边界在无法构造注入对象时，仍按同一受管目录规则生成 URI。 */
        fun uriFor(context: Context, file: File, spec: ManagedDirectorySpec): Uri {
            require(spec.sharingPolicy == SharingPolicy.FILE_PROVIDER) {
                "Directory is not registered for FileProvider sharing: ${spec.id}"
            }
            val root = AndroidAppDirectoryResolver(context).resolve(spec, create = true).canonicalFile
            val candidate = file.canonicalFile
            require(candidate != root && candidate.toPath().startsWith(root.toPath())) {
                "File is outside managed FileProvider directory: ${candidate.path}"
            }
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                candidate,
            )
        }
    }
}
