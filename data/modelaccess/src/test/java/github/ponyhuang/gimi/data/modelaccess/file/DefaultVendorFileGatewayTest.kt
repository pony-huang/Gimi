package github.ponyhuang.gimi.data.modelaccess.file

import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileContentResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileListResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObjectResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUpload
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 网关按连接协议选择客户端、透传端点与删除后的注册表联动。
 */
class DefaultVendorFileGatewayTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(File("."))

    private class RecordingClient(override val protocol: FileProtocol) : FileProtocolClient {
        val uploads = mutableListOf<String>()
        val deletes = mutableListOf<String>()
        val endpoints = mutableListOf<String>()

        override suspend fun upload(
            vendor: String,
            endpoint: String,
            apiKey: String,
            request: VendorFileUpload,
        ): VendorFileUploadResult {
            uploads += vendor
            endpoints += endpoint
            return VendorFileUploadResult.Uploaded(fileId = "$protocol-id", bytes = 1L, createdAtEpochSeconds = 0L)
        }

        override suspend fun listFiles(
            vendor: String,
            endpoint: String,
            apiKey: String,
            limit: Int?,
            cursor: String?,
        ): VendorFileListResult {
            endpoints += endpoint
            return VendorFileListResult.Success(files = emptyList())
        }

        override suspend fun getFile(
            vendor: String,
            endpoint: String,
            apiKey: String,
            fileId: String,
        ): VendorFileObjectResult {
            endpoints += endpoint
            return VendorFileObjectResult.Failure(VendorFileFailureReason.NOT_FOUND)
        }

        override suspend fun deleteFile(
            vendor: String,
            endpoint: String,
            apiKey: String,
            fileId: String,
        ): VendorFileDeleteResult {
            deletes += fileId
            endpoints += endpoint
            return VendorFileDeleteResult.Success(fileId = fileId, deleted = true)
        }

        override suspend fun getContent(
            vendor: String,
            endpoint: String,
            apiKey: String,
            fileId: String,
        ): VendorFileContentResult {
            endpoints += endpoint
            return VendorFileContentResult.Failure(VendorFileFailureReason.NOT_FOUND)
        }
    }

    private class RecordingRegistry : UploadedFileRegistry {
        val removed = mutableListOf<Pair<String, String>>()
        override suspend fun record(entry: github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry) = Unit
        override fun entriesFor(vendor: String) = kotlinx.coroutines.flow.flowOf(emptyList<github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry>())
        override suspend fun localPathFor(vendor: String, fileId: String): String? = null
        override suspend fun bindLocalFile(vendor: String, fileId: String, localPath: String) = Unit
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
    fun connectionProtocolSelectsClientForEveryOperationAcrossVendorsAndRelayPaths() = runBlocking {
        val endpoint = "https://relay.example.com/custom/anthropic"
        listOf("deepseek", "openai", "future-vendor").forEach { vendor ->
            gateway.upload(vendor, ApiProtocol.Anthropic, endpoint, "key", uploadRequest())
            gateway.listFiles(vendor, ApiProtocol.Anthropic, endpoint, "key", 20, "f0")
            gateway.getFile(vendor, ApiProtocol.Anthropic, endpoint, "key", "f1")
            gateway.deleteFile(vendor, ApiProtocol.Anthropic, endpoint, "key", "f1")
            gateway.getContent(vendor, ApiProtocol.Anthropic, endpoint, "key", "f1")
        }
        assertEquals(listOf("deepseek", "openai", "future-vendor"), claudeClient.uploads)
        // DeepSeek 无 content 下载端点：getContent 不达客户端（4+5+5=14），其余厂商透传。
        assertEquals(List(14) { endpoint }, claudeClient.endpoints)
        assertTrue(openAiClient.endpoints.isEmpty())
    }

    @Test
    fun deepseekContentDownloadIsGatedAsUnsupportedWithoutNetwork() = runBlocking {
        val result = gateway.getContent("deepseek", ApiProtocol.Anthropic, "https://api.deepseek.com", "key", "f1")

        assertTrue(result is VendorFileContentResult.Failure)
        assertEquals(VendorFileFailureReason.UNSUPPORTED_VENDOR, (result as VendorFileContentResult.Failure).reason)
        assertTrue(claudeClient.endpoints.isEmpty())
    }

    @Test
    fun sameVendorCanUseEitherConfiguredProtocolWithoutRewritingEndpoint() = runBlocking {
        val endpoint = "https://api.deepseek.com/anthropic"
        gateway.upload("deepseek", ApiProtocol.Anthropic, endpoint, "key", uploadRequest())
        gateway.upload("deepseek", ApiProtocol.OpenaiChatcompletions, endpoint, "key", uploadRequest())
        assertEquals(listOf(endpoint), claudeClient.endpoints)
        assertEquals(listOf(endpoint), openAiClient.endpoints)
    }

    @Test
    fun unsupportedProtocolFailsWithoutTouchingClients() = runBlocking {
        val result = gateway.upload("deepseek", ApiProtocol.Gemini, "https://example.com", "key", uploadRequest())
        assertEquals(VendorFileFailureReason.UNSUPPORTED_VENDOR, (result as VendorFileUploadResult.Failure).reason)
        assertTrue(openAiClient.endpoints.isEmpty() && claudeClient.endpoints.isEmpty())
    }

    @Test
    fun missingProtocolClientFailsWithoutUsingAnotherClient() = runBlocking {
        val incompleteGateway = DefaultVendorFileGateway(setOf(openAiClient), registry)
        val result = incompleteGateway.upload("deepseek", ApiProtocol.Anthropic, "https://example.com", "key", uploadRequest())
        assertEquals(VendorFileFailureReason.UNSUPPORTED_VENDOR, (result as VendorFileUploadResult.Failure).reason)
        assertTrue(openAiClient.endpoints.isEmpty())
    }

    @Test
    fun successfulDeleteRemovesRegistryEntry() = runBlocking {
        val result = gateway.deleteFile("deepseek", ApiProtocol.Anthropic, "https://example.com", "key", "f1")
        assertTrue(result is VendorFileDeleteResult.Success)
        assertEquals(listOf("deepseek" to "f1"), registry.removed)
        assertEquals(listOf("f1"), claudeClient.deletes)
    }
}
