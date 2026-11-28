package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.data.conversation.local.UploadedFileDao
import github.ponyhuang.gimi.data.conversation.local.UploadedFileEntity
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultUploadedFileRegistryTest {

    @Test
    fun recordPersistsEntryIncludingLocalBinding() = runTest {
        val dao = FakeUploadedFileDao()
        val registry = DefaultUploadedFileRegistry(dao)

        registry.record(entry(localPath = "/data/workspace/简历-abc123.pdf"))

        assertEquals("/data/workspace/简历-abc123.pdf", registry.localPathFor("deepseek", "file-1"))
    }

    @Test
    fun recordIsIdempotentPerFileId() = runTest {
        val dao = FakeUploadedFileDao()
        val registry = DefaultUploadedFileRegistry(dao)
        registry.record(entry())

        registry.record(entry(localPath = "/data/workspace/second.pdf"))

        assertEquals(1, dao.rows.value.size)
        assertEquals("/data/workspace/second.pdf", registry.localPathFor("deepseek", "file-1"))
    }

    @Test
    fun bindLocalFileRewritesBindingAndIgnoresUnknownRecord() = runTest {
        val dao = FakeUploadedFileDao()
        val registry = DefaultUploadedFileRegistry(dao)

        // 记录不存在时无行可改，静默幂等。
        registry.bindLocalFile("deepseek", "file-1", "/missing.pdf")
        assertNull(registry.localPathFor("deepseek", "file-1"))

        registry.record(entry())
        registry.bindLocalFile("deepseek", "file-1", "/data/workspace/new.pdf")

        assertEquals("/data/workspace/new.pdf", registry.localPathFor("deepseek", "file-1"))
    }

    @Test
    fun entriesForFiltersVendorAndSortsByCreationDescending() = runTest {
        val dao = FakeUploadedFileDao()
        val registry = DefaultUploadedFileRegistry(dao)
        registry.record(entry(fileId = "file-old", createdAt = 10L))
        registry.record(entry(fileId = "file-new", createdAt = 20L))
        registry.record(entry(fileId = "other-vendor", vendor = "qwen", createdAt = 30L))

        val entries = registry.entriesFor("deepseek").first()

        assertEquals(listOf("file-new", "file-old"), entries.map { it.fileId })
    }

    @Test
    fun removeDeletesTheMatchingRecord() = runTest {
        val dao = FakeUploadedFileDao()
        val registry = DefaultUploadedFileRegistry(dao)
        registry.record(entry())

        registry.remove("deepseek", "file-1")

        assertEquals(0, dao.rows.value.size)
        assertNull(registry.localPathFor("deepseek", "file-1"))
    }

    private fun entry(
        fileId: String = "file-1",
        vendor: String = "deepseek",
        createdAt: Long = 0L,
        localPath: String? = null,
    ) = UploadedFileEntry(
        fileId = fileId,
        vendor = vendor,
        displayName = "简历.pdf",
        mimeType = "application/pdf",
        bytes = 8L,
        createdAtEpochSeconds = createdAt,
        localPath = localPath,
    )
}

/** 进程内 [UploadedFileDao] 假实现；只覆盖注册表用到的四个操作。 */
private class FakeUploadedFileDao : UploadedFileDao {
    val rows = MutableStateFlow<Map<String, UploadedFileEntity>>(emptyMap())

    override suspend fun upsert(entity: UploadedFileEntity) {
        rows.value = rows.value + (entity.fileId to entity)
    }

    override fun observeForVendor(vendor: String): Flow<List<UploadedFileEntity>> =
        rows.map { current ->
            current.values
                .filter { it.vendor == vendor }
                .sortedByDescending { it.createdAtEpochSeconds }
        }

    override suspend fun localPath(fileId: String, vendor: String): String? =
        rows.value[fileId]?.takeIf { it.vendor == vendor }?.localPath

    override suspend fun bindLocalPath(fileId: String, vendor: String, localPath: String) {
        rows.value[fileId]?.takeIf { it.vendor == vendor }?.let { entity ->
            rows.value = rows.value + (fileId to entity.copy(localPath = localPath))
        }
    }

    override suspend fun delete(fileId: String, vendor: String) {
        rows.value[fileId]?.takeIf { it.vendor == vendor }?.let {
            rows.value = rows.value - fileId
        }
    }
}
