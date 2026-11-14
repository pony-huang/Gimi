package github.ponyhuang.gimi.data.skills

import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.domain.skills.repository.SkillSnapshotFailure
import github.ponyhuang.gimi.domain.skills.repository.SkillSnapshotRepository
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 将技能全部资源打包，保持相对路径，防止执行时遗漏模板与引用文件。 */
internal class FileSkillSnapshotRepository @Inject constructor(
    private val store: SkillArchiveStore,
    private val registry: StorageRegistry,
    private val enabledStore: SkillEnabledStore,
) : SkillSnapshotRepository {
    override suspend fun export(name: String): String {
        var prepared: File? = null
        try {
            return withContext(Dispatchers.IO) {
                require(name.matches(Regex("[a-zA-Z0-9_-]+")))
                if (name in enabledStore.disabledNames()) throw SkillSnapshotFailure(
                    SkillSnapshotFailure.Reason.Disabled,
                )
                store.withInstalledSnapshot(name) { root ->
                    val archive = File.createTempFile("execution-", ".zip", registry.resolve(SkillsStorage.IMPORTS_ID, create = true)).also { prepared = it }
                    try {
                        ZipOutputStream(archive.outputStream()).use { zip ->
                            var bytes = 0L
                            var count = 0
                            root.walkTopDown().onEnter { directory ->
                                require(!java.nio.file.Files.isSymbolicLink(directory.toPath()))
                                true
                            }.filter { it.isFile }.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach { file ->
                                require(!java.nio.file.Files.isSymbolicLink(file.toPath()))
                                require(file.canonicalFile.toPath().startsWith(root.toPath()))
                                require(++count <= 2000)
                                bytes += file.length()
                                require(bytes <= 100L * 1024 * 1024)
                                zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath).apply { time = 0L })
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                        archive.absolutePath
                    } catch (error: Exception) {
                        archive.delete()
                        throw error
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { prepared?.delete() }
            throw cancelled
        }
    }

    override suspend fun release(archivePath: String) = withContext(Dispatchers.IO) {
        val file = File(archivePath).canonicalFile
        require(file.parentFile == registry.resolve(SkillsStorage.IMPORTS_ID).canonicalFile)
        file.delete()
        Unit
    }
}
