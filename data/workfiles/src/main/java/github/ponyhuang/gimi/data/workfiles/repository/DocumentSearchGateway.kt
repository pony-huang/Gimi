package github.ponyhuang.gimi.data.workfiles.repository

import github.ponyhuang.gimi.domain.workfiles.model.WorkFileSearchResult
import java.io.File
import java.io.IOException
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 隔离共享存储权限和文件系统遍历。 */
interface DocumentSearchGateway {
    fun hasAccess(): Boolean
    suspend fun search(query: String, limit: Int): List<WorkFileSearchResult>
    suspend fun contains(contentUri: String): Boolean
}

/** 直接遍历已挂载共享存储，覆盖未进入 MediaStore 索引的文档。 */
@Singleton
class AndroidDocumentSearchGateway @Inject constructor(
    private val access: SharedStorageAccess,
) : DocumentSearchGateway {
    override fun hasAccess(): Boolean = access.hasAccess()

    override suspend fun search(query: String, limit: Int): List<WorkFileSearchResult> =
        withContext(Dispatchers.IO) {
            if (!hasAccess()) return@withContext emptyList()
            newestSharedFiles(access.roots(), query, limit).map { file ->
                WorkFileSearchResult(
                    contentUri = access.uriForFile(file).toString(),
                    displayName = file.name,
                    mimeType = sharedFileMimeType(file),
                    sizeBytes = file.length(),
                    modifiedTimeMillis = file.lastModified(),
                )
            }
        }

    override suspend fun contains(contentUri: String): Boolean = withContext(Dispatchers.IO) {
        access.fileForUri(contentUri) != null
    }
}

/** 只保留最新的有限结果；遍历期间响应取消，目录别名不会形成循环。 */
internal suspend fun newestSharedFiles(roots: List<File>, query: String, limit: Int): List<File> {
    val policy = SharedStorageFilePolicy(roots)
    val newest = PriorityQueue<File>(compareBy<File> { it.lastModified() }.thenBy { it.path })
    val pending = ArrayDeque<File>().apply { addAll(roots) }
    val visited = mutableSetOf<String>()
    val foundPaths = mutableSetOf<String>()
    while (pending.isNotEmpty()) {
        coroutineContext.ensureActive()
        val directory = pending.removeLast()
        val children = try {
            val canonical = directory.canonicalFile
            if (!visited.add(canonical.path)) continue
            canonical.listFiles().orEmpty()
        } catch (_: SecurityException) {
            continue
        } catch (_: IOException) {
            continue
        }
        for (child in children) {
            coroutineContext.ensureActive()
            val file = policy.resolve(child) ?: continue
            if (file.isDirectory) {
                pending.add(file)
            } else if (file.isFile && file.name.contains(query, ignoreCase = true) && foundPaths.add(file.path)) {
                newest.add(file)
                if (newest.size > limit.coerceIn(1, 50)) {
                    foundPaths.remove(newest.poll().path)
                }
            }
        }
    }
    return newest.toList().sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.path })
}
