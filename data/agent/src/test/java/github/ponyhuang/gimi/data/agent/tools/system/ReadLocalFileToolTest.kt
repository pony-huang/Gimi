package github.ponyhuang.gimi.data.agent.tools.system

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadLocalFileToolTest {
    private val contentResolver = mockk<ContentResolver>()
    private val documentSearch = mockk<DocumentSearchRepository>()

    @After
    fun tearDown() {
        // mockkStatic 全 JVM 生效，显式还原避免影响同模块后续测试类。
        unmockkStatic(Uri::class)
    }

    private fun tool(context: Context = mockk(relaxed = true)): ReadLocalFileTool {
        every { context.contentResolver } returns contentResolver
        // displayName 查询统一返回空，让工具回退到 URI lastPathSegment，便于断言。
        every {
            contentResolver.query(any(), any(), any(), any(), any())
        } returns null
        return ReadLocalFileTool(context, documentSearch)
    }

    // ---------- execute：入口校验 ----------

    @Test
    fun missingContentUriIsReportedAsArgumentError() = runTest {
        val response = responseOf(tool().execute(mockk(), emptyMap()))

        assertEquals(false, response["success"])
        assertTrue((response["error"] as String).contains("contentUri is required"))
    }

    @Test
    fun uriOutsideAuthorizedSourcesIsRejected() = runTest {
        mockkStatic(Uri::class)
        every { Uri.parse("content://unknown/provider/file") } returns mockUri(authority = "unknown")
        coEvery { documentSearch.isAuthorized("content://unknown/provider/file") } returns false

        val response = responseOf(tool().execute(mockk(), args("content://unknown/provider/file")))

        assertEquals(false, response["success"])
        assertTrue((response["error"] as String).contains("not an accessible"))
        coVerify(exactly = 1) { documentSearch.isAuthorized("content://unknown/provider/file") }
    }

    @Test
    fun mediaStoreUriWithoutRuntimePermissionAsksForPermissionFirst() = runTest {
        val context = mockk<Context>(relaxed = true)
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED
        mockkStatic(Uri::class)
        every { Uri.parse("content://media/external/images/media/9") } returns mockUri(
            authority = "media",
            pathSegments = listOf("external", "images", "media", "9"),
        )

        val response = responseOf(
            tool(context).execute(mockk(), args("content://media/external/images/media/9")),
        )

        assertEquals(false, response["success"])
        assertTrue((response["error"] as String).contains("request_media_file_permissions"))
    }

    // ---------- execute：文本路径 ----------

    @Test
    fun textFileContentIsReturnedInsideFunctionResponse() = runTest {
        val tool = authorizedDocumentTool("content://docs/doc/notes")
        every { contentResolver.getType(any()) } returns "text/markdown"
        every { contentResolver.openInputStream(any()) } returns
            ByteArrayInputStream("计划内容".toByteArray(Charsets.UTF_8))

        val response = responseOf(tool.execute(mockk(), args("content://docs/doc/notes")))

        assertEquals(true, response["success"])
        assertEquals("计划内容", response["content"])
        assertEquals(false, response["truncated"])
        assertEquals("text/markdown", response["mimeType"])
        assertNotNull(response["displayName"])
    }

    @Test
    fun longTextFileIsTruncatedWithMarker() = runTest {
        val tool = authorizedDocumentTool("content://docs/doc/big")
        every { contentResolver.getType(any()) } returns "text/plain"
        val longText = "a".repeat(100_000)
        every { contentResolver.openInputStream(any()) } returns
            ByteArrayInputStream(longText.toByteArray(Charsets.UTF_8))

        val response = responseOf(tool.execute(mockk(), args("content://docs/doc/big")))

        assertEquals(true, response["success"])
        assertEquals(true, response["truncated"])
        val content = response["content"] as String
        assertTrue("content must be trimmed below the source length", content.length < longText.length)
    }

    // ---------- execute：二进制与不支持类型 ----------

    @Test
    fun binaryFileReturnsMetadataOnlyForLaterInjection() = runTest {
        val tool = authorizedDocumentTool("content://docs/doc/photo")
        every { contentResolver.getType(any()) } returns "image/png"
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream(pngBytes)

        val response = responseOf(tool.execute(mockk(), args("content://docs/doc/photo")))

        assertEquals(true, response["success"])
        assertEquals(pngBytes.size, response["sizeBytes"])
        assertTrue((response["status"] as String).contains("read_local_file"))
        assertNull(response["content"])
    }

    @Test
    fun unsupportedBinaryDocumentFallsBackToOpenLocalFile() = runTest {
        val tool = authorizedDocumentTool("content://docs/doc/report")
        every { contentResolver.getType(any()) } returns
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        val response = responseOf(tool.execute(mockk(), args("content://docs/doc/report")))

        assertEquals(false, response["success"])
        assertTrue((response["error"] as String).contains("open_local_file"))
    }

    // ---------- processLlmRequest：LoadArtifactsTool 式注入 ----------

    @Test
    fun binaryReadResponseInjectsInlineDataIntoNextRequest() = runTest {
        val tool = authorizedDocumentTool("content://docs/doc/photo")
        every { contentResolver.getType(any()) } returns "image/png"
        val pngBytes = byteArrayOf(1, 2, 3, 4)
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream(pngBytes)
        val payload = responseOf(tool.execute(mockk(), args("content://docs/doc/photo")))

        val request = LlmRequest(contents = listOf(readResponseContent(payload)))
        val enriched = tool.processLlmRequest(mockk<ToolContext>(), request)

        val injected = enriched.contents.last()
        assertEquals(2, injected.parts.size)
        assertEquals(Role.USER, injected.role)
        val blob = injected.parts[1].inlineData
        assertEquals("image/png", blob?.mimeType)
        assertArrayEquals(pngBytes, blob?.data)
        assertTrue(injected.parts[0].text.orEmpty().contains("photo"))
    }

    @Test
    fun textReadResponseIsNotInjectedAgain() = runTest {
        val tool = authorizedDocumentTool("content://docs/doc/notes")
        every { contentResolver.getType(any()) } returns "text/plain"
        every { contentResolver.openInputStream(any()) } returns
            ByteArrayInputStream("hello".toByteArray())
        val payload = responseOf(tool.execute(mockk(), args("content://docs/doc/notes")))

        val request = LlmRequest(contents = listOf(readResponseContent(payload)))
        val enriched = tool.processLlmRequest(mockk<ToolContext>(), request)

        assertEquals(1, enriched.contents.size)
    }

    @Test
    fun unreadableFileAtInjectionTimeIsReportedAsUserContent() = runTest {
        val tool = authorizedDocumentTool("content://docs/doc/photo")
        every { contentResolver.getType(any()) } returns "image/png"
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream(byteArrayOf(1))
        val payload = responseOf(tool.execute(mockk(), args("content://docs/doc/photo")))

        // 注入阶段文件被清理：流打不开 → 以文本显式告知模型，而不是静默跳过。
        every { contentResolver.openInputStream(any()) } returns null
        val request = LlmRequest(contents = listOf(readResponseContent(payload)))
        val enriched = tool.processLlmRequest(mockk<ToolContext>(), request)

        val injected = enriched.contents.last()
        assertEquals(1, injected.parts.size)
        assertTrue(injected.parts[0].text.orEmpty().contains("could not be read"))
    }

    @Test
    fun otherToolResponsesAreIgnoredByInjection() = runTest {
        val request = LlmRequest(
            contents = listOf(
                Content(
                    role = Role.USER,
                    parts = listOf(
                        Part(
                            functionResponse = FunctionResponse(
                                name = "search_documents",
                                id = "call-1",
                                response = mapOf("success" to true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val enriched = tool().processLlmRequest(mockk<ToolContext>(), request)

        assertEquals(1, enriched.contents.size)
    }

    // ---------- helpers ----------

    // execute() 的 ADK 签名返回 Any；断言前统一收窄为 Map。
    private fun responseOf(result: Any): Map<*, *> = result as Map<*, *>

    private fun args(contentUri: String): Map<String, Any?> = mapOf("contentUri" to contentUri)

    private fun readResponseContent(payload: Any): Content = Content(
        role = Role.USER,
        parts = listOf(
            Part(
                functionResponse = FunctionResponse(
                    name = READ_LOCAL_FILE_TOOL_NAME,
                    id = "call-1",
                    response = payload as Map<String, Any?>,
                ),
            ),
        ),
    )

    /** 授权文档目录 URI：authority 非 MediaStore，走 [DocumentSearchRepository.isAuthorized]。 */
    private suspend fun authorizedDocumentTool(contentUri: String): ReadLocalFileTool {
        mockkStatic(Uri::class)
        every { Uri.parse(contentUri) } returns mockUri(authority = "docs", lastSegment = contentUri.substringAfterLast('/'))
        coEvery { documentSearch.isAuthorized(contentUri) } returns true
        return tool()
    }

    private fun mockUri(
        authority: String,
        pathSegments: List<String> = listOf("doc", "file"),
        lastSegment: String? = null,
    ): Uri = mockk {
        every { scheme } returns "content"
        every { this@mockk.authority } returns authority
        every { this@mockk.pathSegments } returns pathSegments
        every { lastPathSegment } returns (lastSegment ?: pathSegments.last())
    }
}
