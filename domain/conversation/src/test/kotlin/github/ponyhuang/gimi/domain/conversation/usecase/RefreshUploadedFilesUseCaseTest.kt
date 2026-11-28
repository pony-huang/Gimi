package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileListResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObject
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObjectResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileContentResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUpload
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RefreshUploadedFilesUseCase] 的对账行为：厂商新增写回（mime 推断不出跳过）、
 * 本地悬空记录移除、既有条目与绑定保留、翻页拉全量、失败中止不动本地。
 */
class RefreshUploadedFilesUseCaseTest {

    @Test
    fun addsVendorOnlyFilesAndSkipsUnresolvableMime() = runTest {
        val registry = FakeUploadedFileRegistry()
        val gateway = FakeVendorFileGateway(
            pages = listOf(
                VendorFileListResult.Success(
                    files = listOf(
                        vendorFile(id = "file-1", filename = "季度报表.docx", bytes = 2048),
                        // 扩展名推不出类别：不收，避免复用挂载与图标渲染失败。
                        vendorFile(id = "file-2", filename = "payload.xyz", bytes = 10),
                        vendorFile(id = "file-3", filename = null, bytes = 10),
                    ),
                    nextCursor = null,
                ),
            ),
        )

        val result = RefreshUploadedFilesUseCase(gateway, registry)(vendor, connection())

        assertEquals(UploadedFilesRefreshResult.Synced(added = 1, removed = 0), result)
        val entries = registry.all.toList()
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("file-1", entry.fileId)
        assertEquals("季度报表.docx", entry.displayName)
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            entry.mimeType,
        )
        assertEquals(2048L, entry.bytes)
        assertNull(entry.localPath)
    }

    @Test
    fun removesLocalRecordsMissingFromVendor() = runTest {
        val registry = FakeUploadedFileRegistry()
        registry.record(entry(fileId = "file-stale", localPath = "/gone.pdf"))
        registry.record(entry(fileId = "file-keep", localPath = "/keep.pdf"))
        val gateway = FakeVendorFileGateway(
            pages = listOf(
                VendorFileListResult.Success(files = listOf(vendorFile(id = "file-keep")), nextCursor = null),
            ),
        )

        val result = RefreshUploadedFilesUseCase(gateway, registry)(vendor, connection())

        assertEquals(UploadedFilesRefreshResult.Synced(added = 0, removed = 1), result)
        val remaining = registry.all.toList()
        assertEquals(listOf("file-keep"), remaining.map { it.fileId })
        assertEquals("/keep.pdf", remaining.single().localPath)
    }

    @Test
    fun keepsExistingEntriesUntouchedWithBinding() = runTest {
        val registry = FakeUploadedFileRegistry()
        val existing = entry(fileId = "file-1", localPath = "/workspace/绑定.pdf")
        registry.record(existing)
        val gateway = FakeVendorFileGateway(
            pages = listOf(
                // 厂商列表元数据与本地不同（此处 bytes 更大），既有条目应原样保留。
                VendorFileListResult.Success(files = listOf(vendorFile(id = "file-1", bytes = 999_999)), nextCursor = null),
            ),
        )

        val result = RefreshUploadedFilesUseCase(gateway, registry)(vendor, connection())

        assertEquals(UploadedFilesRefreshResult.Synced(added = 0, removed = 0), result)
        assertEquals(listOf(existing), registry.all.toList())
    }

    @Test
    fun paginatesThroughAllPagesBeforeReconcile() = runTest {
        val registry = FakeUploadedFileRegistry()
        registry.record(entry(fileId = "file-stale"))
        val gateway = FakeVendorFileGateway(
            pages = listOf(
                VendorFileListResult.Success(
                    files = listOf(vendorFile(id = "file-1")),
                    nextCursor = "cursor-2",
                ),
                VendorFileListResult.Success(
                    files = listOf(vendorFile(id = "file-2")),
                    nextCursor = null,
                ),
            ),
        )

        val result = RefreshUploadedFilesUseCase(gateway, registry)(vendor, connection())

        assertEquals(UploadedFilesRefreshResult.Synced(added = 2, removed = 1), result)
        assertEquals(listOf("file-1", "file-2"), registry.all.map { it.fileId }.sorted())
        assertEquals("https://api.deepseek.test", gateway.lastEndpoint)
        assertEquals(2, gateway.pageCalls)
    }

    @Test
    fun failsWithoutReconcileWhenListPageFails() = runTest {
        val registry = FakeUploadedFileRegistry()
        registry.record(entry(fileId = "file-1"))
        val gateway = FakeVendorFileGateway(
            pages = listOf(
                VendorFileListResult.Success(files = listOf(vendorFile(id = "file-1")), nextCursor = "cursor-2"),
                VendorFileListResult.Failure(VendorFileFailureReason.NETWORK),
            ),
        )

        val result = RefreshUploadedFilesUseCase(gateway, registry)(vendor, connection())

        assertEquals(UploadedFilesRefreshResult.Failed(VendorFileFailureReason.NETWORK), result)
        // 任一页失败即中止：本地记录保持不动，不把缺页误判成厂商已删。
        assertEquals(listOf("file-1"), registry.all.map { it.fileId })
    }

    private val vendor = "deepseek"

    private fun vendorFile(
        id: String,
        filename: String? = "file.pdf",
        bytes: Long? = 8L,
    ) = VendorFileObject(
        id = id,
        filename = filename,
        sizeBytes = bytes,
        createdAtEpochSeconds = 100L,
    )

    private fun entry(fileId: String, localPath: String? = null) = UploadedFileEntry(
        fileId = fileId,
        vendor = vendor,
        displayName = "$fileId.pdf",
        mimeType = "application/pdf",
        bytes = 8L,
        createdAtEpochSeconds = 0L,
        localPath = localPath,
    )

    private fun connection() = ResolvedServiceConnection(
        instanceId = "inst-1",
        templateId = vendor,
        connectionId = "conn-1",
        capability = ServiceCapability.Conversation,
        protocol = ApiProtocol.OpenaiChatcompletions,
        endpoint = "https://api.deepseek.test/",
        apiKey = "key",
        workspaceId = null,
        model = Model(id = "deepseek-chat", name = "DeepSeek Chat"),
    )
}

