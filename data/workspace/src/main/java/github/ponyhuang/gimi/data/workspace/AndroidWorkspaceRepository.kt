package github.ponyhuang.gimi.data.workspace

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.core.storage.WorkspaceStorageIds
import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFile
import github.ponyhuang.gimi.domain.workspace.repository.WorkspaceRepository
import java.io.File
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于文件系统的工作区管理实现。
 *
 * 工作区目录由 [WorkspaceStorageIds.WORKSPACE] 声明（`:data:conversation` 的归档写入方
 * 与本实现共用同一目录）。文件系统是唯一真源：列表每次现场遍历，不维护任何与磁盘可能
 * 失步的元数据。
 */
@Singleton
class AndroidWorkspaceRepository @Inject constructor(
    storageRegistry: StorageRegistry,
) : WorkspaceRepository {

    private val workspaceRoot = storageRegistry.resolve(
        WorkspaceStorageIds.WORKSPACE,
        create = true,
    )

    override suspend fun list(): List<WorkspaceFile> = withContext(Dispatchers.IO) {
        workspaceRoot
            .listFiles { file -> file.isFile && !file.name.endsWith(TMP_SUFFIX) }
            .orEmpty()
            .map { it.toWorkspaceFile() }
            .sortedByDescending(WorkspaceFile::lastModifiedMillis)
    }

    override suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        // 与 list() 同口径：只统计用户可见的归档内容，瞬时 .tmp 暂存不计入占用。
        workspaceRoot
            .listFiles { file -> file.isFile && !file.name.endsWith(TMP_SUFFIX) }
            ?.sumOf(File::length)
            ?: 0L
    }

    override suspend fun delete(file: WorkspaceFile): Boolean = withContext(Dispatchers.IO) {
        val target = runCatching { File(file.path).canonicalFile }.getOrNull()
            ?: return@withContext false
        // 路径守卫：只允许删除工作区根目录直下的文件，防止越权删除任意路径。
        if (target.parentFile != workspaceRoot.canonicalFile) return@withContext false
        if (!target.isFile) return@withContext false
        target.delete()
    }

    private fun File.toWorkspaceFile(): WorkspaceFile = WorkspaceFile(
        name = name,
        path = absolutePath,
        sizeBytes = length(),
        lastModifiedMillis = lastModified(),
        mimeType = mimeTypeFor(name),
    )

    private companion object {
        private const val TMP_SUFFIX = ".tmp"

        /** 扩展名 → MIME：先走 JVM 内建表，再按常见归档扩展名兜底。 */
        private fun mimeTypeFor(name: String): String? {
            URLConnection.guessContentTypeFromName(name)?.let { return it }
            return when (name.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
                "mp3", "wav", "m4a", "aac", "ogg", "flac", "amr" -> "audio/*"
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "txt", "md", "csv", "epub",
                -> "application/octet-stream"
                else -> null
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object WorkspaceDataModule {

    @Provides
    @Singleton
    fun provideWorkspaceRepository(
        implementation: AndroidWorkspaceRepository,
    ): WorkspaceRepository = implementation
}
