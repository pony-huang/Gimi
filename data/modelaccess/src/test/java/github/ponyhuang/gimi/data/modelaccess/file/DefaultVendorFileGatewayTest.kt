package github.ponyhuang.gimi.data.modelaccess.file

import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileContentResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileListResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObjectResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUpload
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 网关两级分发（vendor → 协议 → 客户端）与删除后的注册表联动。
 */
class DefaultVendorFileGatewayTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(File("."))

    private class RecordingClient(override val protocol: FileProtocol) : FileProtocolClient {
        val uploads = mutableListOf<String>()
        val deletes = mutableListOf<String>()

        override suspend fun upload(
            vendor: String,
            endpoint: String,
            apiKey: String,
            request: VendorFileUpload,
        ): VendorFileUploadResult {
            uploads += vendor
            return VendorFileUploadResult.Uploaded(fileId = "$protocol-id", bytes = 1L, createdAtEpochSeconds = 0L)
        }

        override suspend fun listFiles(
            vendor: String,
            endpoint: String,
            apiKey: String,
            limit: Int?,
            cursor: String?,
        ): VendorFileListResult = VendorFileListResult.Success(files = emptyList())

        override suspend fun getFile(
            vendor: String,
            endpoint: String,
            apiKey: String,
            fileId: String,
        ): VendorFileObjectResult = VendorFileObjectResult.Failure(VendorFileFailureReason.NOT_FOUND)

        override suspend fun deleteFile(
            vendor: String,
            endpoint: String,
            apiKey: String,
            fileId: String,
        ): VendorFileDeleteResult {
            deletes += fileId
            return VendorFileDeleteResult.Success(fileId = fileId, deleted = true)
        }

        override suspend fun getContent(
            vendor: String,
            endpoint: String,
            apiKey: String,
            fileId: String,
        ): VendorFileContentResult = VendorFileContentResult.Failure(VendorFileFailureReason.NOT_FOUND)
    }

    private class RecordingRegistry : UploadedFileRegistry {
        val removed = mutableListOf<Pair<String, String>>()
        override suspend fun record(entry: github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry) = Unit
        override fun entriesFor(vendor: String) = kotlinx.coroutines.flow.flowOf(emptyList<github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry>())
        override suspend fun remove(vendor: String, fileId: String) {
            removed += vendor to fileId
        }
    }

    private val openAiClient = RecordingClient(FileProtocol.OPENAI_COMPATIBLE)
    private val claudeClient = RecordingClient(FileProtocol.ANTHROPIC)
    private val registry = RecordingRegistry()
    private val gateway = DefaultVendorFileGateway(setOf(openAiClient, claudeClient), registry)

    private fun uploadRequest() = VendorFileUpload(
        mimeType = "application/pdf",
        displayName = "doc.pdf",
        // 每次调用建新文件：同一测试内多次 upload 不与 TemporaryFolder 重名。
        filePath = File.createTempFile("doc-", ".pdf", File(temporaryFolder.root, "drafts").apply { mkdirs() }).absolutePath,
    )

    @Test
    fun openAiCompatibleVendorsRouteToSameProtocolClient() = runBlocking {
        // deepseek 与 openai 同属 OpenAI 协议族：仅 endpoint 不同，实现复用。
        listOf("deepseek", "openai").forEach { vendor ->
            val result = gateway.upload(vendor, "https://example.com", "key", uploadRequest())
            assertTrue(result is VendorFileUploadResult.Uploaded)
        }
        assertEquals(listOf("deepseek", "openai"), openAiClient.uploads)
        assertTrue(claudeClient.uploads.isEmpty())
    }

    @Test
    fun anthropicVendorRoutesToClaudeClient() = runBlocking {
        val result = gateway.upload("anthropic", "https://example.com", "key", uploadRequest())
        assertTrue(result is VendorFileUploadResult.Uploaded)
        assertEquals(listOf("anthropic"), claudeClient.uploads)
        assertTrue(openAiClient.uploads.isEmpty())
    }

    @Test
    fun unregisteredVendorFailsWithoutTouchingClients() = runBlocking {
        val result = gateway.upload("kimi", "https://example.com", "key", uploadRequest())
        assertEquals(VendorFileFailureReason.UNSUPPORTED_VENDOR, (result as VendorFileUploadResult.Failure).reason)
        assertTrue(openAiClient.uploads.isEmpty() && claudeClient.uploads.isEmpty())
    }

    @Test
    fun successfulDeleteRemovesRegistryEntry() = runBlocking {
        val result = gateway.deleteFile("deepseek", "https://example.com", "key", "f1")
        assertTrue(result is VendorFileDeleteResult.Success)
        assertEquals(listOf("deepseek" to "f1"), registry.removed)
        assertEquals(listOf("f1"), openAiClient.deletes)
    }
}