/** 进程内 [UploadedFileRegistry] 假实现；只覆盖刷新用例用到的操作。 */
private class FakeUploadedFileRegistry : UploadedFileRegistry {
    private val entries = MutableStateFlow<Map<String, UploadedFileEntry>>(emptyMap())

    val all: Collection<UploadedFileEntry> get() = entries.value.values

    override suspend fun record(entry: UploadedFileEntry) {
        entries.value = entries.value + (entry.fileId to entry)
    }

    override fun entriesFor(vendor: String): Flow<List<UploadedFileEntry>> =
        entries.map { current -> current.values.filter { it.vendor == vendor } }

    override suspend fun localPathFor(vendor: String, fileId: String): String? =
        entries.value[fileId]?.takeIf { it.vendor == vendor }?.localPath

    override suspend fun bindLocalFile(vendor: String, fileId: String, localPath: String) {
        entries.value[fileId]?.takeIf { it.vendor == vendor }?.let { entity ->
            entries.value = entries.value + (fileId to entity.copy(localPath = localPath))
        }
    }

    override suspend fun remove(vendor: String, fileId: String) {
        entries.value[fileId]?.takeIf { it.vendor == vendor }?.let {
            entries.value = entries.value - fileId
        }
    }
}

/** [VendorFileGateway] 假实现；只有 [listFiles] 生效，按预置页序依次返回。 */
private class FakeVendorFileGateway(
    private val pages: List<VendorFileListResult>,
) : VendorFileGateway {
    var pageCalls = 0
        private set
    var lastEndpoint: String? = null
        private set

    override suspend fun upload(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        request: VendorFileUpload,
    ): VendorFileUploadResult = error("not used")

    override suspend fun listFiles(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        limit: Int?,
        cursor: String?,
    ): VendorFileListResult {
        pageCalls++
        lastEndpoint = endpoint
        return pages[pageCalls - 1]
    }

    override suspend fun getFile(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileObjectResult = error("not used")

    override suspend fun deleteFile(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileDeleteResult = error("not used")

    override suspend fun getContent(
        vendor: String,
        protocol: ApiProtocol,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileContentResult = error("not used")
}
