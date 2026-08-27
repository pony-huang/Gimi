package github.ponyhuang.gimi.plugin.zhihu

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** ZhihuApi 网关测试：用 MockWebServer 覆盖请求拼接、multipart、包裹校验与错误映射。 */
class ZhihuApiTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var api: ZhihuApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ZhihuApi(baseUrl = server.url("").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun quota_returnsDataArray() {
        server.enqueue(json("""{"Code":0,"Message":"success","Data":[{"APIID":"zhihu_search","APIName":"知乎搜索","TotalQuota":100,"TotalUsed":5,"RemainingQuota":95}]}"""))

        val result = api.quota("secret", "zhihu_search")

        val request = server.takeRequest()
        assertEquals("/api/v1/quota?APIIDs=zhihu_search", request.path)
        assertEquals("Bearer secret", request.getHeader("Authorization"))
        assertNotNull(request.getHeader("X-Request-Timestamp"))

        assertEquals(1, result.length())
        assertEquals("zhihu_search", result.getJSONObject(0).getString("APIID"))
        assertEquals(95L, result.getJSONObject(0).getLong("RemainingQuota"))
    }

    @Test
    fun quota_omitsApiIdsWhenBlank() {
        server.enqueue(json("""{"Code":0,"Data":[]}"""))

        api.quota("secret", null)

        assertEquals("/api/v1/quota", server.takeRequest().path)
    }

    @Test
    fun knowledgeBases_sendsScope() {
        server.enqueue(json("""{"Code":0,"Data":{"Items":[]}}"""))

        api.knowledgeBases("secret", "subscribed")

        assertEquals("/api/v1/knowledge/bases?Scope=subscribed", server.takeRequest().path)
    }

    @Test
    fun knowledgeItems_buildsPathAndQuery() {
        server.enqueue(json("""{"Code":0,"Data":{"Items":[]}}"""))

        api.knowledgeItems("secret", "kb-1", "cur", 20)

        assertEquals("/api/v1/knowledge/bases/kb-1/items?Cursor=cur&Limit=20", server.takeRequest().path)
    }

    @Test
    fun knowledgeSearch_postsJsonBody() {
        server.enqueue(json("""{"Code":0,"Data":{"Items":[]}}"""))

        api.knowledgeSearch("secret", "退款规则", listOf("kb1"), listOf("personal", "subscription"), 10)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/knowledge/search", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("退款规则", body.getString("Query"))
        assertEquals(listOf("kb1"), body.getJSONArray("KnowledgeBaseIDs").toStringList())
        assertEquals(listOf("personal", "subscription"), body.getJSONArray("RecallScopes").toStringList())
        assertEquals(10, body.getInt("Limit"))
    }

    @Test
    fun knowledgeUpload_sendsMultipart() {
        server.enqueue(json("""{"Code":0,"Data":{"KnowledgeBaseID":"kb-1","RecallContentID":"rc-1","FileName":"doc.pdf"}}"""))
        val file = newFile("doc.pdf", "pdf-content")

        api.knowledgeUpload("secret", file.absolutePath, "kb-1")

        val request = server.takeRequest()
        assertEquals("/api/v1/knowledge/files", request.path)
        assertTrue(request.getHeader("Content-Type")?.startsWith("multipart/form-data; boundary=") == true)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"File\""))
        assertTrue(body.contains("filename=\"doc.pdf\""))
        assertTrue(body.contains("name=\"KnowledgeBaseID\""))
        assertTrue(body.contains("kb-1"))
        assertTrue(body.contains("pdf-content"))
    }

    @Test
    fun knowledgeUpload_withoutBaseId_omitsField() {
        server.enqueue(json("""{"Code":0,"Data":{}}"""))
        val file = newFile("doc.md", "hello")

        api.knowledgeUpload("secret", file.absolutePath, null)

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("KnowledgeBaseID"))
    }

    @Test
    fun upload_rejectsMissingFile() {
        val error = assertThrows(IllegalStateException::class.java) {
            api.knowledgeUpload("secret", "no/such/file.pdf", null)
        }
        assertTrue(error.message?.contains("文件不存在") == true)
    }

    @Test
    fun pdfUpload_sendsFileField() {
        server.enqueue(json("""{"Code":0,"Data":{"file_id":"file_123"}}"""))
        val file = newFile("doc.pdf", "pdf-data")

        api.pdfUpload("secret", file.absolutePath)

        val request = server.takeRequest()
        assertEquals("/resources/v1/files", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("filename=\"doc.pdf\""))
    }

    @Test
    fun createPdfTask_postsFileId() {
        server.enqueue(json("""{"Code":0,"Data":{"task_id":"pdf_1","task_status":"pending"}}"""))

        api.createPdfTask("secret", "file_123")

        val request = server.takeRequest()
        assertEquals("/api/v1/pdf-parse/tasks", request.path)
        assertNotNull(request.getHeader("Idempotency-Key"))
        assertEquals("file_123", JSONObject(request.body.readUtf8()).getString("file_id"))
    }

    @Test
    fun createPptTask_postsUrlAndPages() {
        server.enqueue(json("""{"Code":0,"Data":{"task_id":"ppt_1","task_status":"pending"}}"""))

        api.createPptTask("secret", "https://www.zhihu.com/answer/123", 12)

        val request = server.takeRequest()
        assertEquals("/api/v1/ppt-generation/tasks", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("https://www.zhihu.com/answer/123", body.getString("resource_url"))
        assertEquals(12, body.getInt("num_pages"))
    }

    @Test
    fun pollTask_hitsPdfParsePath() {
        server.enqueue(json("""{"Code":0,"Data":{"task_id":"t1","task_status":"running","progress":0.5}}"""))

        api.pollTask("secret", ZhihuTaskKind.PdfParse, "t1")

        assertEquals("/api/v1/pdf-parse/tasks/t1", server.takeRequest().path)
    }

    @Test
    fun pollTask_hitsPptGenerationPath() {
        server.enqueue(json("""{"Code":0,"Data":{"task_id":"t2","task_status":"succeeded"}}"""))

        api.pollTask("secret", ZhihuTaskKind.PptGeneration, "t2")

        assertEquals("/api/v1/ppt-generation/tasks/t2", server.takeRequest().path)
    }

    @Test
    fun errorEnvelope_throwsMessage() {
        server.enqueue(json("""{"Code":20001,"Message":"Access Secret 鉴权失败"}"""))

        val error = assertThrows(IllegalStateException::class.java) {
            api.quota("bad", null)
        }
        assertTrue(error.message?.contains("Access Secret 鉴权失败") == true)
    }

    @Test
    fun httpError_throws() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = assertThrows(IllegalStateException::class.java) {
            api.quota("secret", null)
        }
        assertTrue(error.message?.contains("HTTP 500") == true)
    }

    private fun json(body: String): MockResponse =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun newFile(name: String, content: String): File =
        tempFolder.newFile(name).apply { writeText(content) }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
