package github.ponyhuang.gimi.data.conversation.attachment

import github.ponyhuang.gimi.data.conversation.testing.FakeUploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.conversation.repository.VendorAttachmentResolution
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileContentResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileListResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObjectResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUpload
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [resolveVendorAttachmentPayload] 的解析顺序与下载兜底行为：绑定命中离线直开、
 * 下载成功归档进工作区并回写绑定、失败原因透传。
 */
class VendorAttachmentPayloadResolveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val registry = FakeUploadedFileRegistry()

    @Test
    fun resolvesBoundWorkspaceFileWithoutVendorRoundTrip() = runTest {
        val bytes = "bound-pdf".toByteArray()
        val bound = temporaryFolder.newFile("简历-abc123def456.pdf").apply { writeBytes(bytes) }
        registry.record(entry(localPath = bound.absolutePath))
        val gateway = FakeVendorFileGateway { _, _ -> error("must not be called") }

        val resolution = resolveVendorAttachmentPayload(
            attachment = vendorAttachment(),
            connection = connection(),
            workspaceRoot = temporaryFolder.newFolder("workspace"),
            registry = registry,
            gateway = gateway,
            extension = "pdf",
        )

        val resolved = resolution as VendorAttachmentResolution.Resolved
        assertEquals(bound.absolutePath, resolved.attachment.payloadReference)
        assertEquals(bytes.size.toLong(), resolved.attachment.sizeBytes)
        assertEquals(0, gateway.contentCalls)
    }

    @Test
    fun failsWithoutConnectionWhenNoUsableBinding() = runTest {
        val gateway = FakeVendorFileGateway { _, _ -> error("must not be called") }

        val resolution = resolveVendorAttachmentPayload(
            attachment = vendorAttachment(),
            connection = null,
            workspaceRoot = temporaryFolder.newFolder("workspace"),
            registry = registry,
            gateway = gateway,
            extension = "pdf",
        )

        assertEquals(
            VendorFileFailureReason.VENDOR_ERROR,
            (resolution as VendorAttachmentResolution.Failed).reason,
        )
        assertEquals(0, gateway.contentCalls)
    }

    @Test
    fun downloadsArchivesAndBindsWhenBindingMissing() = runTest {
        val workspace = temporaryFolder.newFolder("workspace")
        val bytes = "downloaded-pdf".toByteArray()
        val download = temporaryFolder.newFile("vendor-file-1").apply { writeBytes(bytes) }
        val gateway = FakeVendorFileGateway { _, _ ->
            VendorFileContentResult.Success(
                fileId = "file-1",
                localPath = download.absolutePath,
                mimeType = "application/pdf",
                bytes = bytes.size.toLong(),
            )
        }
        // 生产路径上上传成功即已写入注册表记录（绑定悬空对应"记录在、路径失效"），
        // 这里播种无绑定的记录，绑定由下载兜底回写。
        registry.record(entry())

        val resolution = resolveVendorAttachmentPayload(
            attachment = vendorAttachment(),
            connection = connection(),
            workspaceRoot = workspace,
            registry = registry,
            gateway = gateway,
            extension = "pdf",
        )

        val resolved = resolution as VendorAttachmentResolution.Resolved
        val archived = File(requireNotNull(resolved.attachment.payloadReference))
        assertEquals(workspace.canonicalFile, archived.parentFile.canonicalFile)
        assertTrue(archived.name.startsWith("简历-"))
        assertTrue(archived.name.endsWith(".pdf"))
        assertArrayEquals(bytes, archived.readBytes())
        assertEquals(bytes.size.toLong(), resolved.attachment.sizeBytes)
        // 网关临时文件由调用方释放，绑定回写后下次打开离线直开。
        assertFalse(download.exists())
        assertEquals(archived.absolutePath, registry.localPathFor("deepseek", "file-1"))
        assertEquals(1, gateway.contentCalls)
        assertEquals("https://api.deepseek.test", gateway.lastEndpoint)
        assertEquals("file-1", gateway.lastFileId)
    }

    @Test
    fun passesThroughVendorFailureReason() = runTest {
        val gateway = FakeVendorFileGateway { _, _ ->
            VendorFileContentResult.Failure(VendorFileFailureReason.NOT_FOUND)
        }

        val resolution = resolveVendorAttachmentPayload(
            attachment = vendorAttachment(),
            connection = connection(),
            workspaceRoot = temporaryFolder.newFolder("workspace"),
            registry = registry,
            gateway = gateway,
            extension = "pdf",
        )

        assertEquals(
            VendorFileFailureReason.NOT_FOUND,
            (resolution as VendorAttachmentResolution.Failed).reason,
        )
    }

    @Test
    fun keepsUnsupportedVendorReasonForProtocolGaps() = runTest {
        val gateway = FakeVendorFileGateway { _, _ ->
            VendorFileContentResult.Failure(VendorFileFailureReason.UNSUPPORTED_VENDOR)
        }

        val resolution = resolveVendorAttachmentPayload(
            attachment = vendorAttachment(),
            connection = connection(),
            workspaceRoot = temporaryFolder.newFolder("workspace"),
            registry = registry,
            gateway = gateway,
            extension = "pdf",
        )

        assertEquals(
            VendorFileFailureReason.UNSUPPORTED_VENDOR,
            (resolution as VendorAttachmentResolution.Failed).reason,
        )
    }

    @Test
    fun failsWhenVendorAttachmentLacksVendorReference() = runTest {
        val gateway = FakeVendorFileGateway { _, _ -> error("must not be called") }

        val resolution = resolveVendorAttachmentPayload(
            attachment = FileAttachment(
                mimeType = "application/pdf",
                id = "local-1",
                sizeBytes = 1L,
                displayName = "local.pdf",
                payloadReference = "/nonexistent/local.pdf",
            ),
            connection = connection(),
            workspaceRoot = temporaryFolder.newFolder("workspace"),
            registry = registry,
            gateway = gateway,
            extension = "pdf",
        )

        assertEquals(
            VendorFileFailureReason.VENDOR_ERROR,
            (resolution as VendorAttachmentResolution.Failed).reason,
        )
        assertEquals(0, gateway.contentCalls)
    }

    @Test
    fun failsWhenDownloadedPayloadIsMissing() = runTest {
        val gateway = FakeVendorFileGateway { _, _ ->
            VendorFileContentResult.Success(
                fileId = "file-1",
                localPath = File(temporaryFolder.root, "gone/vendor-file-1").absolutePath,
                mimeType = "application/pdf",
                bytes = 4L,
            )
        }

        val resolution = resolveVendorAttachmentPayload(
            attachment = vendorAttachment(),
            connection = connection(),
            workspaceRoot = temporaryFolder.newFolder("workspace"),
            registry = registry,
            gateway = gateway,
            extension = "pdf",
        )

        assertEquals(
            VendorFileFailureReason.VENDOR_ERROR,
            (resolution as VendorAttachmentResolution.Failed).reason,
        )
        assertNull(registry.localPathFor("deepseek", "file-1"))
    }

    @Test
    fun archivesDownloadedTempFileIntoWorkspaceAndReleasesTemp() {
        val workspace = temporaryFolder.newFolder("workspace")
        val bytes = "temp-payload".toByteArray()
        val temp = temporaryFolder.newFile("vendor-file-2").apply { writeBytes(bytes) }

        val archived = archiveDownloadedVendorFile(
            downloaded = VendorFileContentResult.Success(
                fileId = "file-2",
                localPath = temp.absolutePath,
                mimeType = "application/pdf",
                bytes = bytes.size.toLong(),
            ),
            displayName = "Java 后端工程师.pdf",
            workspaceRoot = workspace,
            extension = "pdf",
        )

        requireNotNull(archived)
        assertTrue(archived.name.startsWith("Java 后端工程师-"))
        assertArrayEquals(bytes, archived.readBytes())
        assertFalse(temp.exists())
        assertEquals(1, workspace.listFiles()?.size)
    }

    @Test
    fun archiveReturnsNullForMissingDownload() {
        val archived = archiveDownloadedVendorFile(
            downloaded = VendorFileContentResult.Success(
                fileId = "file-3",
                localPath = File(temporaryFolder.root, "gone/vendor-file-3").absolutePath,
                mimeType = "application/pdf",
                bytes = 4L,
            ),
            displayName = "missing.pdf",
            workspaceRoot = temporaryFolder.newFolder("workspace"),
            extension = "pdf",
        )

        assertNull(archived)
    }

    private fun vendorAttachment() = FileAttachment.fromVendorReference(
        vendor = "deepseek",
        fileId = "file-1",
        mimeType = "application/pdf",
        displayName = "简历.pdf",
    )

    private fun connection() = ResolvedServiceConnection(
        instanceId = "inst-1",
        templateId = "deepseek",
        connectionId = "conn-1",
        capability = ServiceCapability.Conversation,
        protocol = ApiProtocol.OpenaiChatcompletions,
        endpoint = "https://api.deepseek.test/",
        apiKey = "key",
        workspaceId = null,
        model = Model(id = "deepseek-chat", name = "DeepSeek Chat"),
    )

    private fun entry(localPath: String? = null) = UploadedFileEntry(
        fileId = "file-1",
        vendor = "deepseek",
        displayName = "简历.pdf",
        mimeType = "application/pdf",
        bytes = 8L,
        createdAtEpochSeconds = 0L,
        localPath = localPath,
    )
}

/** 进程内 [VendorFileGateway] 假实现；只有 [getContent] 可配置，其余操作不许被调用。 */
private class FakeVendorFileGateway(
    private val content: (fileId: String, endpoint: String) -> VendorFileContentResult,
) : VendorFileGateway {
    var contentCalls = 0
        private set
    var lastEndpoint: String? = null
        private set
    var lastFileId: String? = null
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
    ): VendorFileListResult = error("not used")

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
    ): VendorFileContentResult {
        contentCalls++
        lastEndpoint = endpoint
        lastFileId = fileId
        return content(fileId, endpoint)
    }
}
