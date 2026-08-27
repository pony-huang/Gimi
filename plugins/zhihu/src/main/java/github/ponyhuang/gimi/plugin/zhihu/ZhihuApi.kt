package github.ponyhuang.gimi.plugin.zhihu

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** 知乎异步任务类型；路径段用于拼接建任务/轮询 URL。 */
internal enum class ZhihuTaskKind(val pathSegment: String) {
    PdfParse("pdf-parse"),
    PptGeneration("ppt-generation"),
}

/** multipart 表单文本字段。 */
internal class MultipartField(val name: String, val value: String)

/** multipart 表单文件字段。 */
internal class MultipartFile(
    val name: String,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

/**
 * 知乎开放平台（developer.zhihu.com）API 客户端。
 *
 * 鉴权：`Authorization: Bearer <access_secret>` + `X-Request-Timestamp`（秒级时间戳）。
 * 响应包裹：`{Code, Message, Data}`（Code=0 成功）。
 * HTTP 用 JDK 自带 HttpURLConnection，JSON 用 Android 自带 org.json，插件零第三方依赖。
 */
internal class ZhihuApi(
    private val baseUrl: String = "https://developer.zhihu.com",
) {

    /** 热榜：返回 `Data`（含 `Total`、`Items`）。 */
    fun hotList(secret: String, limit: Int): JSONObject =
        data(get("/api/v1/content/hot_list", secret, mapOf("Limit" to limit.toString())))

    /** 站内搜索：返回 `Data`（含 `Items`、`HasMore`、`SearchHashId`）。 */
    fun zhihuSearch(secret: String, query: String, count: Int): JSONObject =
        data(
            get(
                "/api/v1/content/zhihu_search",
                secret,
                mapOf("Query" to query, "Count" to count.toString()),
            ),
        )

    /** 全网搜索：返回 `Data`。 */
    fun globalSearch(
        secret: String,
        query: String,
        count: Int,
        filter: String?,
        searchDb: String?,
    ): JSONObject {
        val params = linkedMapOf("Query" to query, "Count" to count.toString())
        filter?.takeIf(String::isNotBlank)?.let { params["Filter"] = it }
        searchDb?.takeIf(String::isNotBlank)?.let { params["SearchDB"] = it }
        return data(get("/api/v1/content/global_search", secret, params))
    }

    /** 直答：返回 OpenAI 风格 JSON（`choices[].message`）。 */
    fun zhida(secret: String, model: String, query: String): JSONObject {
        val messages = JSONArray()
            .put(JSONObject().put("role", "user").put("content", query))
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", false)
        return checked(post("/v1/chat/completions", secret, body.toString()))
    }

    /** 额度查询：Data 是 JSON 数组（非对象），经 [dataArray] 取出。 */
    fun quota(secret: String, apiIds: String?): JSONArray {
        val query = apiIds?.takeIf(String::isNotBlank)?.let { mapOf("APIIDs" to it) }.orEmpty()
        return dataArray(get("/api/v1/quota", secret, query))
    }

    /** 知识库列表：可选 Scope（all/created/subscribed）。 */
    fun knowledgeBases(secret: String, scope: String?): JSONObject {
        val query = scope?.takeIf(String::isNotBlank)?.let { mapOf("Scope" to it) }.orEmpty()
        return data(get("/api/v1/knowledge/bases", secret, query))
    }

    /** 知识库内容分页列表：path 带 [knowledgeBaseId]，可选 Cursor/Limit。 */
    fun knowledgeItems(secret: String, knowledgeBaseId: String, cursor: String?, limit: Int): JSONObject {
        val query = linkedMapOf<String, String>()
        cursor?.takeIf(String::isNotBlank)?.let { query["Cursor"] = it }
        query["Limit"] = limit.toString()
        return data(get("/api/v1/knowledge/bases/${encPath(knowledgeBaseId)}/items", secret, query))
    }

    /** 知识库 RAG 检索：KnowledgeBaseIDs / RecallScopes 至少一个非空。 */
    fun knowledgeSearch(
        secret: String,
        query: String,
        knowledgeBaseIds: List<String>,
        recallScopes: List<String>,
        limit: Int,
    ): JSONObject {
        val body = JSONObject()
            .put("Query", query)
            .put("KnowledgeBaseIDs", JSONArray().apply { knowledgeBaseIds.forEach(::put) })
            .put("RecallScopes", JSONArray().apply { recallScopes.forEach(::put) })
            .put("Limit", limit)
        return data(post("/api/v1/knowledge/search", secret, body.toString()))
    }

    /** 知识库文件上传：multipart，File + 可选 KnowledgeBaseID。 */
    fun knowledgeUpload(secret: String, filePath: String, knowledgeBaseId: String?): JSONObject {
        val file = requireFile(filePath)
        val fields = buildList {
            knowledgeBaseId?.takeIf(String::isNotBlank)?.let { add(MultipartField("KnowledgeBaseID", it)) }
        }
        val files = listOf(MultipartFile("File", file.name, contentTypeFor(file.name), file.readBytes()))
        return data(postMultipart("/api/v1/knowledge/files", secret, fields, files))
    }

    /** PDF 上传：multipart 字段 `file`，返回 Data（含 file_id）。 */
    fun pdfUpload(secret: String, filePath: String): JSONObject {
        val file = requireFile(filePath)
        val files = listOf(MultipartFile("file", file.name, contentTypeFor(file.name), file.readBytes()))
        return data(postMultipart("/resources/v1/files", secret, emptyList(), files))
    }

    /** 创建 PDF 解析任务。 */
    fun createPdfTask(secret: String, fileId: String): JSONObject =
        createTask(secret, ZhihuTaskKind.PdfParse, JSONObject().put("file_id", fileId))

    /** 创建 PPT 生成任务。 */
    fun createPptTask(secret: String, resourceUrl: String, numPages: Int): JSONObject =
        createTask(
            secret,
            ZhihuTaskKind.PptGeneration,
            JSONObject().put("resource_url", resourceUrl).put("num_pages", numPages),
        )

    /** 轮询异步任务状态；kind 决定 URL 路径段。 */
    fun pollTask(secret: String, kind: ZhihuTaskKind, taskId: String): JSONObject =
        data(get("/api/v1/${kind.pathSegment}/tasks/${encPath(taskId)}", secret, emptyMap()))

    /** 包裹校验 + 取 Data。 */
    private fun data(json: JSONObject): JSONObject = checked(json).optJSONObject("Data") ?: JSONObject()

    /** 包裹校验 + 取 Data（数组形态，如额度查询）。 */
    private fun dataArray(json: JSONObject): JSONArray = checked(json).optJSONArray("Data") ?: JSONArray()

    private fun checked(json: JSONObject): JSONObject {
        val code = json.optInt("Code", 0)
        if (code != 0) {
            throw IllegalStateException("知乎 API 错误 code=$code: ${json.optString("Message")}")
        }
        return json
    }

    private fun get(path: String, secret: String, query: Map<String, String>): JSONObject {
        val url = buildString {
            append(baseUrl).append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" })
            }
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.applyAuth(secret)
        return connection.readJson()
    }

    private fun post(
        path: String,
        secret: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.applyAuth(secret)
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return connection.readJson()
    }

    /** multipart 表单 POST：Content-Type 需带 boundary，且不设置 application/json。 */
    private fun postMultipart(
        path: String,
        secret: String,
        fields: List<MultipartField>,
        files: List<MultipartFile>,
    ): JSONObject {
        val boundary = "----ZhihuBoundary${System.nanoTime()}"
        val body = buildMultipartBody(boundary, fields, files)
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.applyAuth(secret, contentType = null)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("Content-Length", body.size.toString())
        connection.outputStream.use { it.write(body) }
        return connection.readJson()
    }

    /** 设置鉴权头与超时；必须在写请求体之前调用（写体会触发连接，之后不能再改请求头）。 */
    private fun HttpURLConnection.applyAuth(secret: String, contentType: String? = "application/json") {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        setRequestProperty("Authorization", "Bearer $secret")
        setRequestProperty("X-Request-Timestamp", (System.currentTimeMillis() / 1000).toString())
        contentType?.let { setRequestProperty("Content-Type", it) }
    }

    /** 读取响应并统一错误处理；请求头已在写体前经 [applyAuth] 设置。 */
    private fun HttpURLConnection.readJson(): JSONObject {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("知乎 API HTTP $code: $body")
        }
        return JSONObject(body)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** path 段编码：URLEncoder 把空格编成 `+`，path 段需要 `%20`。 */
    private fun encPath(value: String): String = enc(value).replace("+", "%20")

    /** 校验本地文件存在且不超过 100MB，供上传接口使用。 */
    private fun requireFile(path: String): File {
        val file = File(path)
        if (!file.exists() || !file.isFile) throw IllegalStateException("文件不存在：$path")
        if (file.length() > MAX_UPLOAD_BYTES) throw IllegalStateException("文件超过 100MB 上传上限：$path")
        return file
    }

    /** 通用异步任务创建：body 决定幂等键，同一 body 重试复用同一 task_id。 */
    private fun createTask(secret: String, kind: ZhihuTaskKind, body: JSONObject): JSONObject {
        val json = body.toString()
        val key = UUID.nameUUIDFromBytes(json.toByteArray(Charsets.UTF_8)).toString()
        return data(post("/api/v1/${kind.pathSegment}/tasks", secret, json, headers = mapOf("Idempotency-Key" to key)))
    }

    /** 组装 multipart 请求体：先文本字段后文件字段，结尾 `--boundary--`。 */
    private fun buildMultipartBody(
        boundary: String,
        fields: List<MultipartField>,
        files: List<MultipartFile>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun write(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
        fun writeLine(s: String = "") {
            write(s)
            write("\r\n")
        }
        for (f in fields) {
            writeLine("--$boundary")
            writeLine("Content-Disposition: form-data; name=\"${f.name}\"")
            writeLine()
            writeLine(f.value)
        }
        for (f in files) {
            writeLine("--$boundary")
            writeLine("Content-Disposition: form-data; name=\"${f.name}\"; filename=\"${safeFileName(f.fileName)}\"")
            writeLine("Content-Type: ${f.contentType}")
            writeLine()
            out.write(f.bytes)
            writeLine()
        }
        write("--$boundary--")
        return out.toByteArray()
    }

    /** 文件名清理：剔除会破坏 multipart 头的引号与控制字符。 */
    private fun safeFileName(name: String): String = name.replace("\"", "").replace("\r", "").replace("\n", "")

    private fun contentTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "md", "markdown" -> "text/markdown"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private companion object {
        /** 建立连接超时；服务无响应时快速失败，避免阻塞 Agent 轮次。 */
        const val CONNECT_TIMEOUT_MS: Int = 10_000

        /** 读取响应超时。 */
        const val READ_TIMEOUT_MS: Int = 30_000

        /** 上传大小上限：100MB（文档要求）。 */
        const val MAX_UPLOAD_BYTES: Long = 100L * 1024 * 1024
    }
}
