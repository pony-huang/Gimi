package github.ponyhuang.gimi.data.modelaccess.file

import github.ponyhuang.gimi.domain.conversation.repository.VendorFileContentResult
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
 * OpenAI Files 协议客户端（含 DeepSeek 兼容路径）：multipart 字段顺序、purpose 映射、
 * 文件名清洗、错误归类、游标分页映射与统一 DTO 装配。
 */
class OpenAiProtocolFileClientTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(File("."))

    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiProtocolFileClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenAiProtocolFileClient(temporaryFolder.newFolder("downloads"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun draftFile(name: String = "doc.pdf"): File =
        temporaryFolder.newFile(name).apply { writeBytes(ByteArray(16) { it.toByte() }) }

    private fun request(
        file: File,
        mime: String = "application/pdf",
        displayName: String = file.name,
        purposeHint: String? = null,
    ) = VendorFileUpload(
        mimeType = mime,
        displayName = displayName,
        filePath = file.absolutePath,
        purposeHint = purposeHint,
    )

    private fun success(): MockResponse = MockResponse().setHeader("Content-Type", "application/json")
        .setBody("{\"id\":\"file-1\",\"bytes\":16,\"created_at\":1700000000}")

    private fun base(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun successCarriesPurposeBeforeFileAndReturnsParsedFields() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                "{\"id\":\"file-abc\",\"object\":\"file\",\"bytes\":16,\"created_at\":1700000000," +
                    "\"filename\":\"doc.pdf\",\"purpose\":\"rich_layout_recognition\"}",
            ),
        )
        val result = client.upload("deepseek", base(), "key", request(draftFile()))
        assertTrue("result=$result", result is VendorFileUploadResult.Uploaded)
        result as VendorFileUploadResult.Uploaded
        assertEquals("file-abc", result.fileId)
        assertEquals(16L, result.bytes)
        assertEquals(1700000000L, result.createdAtEpochSeconds)

        val recorded = server.takeRequest()
        assertEquals("/files", recorded.path)
        assertEquals("Bearer key", recorded.getHeader("Authorization"))
        assertTrue(
            "File uploads require multipart/form-data",
            recorded.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="),
        )
        // 厂商要求 purpose 字段先于 file 字段。
        val body = recorded.body.readUtf8()
        val purposeIndex = body.indexOf("name=\"purpose\"")
        val fileIndex = body.indexOf("name=\"file\"")
        assertTrue("purpose=$purposeIndex file=$fileIndex", purposeIndex in 0 until fileIndex)
        assertTrue(body.contains("rich_layout_recognition"))
    }

    @Test
    fun chineseDisplayNameIsSanitizedInMultipartHeader() = runBlocking {
        server.enqueue(success())
        val result = client.upload(
            "deepseek",
            base(),
            "key",
            request(draftFile("doc.pdf"), displayName = "季度财报.pdf"),
        )
        assertTrue("result=$result", result is VendorFileUploadResult.Uploaded)
        val body = server.takeRequest().body.readUtf8()
        // 非 ASCII 文件名会让厂商的 multipart 解析 400：头里只允许出现 ASCII 安全名。
        assertFalse("body=$body", body.contains("季度"))
        assertTrue(body.contains(".pdf"))
    }

    @Test
    fun deepSeekLayoutMimeMapsToRichLayoutRecognitionAndOthersToUserData() = runBlocking {
        server.enqueue(success())
        client.upload("deepseek", base(), "key", request(draftFile("a.csv"), "text/csv"))
        assertTrue(server.takeRequest().body.readUtf8().contains("user_data"))
    }

    @Test
    fun nonDeepSeekVendorDefaultsToUserData() = runBlocking {
        server.enqueue(success())
        client.upload("openai", base(), "key", request(draftFile()))
        assertTrue(server.takeRequest().body.readUtf8().contains("user_data"))
    }

    @Test
    fun purposeHintOverridesVendorDefaultMapping() = runBlocking {
        server.enqueue(success())
        client.upload("openai", base(), "key", request(draftFile("b.jsonl"), "application/jsonl", purposeHint = "fine-tune"))
        val body = server.takeRequest().body.readUtf8()
        assertTrue("body=$body", body.contains("name=\"purpose\""))
        assertTrue(body.contains("fine-tune"))
    }

    @Test
    fun http413MapsToTooLarge() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(413))
        val result = client.upload("deepseek", base(), "key", request(draftFile()))
        assertEquals(VendorFileFailureReason.TOO_LARGE, (result as VendorFileUploadResult.Failure).reason)
    }

    @Test
    fun otherHttpErrorsMapToVendorError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{\"error\":{\"message\":\"bad\"}}"))
        val result = client.upload("deepseek", base(), "key", request(draftFile()))
        assertEquals(VendorFileFailureReason.VENDOR_ERROR, (result as VendorFileUploadResult.Failure).reason)
    }

    @Test
    fun missingDraftFileMapsToNetworkFailure() = runBlocking {
        val result = client.upload(
            "deepseek",
            base(),
            "key",
            request(File(temporaryFolder.root, "gone.pdf")),
        )
        assertEquals(VendorFileFailureReason.NETWORK, (result as VendorFileUploadResult.Failure).reason)
    }

    @Test
    fun listMapsHasMoreAndLastIdToNextCursor() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                "{\"object\":\"list\",\"data\":[" +
                    "{\"id\":\"f1\",\"bytes\":10,\"created_at\":1700000001,\"filename\":\"a.csv\"}," +
                    "{\"id\":\"f2\",\"bytes\":20,\"created_at\":1700000002,\"filename\":\"b.pdf\"}" +
                    "],\"has_more\":true,\"last_id\":\"f2\"}",
            ),
        )
        val result = client.listFiles("deepseek", base(), "key", limit = 2, cursor = "f0")
        assertTrue("result=$result", result is VendorFileListResult.Success)
        result as VendorFileListResult.Success
        assertEquals(listOf("f1", "f2"), result.files.map { it.id })
        assertEquals("f2", result.nextCursor)

        val recorded = server.takeRequest()
        assertEquals("/files?limit=2&after=f0", recorded.path)
    }

    @Test
    fun listWithoutMoreReturnsNullCursor() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                "{\"object\":\"list\",\"data\":[{\"id\":\"f1\"}],\"has_more\":false}",
            ),
        )
        val result = client.listFiles("deepseek", base(), "key", limit = null, cursor = null)
        assertNull((result as VendorFileListResult.Success).nextCursor)
        assertEquals("/files", server.takeRequest().path)
    }

    @Test
    fun getFileMapsObjectFieldsAndKeepsRawFallback() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                "{\"id\":\"f9\",\"filename\":\"doc.pdf\",\"bytes\":32,\"created_at\":1700000003," +
                    "\"purpose\":\"user_data\"}",
            ),
        )
        val result = client.getFile("deepseek", base(), "key", "f9")
        assertTrue("result=$result", result is VendorFileObjectResult.Success)
        val file = (result as VendorFileObjectResult.Success).file
        assertEquals("f9", file.id)
        assertEquals("doc.pdf", file.filename)
        assertEquals(32L, file.sizeBytes)
        assertEquals(1700000003L, file.createdAtEpochSeconds)
        // 厂商特有字段（purpose）不进统一模型，原样进 raw 兜底。
        assertTrue(file.raw?.containsKey("purpose") == true)
        assertEquals("/files/f9", server.takeRequest().path)
    }

    @Test
    fun deleteMapsResponseFields() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"f9\",\"deleted\":true,\"object\":\"file\"}"),
        )
        val result = client.deleteFile("deepseek", base(), "key", "f9")
        assertTrue(result is github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult.Success)
        val success = result as github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult.Success
        assertEquals("f9", success.fileId)
        assertTrue(success.deleted)
        val recorded = server.takeRequest()
        assertEquals("/files/f9", recorded.path)
        assertEquals("DELETE", recorded.method)
    }

    @Test
    fun http404MapsToNotFound() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = client.getFile("deepseek", base(), "key", "missing")
        assertEquals(VendorFileFailureReason.NOT_FOUND, (result as VendorFileObjectResult.Failure).reason)
    }

    @Test
    fun getContentDownloadsBodyToTempFile() = runBlocking {
        val bytes = ByteArray(32) { (it * 7).toByte() }
        val payload = buildString { bytes.forEach { append((it.toInt() and 0x7F).toChar()) } }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/pdf; charset=binary")
                .setBody(payload),
        )
        val result = client.getContent("deepseek", base(), "key", "f1")
        val success = result as VendorFileContentResult.Success
        assertEquals("f1", success.fileId)
        assertEquals("application/pdf", success.mimeType)
        val written = File(success.localPath).readBytes()
        assertEquals(payload.toByteArray().size.toLong(), success.bytes)
        org.junit.Assert.assertArrayEquals(payload.toByteArray(), written)
    }

    @Test
    fun contentHttpErrorsMapToFailureReasons() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val missing = client.getContent("deepseek", base(), "key", "gone")
        assertEquals(VendorFileFailureReason.NOT_FOUND, (missing as VendorFileContentResult.Failure).reason)
        server.enqueue(MockResponse().setResponseCode(502))
        val bad = client.getContent("deepseek", base(), "key", "f1")
        assertEquals(VendorFileFailureReason.VENDOR_ERROR, (bad as VendorFileContentResult.Failure).reason)
    }
}
