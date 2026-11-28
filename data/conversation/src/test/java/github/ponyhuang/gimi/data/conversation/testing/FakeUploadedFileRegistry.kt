package github.ponyhuang.gimi.data.conversation.testing

import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 进程内 [UploadedFileRegistry] 假实现：记录与归档绑定全部存内存映射，键为
 * `<vendor>/<fileId>`，供 JVM 单测隔离 Room 与真文件系统。
 */
class FakeUploadedFileRegistry : UploadedFileRegistry {
    private val entries = MutableStateFlow<Map<String, UploadedFileEntry>>(emptyMap())

    /** 当前全部记录；测试断言辅助。 */
    val all: Collection<UploadedFileEntry> get() = entries.value.values

    override suspend fun record(entry: UploadedFileEntry) {
        entries.value = entries.value + (key(entry.vendor, entry.fileId) to entry)
    }

    override fun entriesFor(vendor: String): Flow<List<UploadedFileEntry>> =
        entries.map { current ->
            current.values
                .filter { it.vendor == vendor }
                .sortedByDescending { it.createdAtEpochSeconds }
        }

    override suspend fun localPathFor(vendor: String, fileId: String): String? =
        entries.value[key(vendor, fileId)]?.localPath

    override suspend fun bindLocalFile(vendor: String, fileId: String, localPath: String) {
        val current = entries.value[key(vendor, fileId)] ?: return
        entries.value = entries.value + (key(vendor, fileId) to current.copy(localPath = localPath))
    }

    override suspend fun remove(vendor: String, fileId: String) {
        entries.value = entries.value - key(vendor, fileId)
    }

    private fun key(vendor: String, fileId: String) = "$vendor/$fileId"
}
