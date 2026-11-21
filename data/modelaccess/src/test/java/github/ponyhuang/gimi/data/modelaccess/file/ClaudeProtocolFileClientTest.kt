package github.ponyhuang.gimi.data.modelaccess.file

import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileListResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObjectResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUpload
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Anthropic Files 协议客户端：无 purpose 的 multipart、x-api-key/版本头、/v1 路径补齐、
 * page 游标映射、RFC 3339 时间解析与删除语义。
 */
class ClaudeProtocolFileClientTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(File("."))

    private lateinit var server: MockWebServer
    private lateinit var client: ClaudeProtocolFileClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ClaudeProtocolFileClient(temporaryFolder.newFolder("downloads"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun draftFile(name: String = "doc.pdf"): File =
        temporaryFolder.newFile(name).apply { writeBytes(ByteArray(16) { it.toByte() }) }

    private fun request(file: File, mime: String = "application/pdf", displayName: String = file.name) =
        VendorFileUpload(
            mimeType = mime,
            displayName = displayName,
            filePath = file.absolutePath,
            // 该协议无 purpose 概念；实现应忽略 hint 而不是报错。
            purposeHint = "fine-tune",
        )

    private fun base(): String = server.url("/").toString().trimEnd('/')

    /** 官方基地址不含 /v1：客户端应自动补 v1 段。 */
    private fun fileMetadataBody(): String =
        "{\"type\":\"file\",\"id\":\"file_01\",\"filename\":\"doc.pdf\",\"mime_type\":\"application/pdf\"," +
            "\"size_bytes\":16,\"created_at\":\"2025-04-14T12:00:00Z\",\"downloadable\":false," +
            "\"scope\":{\"type\":\"session\",\"id\":\"sess_1\"}}"

    @Test
    fun deepSeekUploadUsesConfiguredAnthropicBaseAndDocumentPurpose() = runBlocking {
        server.enqueue(MockResponse().setBody(fileMetadataBody()))
        val result = client.upload(
            "deepseek",
            server.url("/relay/anthropic").toString(),
            "key",
            request(draftFile()).copy(purposeHint = null),
        )
        assertTrue("result=$result", result is VendorFileUploadResult.Uploaded)
        assertEquals(16L, (result as VendorFileUploadResult.Uploaded).bytes)
        assertEquals(1744632000L, result.createdAtEpochSeconds)
        val recorded = server.takeRequest()
        assertEquals("/relay/anthropic/v1/files", recorded.path)
        assertEquals("key", recorded.getHeader("x-api-key"))
        assertEquals("files-api-2025-04-14", recorded.getHeader("anthropic-beta"))
        val body = recorded.body.readUtf8()
        val purposeIndex = body.indexOf("name=\"purpose\"")
        val fileIndex = body.indexOf("name=\"file\"")
        assertTrue("body=$body", purposeIndex in 0 until fileIndex)
        assertTrue(body.contains("rich_layout_recognition"))
    }

    @Test
    fun deepSeekUploadUsesUserDataForTextAndHonorsExplicitPurpose() = runBlocking {
        val file = draftFile("notes.txt")
        listOf(null to "user_data", "text_recognition" to "text_recognition").forEach { (hint, expected) ->
            server.enqueue(MockResponse().setBody(fileMetadataBody()))
            val result = client.upload(
                "deepseek", base(), "key", request(file, "text/plain").copy(purposeHint = hint),
            )
            assertTrue(result is VendorFileUploadResult.Uploaded)
            val body = server.takeRequest().body.readUtf8()
            assertTrue("body=$body", body.contains("\r\n$expected\r\n"))
        }
    }

    @Test
    fun deepSeekListUsesAfterIdAndLastIdWhileFileOperationsSendBetaHeader() = runBlocking {
        val endpoint = server.url("/anthropic/v1/").toString()
        server.enqueue(MockResponse().setBody(
            "{\"data\":[{\"id\":\"f1\"}],\"has_more\":true,\"last_id\":\"f1\"}",
        ))
        val listed = client.listFiles("deepseek", endpoint, "key", 2, "f0")
        assertTrue(listed is VendorFileListResult.Success)
        assertEquals("f1", (listed as VendorFileListResult.Success).nextCursor)
        val listRequest = server.takeRequest()
        assertEquals("/anthropic/v1/files?limit=2&after_id=f0", listRequest.path)
        assertEquals("files-api-2025-04-14", listRequest.getHeader("anthropic-beta"))

        server.enqueue(MockResponse().setBody("{\"data\":[],\"has_more\":false,\"last_id\":\"f1\"}"))
        val lastPage = client.listFiles("deepseek", endpoint, "key", null, "f1")
        assertNull((lastPage as VendorFileListResult.Success).nextCursor)
        server.takeRequest()

        server.enqueue(MockResponse().setBody(fileMetadataBody()))
        assertTrue(client.getFile("deepseek", endpoint, "key", "f1") is VendorFileObjectResult.Success)
        server.enqueue(MockResponse().setBody("{\"id\":\"f1\",\"type\":\"file_deleted\"}"))
        client.deleteFile("deepseek", endpoint, "key", "f1")
        server.enqueue(MockResponse().setResponseCode(404))
        client.getContent("deepseek", endpoint, "key", "f1")
        repeat(3) {
            assertEquals("files-api-2025-04-14", server.takeRequest().getHeader("anthropic-beta"))
        }
    }

    @Test
    fun uploadOmitsPurposeAndSendsAnthropicHeadersUnderV1Path() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(fileMetadataBody()),
        )
        val result = client.upload("anthropic", base(), "key", request(draftFile()))
        assertTrue("result=$result", result is VendorFileUploadResult.Uploaded)
        result as VendorFileUploadResult.Uploaded
        assertEquals("file_01", result.fileId)
        assertEquals(16L, result.bytes)
        // RFC 3339 → Unix 秒（2025-04-14T12:00:00Z）。
        assertEquals(1744632000L, result.createdAtEpochSeconds)

        val recorded = server.takeRequest()
        assertEquals("/v1/files", recorded.path)
        assertEquals("key", recorded.getHeader("x-api-key"))
        assertTrue(recorded.getHeader("anthropic-version").orEmpty().isNotEmpty())
        // 该协议无 purpose：表单里不得出现 purpose 字段（协议解析器不认识它）。
        val body = recorded.body.readUtf8()
        assertFalse("body=$body", body.contains("name=\"purpose\""))
        assertTrue(body.contains("name=\"file\""))
    }

    @Test
    fun endpointAlreadyEndingWithV1IsNotDuplicated() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(fileMetadataBody()))
        val v1Base = server.url("/v1").toString().trimEnd('/')
        val result = client.upload("anthropic", v1Base, "key", request(draftFile()))
        assertTrue("result=$result", result is VendorFileUploadResult.Uploaded)
        assertEquals("/v1/files", server.takeRequest().path)
    }

    @Test
    fun chineseDisplayNameIsSanitizedInMultipartHeader() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(fileMetadataBody()))
        val result = client.upload(
            "anthropic",
            base(),
            "key",
            request(draftFile("doc.pdf"), displayName = "季度财报.pdf"),
        )
        assertTrue("result=$result", result is VendorFileUploadResult.Uploaded)
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("季度"))
        assertTrue(body.contains(".pdf"))
    }

    @Test
    fun listMapsNextPageToNextCursor() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                "{\"data\":[" +
                    "{\"id\":\"file_a\",\"filename\":\"a.csv\",\"mime_type\":\"text/csv\",\"size_bytes\":10," +
                    "\"created_at\":\"2025-04-14T12:00:00Z\"}," +
                    "{\"id\":\"file_b\",\"filename\":\"b.pdf\",\"mime_type\":\"application/pdf\",\"size_bytes\":20," +
                    "\"created_at\":\"2025-04-14T13:00:00Z\"}" +
                    "],\"next_page\":\"page_2\",\"has_more\":true}",
            ),
        )
        val result = client.listFiles("anthropic", base(), "key", limit = 2, cursor = "page_1")
        assertTrue("result=$result", result is VendorFileListResult.Success)
        result as VendorFileListResult.Success
        assertEquals(listOf("file_a", "file_b"), result.files.map { it.id })
        // mime_type 进统一模型；scope/downloadable 等特有字段走 raw 兜底。
        assertEquals("text/csv", result.files[0].mimeType)
        assertEquals(10L, result.files[0].sizeBytes)
        assertEquals("page_2", result.nextCursor)

        val recorded = server.takeRequest()
        assertEquals("/v1/files?limit=2&page=page_1", recorded.path)
    }

    @Test
    fun listAtLastPageReturnsNullCursor() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[{\"id\":\"file_a\"}],\"next_page\":null}"),
        )
        val result = client.listFiles("anthropic", base(), "key", limit = null, cursor = null)
        assertTrue(result is VendorFileListResult.Success)
        assertNull((result as VendorFileListResult.Success).nextCursor)
    }

    @Test
    fun getFileMapsMetadataFields() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(fileMetadataBody()))
        val result = client.getFile("anthropic", base(), "key", "file_01")
        assertTrue("result=$result", result is VendorFileObjectResult.Success)
        val file = (result as VendorFileObjectResult.Success).file
        assertEquals("file_01", file.id)
        assertEquals("doc.pdf", file.filename)
        assertEquals("application/pdf", file.mimeType)
        assertEquals(16L, file.sizeBytes)
        assertEquals(1744632000L, file.createdAtEpochSeconds)
        assertTrue(file.raw?.containsKey("scope") == true)
        assertEquals("/v1/files/file_01", server.takeRequest().path)
    }

    @Test
    fun deleteMapsFileDeletedResponseToSuccess() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"file_01\",\"type\":\"file_deleted\"}"),
        )
        val result = client.deleteFile("anthropic", base(), "key", "file_01")
        assertTrue(result is github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult.Success)
        val success = result as github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult.Success
        assertEquals("file_01", success.fileId)
        assertTrue(success.deleted)
        assertEquals("/v1/files/file_01", server.takeRequest().path)
    }

    @Test
    fun deleteRejectsSuccessfulResponseWithoutFileDeletedType() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody("{}"),
        )
        val result = client.deleteFile("anthropic", base(), "key", "file_01")
        val failure = result as github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult.Failure
        assertEquals(VendorFileFailureReason.VENDOR_ERROR, failure.reason)
        assertEquals("/v1/files/file_01", server.takeRequest().path)
    }

    @Test
    fun http404MapsToNotFound() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.getFile("anthropic", base(), "key", "missing")
        assertEquals(VendorFileFailureReason.NOT_FOUND, (result as VendorFileObjectResult.Failure).reason)
    }

    @Test
    fun http500MapsToVendorError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val result = client.listFiles("anthropic", base(), "key", limit = null, cursor = null)
        assertEquals(VendorFileFailureReason.VENDOR_ERROR, (result as VendorFileListResult.Failure).reason)
    }

    @Test
    fun missingDraftFileMapsToNetworkFailure() = runBlocking {
        val result = client.upload(
            "anthropic",
            base(),
            "key",
            request(File(temporaryFolder.root, "gone.pdf")),
        )
        assertEquals(VendorFileFailureReason.NETWORK, (result as VendorFileUploadResult.Failure).reason)
    }
}
