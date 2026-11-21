package github.ponyhuang.gimi.data.modelaccess.file

import github.ponyhuang.gimi.domain.conversation.repository.VendorFileContentResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileListResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObject
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileObjectResult
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUpload
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileUploadResult
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Anthropic Files API 客户端（`{base}/v1/files`），覆盖 Claude 与 DeepSeek 兼容接口。
 *
 * 与 OpenAI 协议的关键差异：
 * - Claude 上传仅包含 `file`；DeepSeek 文档上传在 `file` 前设置 `purpose`；
 * - 认证是 `x-api-key` 头 + `anthropic-version` 版本头（files 专用的 beta header
 *   `files-api-2025-04-14` 仅 DeepSeek 兼容接口要求）；
 * - Claude 分页使用 page/next_page；DeepSeek 使用 after_id 与 has_more/last_id；
 * - 元数据字段是 `mime_type`/`size_bytes`，时间为 RFC 3339 字符串（需转 Unix 秒）；
 * - 删除响应 `{id, type: "file_deleted"}`，HTTP 2xx 即删除成功；
 * - Files 路径固定在 `/v1` 下：endpoint 不含 `/v1` 时自动补齐。
 *
 * @property downloadDir 下载内容的落盘目录（DI 注入应用 cache 子目录）。
 */
@Singleton
class ClaudeProtocolFileClient @Inject constructor(
    private val downloadDir: File,
) : FileProtocolClient {

    override val protocol: FileProtocol = FileProtocol.ANTHROPIC

    private val client: OkHttpClient = FileClientSupport.newClient()

    override suspend fun upload(
        vendor: String,
        endpoint: String,
        apiKey: String,
        request: VendorFileUpload,
    ): VendorFileUploadResult {
        val file = File(request.filePath)
        if (!file.isFile) {
            return VendorFileUploadResult.Failure(
                VendorFileFailureReason.NETWORK,
                "draft file is gone: ${request.filePath}",
            )
        }
        val url = filesUrl(endpoint)
            ?: return VendorFileUploadResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        // DeepSeek 文档解析需要 purpose，且必须排在 file 前；Claude 上传仅包含 file。
        val body = MultipartBody.Builder().apply {
            setType(MultipartBody.FORM)
            if (vendor == DEEPSEEK_VENDOR) {
                addFormDataPart("purpose", FileClientSupport.purposeFor(vendor, request.mimeType, request.purposeHint))
            }
            addFormDataPart(
                "file",
                FileClientSupport.asciiFileName(request.displayName.ifEmpty { file.name }),
                file.asRequestBody(request.mimeType.toMediaType()),
            )
        }.build()
        val httpRequest = requestBuilder(url, apiKey, vendor).post(body).build()
        return execute(httpRequest, { r, m -> VendorFileUploadResult.Failure(r, m) }) { payload ->
            val decoded = runCatching {
                FileClientSupport.json.decodeFromString<ClaudeFileMetadataResponse>(payload)
            }.getOrElse { e -> return@execute VendorFileUploadResult.Failure(
                VendorFileFailureReason.VENDOR_ERROR,
                "malformed upload response: ${e.message}",
            ) }
            VendorFileUploadResult.Uploaded(
                fileId = decoded.id,
                bytes = decoded.sizeBytes ?: file.length(),
                createdAtEpochSeconds = decoded.createdAt?.let { rfc3339ToEpochSeconds(it) } ?: 0L,
            )
        }
    }

    override suspend fun listFiles(
        vendor: String,
        endpoint: String,
        apiKey: String,
        limit: Int?,
        cursor: String?,
    ): VendorFileListResult {
        val query = filesUrl(endpoint)?.newBuilder()?.apply {
            limit?.let { addQueryParameter("limit", it.toString()) }
            cursor?.let { addQueryParameter(if (vendor == DEEPSEEK_VENDOR) "after_id" else "page", it) }
        }?.build() ?: return VendorFileListResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        return execute(requestBuilder(query, apiKey, vendor).build(), { r, m -> VendorFileListResult.Failure(r, m) }) { payload ->
            val decoded = runCatching {
                FileClientSupport.json.decodeFromString<ClaudeFileListResponse>(payload)
            }.getOrNull() ?: return@execute VendorFileListResult.Failure(
                VendorFileFailureReason.VENDOR_ERROR,
                "malformed list response",
            )
            VendorFileListResult.Success(
                files = decoded.data.map { it.toDomain(FileClientSupport.rawObject(payload)) },
                nextCursor = if (vendor == DEEPSEEK_VENDOR) {
                    decoded.lastId.takeIf { decoded.hasMore }
                } else {
                    decoded.nextPage
                },
            )
        }
    }

    override suspend fun getFile(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileObjectResult {
        val url = filesUrl(endpoint, fileId)
            ?: return VendorFileObjectResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        return execute(requestBuilder(url, apiKey, vendor).build(), { r, m -> VendorFileObjectResult.Failure(r, m) }) { payload ->
            val decoded = runCatching {
                FileClientSupport.json.decodeFromString<ClaudeFileMetadataResponse>(payload)
            }.getOrElse { e -> return@execute VendorFileObjectResult.Failure(
                VendorFileFailureReason.VENDOR_ERROR,
                "malformed file response: ${e.message}",
            ) }
            VendorFileObjectResult.Success(decoded.toDomain(FileClientSupport.rawObject(payload)))
        }
    }

    override suspend fun deleteFile(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileDeleteResult {
        val url = filesUrl(endpoint, fileId)
            ?: return VendorFileDeleteResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        return execute(requestBuilder(url, apiKey, vendor).delete().build(), { r, m -> VendorFileDeleteResult.Failure(r, m) }) { payload ->
            val decoded = runCatching {
                FileClientSupport.json.decodeFromString<ClaudeDeleteResponse>(payload)
            }.getOrNull()
            // 响应 {id, type: "file_deleted"}；2xx 但响应体异常时不移除本地注册表记录，
            // 否则会产生远端孤儿且本地失去管理入口。`{}` 能反序列化为全 null 对象，
            // 必须显式校验 type 而不是只判断 decoded 是否为空。
            if (decoded?.type != "file_deleted") {
                VendorFileDeleteResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "malformed delete response")
            } else {
                VendorFileDeleteResult.Success(fileId = decoded.id ?: fileId, deleted = true)
            }
        }
    }

    override suspend fun getContent(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileContentResult {
        val url = filesUrl(endpoint, fileId, "content")
            ?: return VendorFileContentResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(requestBuilder(url, apiKey, vendor).build()).awaitCancellable { response ->
                    when {
                        response.code == 404 -> VendorFileContentResult.Failure(VendorFileFailureReason.NOT_FOUND, "HTTP 404")
                        !response.isSuccessful -> VendorFileContentResult.Failure(
                            VendorFileFailureReason.VENDOR_ERROR,
                            "HTTP ${response.code}",
                        )
                        else -> {
                            val source = response.body
                                ?: return@awaitCancellable VendorFileContentResult.Failure(
                                    VendorFileFailureReason.VENDOR_ERROR, "empty body",
                                )
                            downloadDir.mkdirs()
                            val target = File.createTempFile("vendor-file-", null, downloadDir)
                            try {
                                source.byteStream().use { input ->
                                    target.outputStream().use { output -> input.copyTo(output) }
                                }
                            } catch (e: IOException) {
                                // 拷贝中断不留半写临时文件：取消（连接被 cancel）与读写失败都走这里。
                                target.delete()
                                throw e
                            }
                            VendorFileContentResult.Success(
                                fileId = fileId,
                                localPath = target.absolutePath,
                                mimeType = response.header("Content-Type")?.substringBefore(';'),
                                bytes = target.length(),
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                VendorFileContentResult.Failure(VendorFileFailureReason.NETWORK, e.message)
            }
        }
    }

    /** 统一响应处理：404/非 2xx 归类，2xx 交给 [onSuccess]；传输层随协程取消而中断。 */
    private suspend fun <T> execute(
        request: Request,
        failure: (VendorFileFailureReason, String?) -> T,
        onSuccess: (payload: String) -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            val (code, payload) = client.newCall(request).awaitCancellable { response ->
                response.code to response.body?.string().orEmpty()
            }
            when {
                code == 404 -> failure(VendorFileFailureReason.NOT_FOUND, "HTTP 404")
                code !in 200..299 -> failure(
                    VendorFileFailureReason.VENDOR_ERROR,
                    "HTTP $code: ${FileClientSupport.errorDetail(payload)}",
                )
                else -> onSuccess(payload)
            }
        } catch (e: IOException) {
            failure(VendorFileFailureReason.NETWORK, e.message)
        }
    }

    /**
     * 拼接 Files 基路径 `{base}[/v1]/files`：endpoint 已以 `/v1` 结尾时不重复补段，
     * 否则先补 `v1`（官方基地址是 `https://api.anthropic.com`，不含 `/v1`）。
     * [extraSegments] 追加在 `files` 之后（如 `{fileId}`、`content`）。
     */
    private fun filesUrl(endpoint: String, vararg extraSegments: String): HttpUrl? {
        val builder = endpoint.trimEnd('/').toHttpUrlOrNull()?.newBuilder() ?: return null
        if (!builder.build().encodedPath.endsWith("/v1")) builder.addPathSegment("v1")
        builder.addPathSegment("files")
        extraSegments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun requestBuilder(url: HttpUrl, apiKey: String, vendor: String): Request.Builder = Request.Builder()
        .url(url)
        .header("x-api-key", apiKey)
        .header("anthropic-version", ANTHROPIC_VERSION)
        .apply {
            // DeepSeek 的兼容 Files API 仍要求 beta 头，Claude GA 使用当前响应形态。
            if (vendor == DEEPSEEK_VENDOR) header("anthropic-beta", "files-api-2025-04-14")
        }

    private companion object {
        const val DEEPSEEK_VENDOR = "deepseek"

        /** Anthropic API 版本头；DeepSeek 的兼容 beta 要求在请求构造处单独处理。 */
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}

/** RFC 3339 时间串（如 `2025-04-14T12:00:00Z`）转 Unix 秒；不可解析时回退 0。 */
private fun rfc3339ToEpochSeconds(value: String): Long =
    runCatching { OffsetDateTime.parse(value).toInstant().epochSecond }.getOrDefault(0L)

/** Anthropic BetaFileMetadata 线格式的最小字段集。 */
@Serializable
private data class ClaudeFileMetadataResponse(
    val id: String,
    val filename: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
) {
    // 注意：@Serializable 类不要带自定义 companion —— 运行期 serializer 反射会因
    // 私有可见性抛 IllegalAccessError（Android 单测实测踩坑）。时间转换用文件内
    // 顶层函数 rfc3339ToEpochSeconds。
    fun toDomain(raw: JsonObject?): VendorFileObject = VendorFileObject(
        id = id,
        filename = filename,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        createdAtEpochSeconds = createdAt?.let { rfc3339ToEpochSeconds(it) } ?: 0L,
        expiresAtEpochSeconds = expiresAt?.let { rfc3339ToEpochSeconds(it) },
        raw = raw,
    )
}

/** Anthropic 文件列表；Claude 使用 next_page，DeepSeek 兼容接口使用 has_more 与 last_id。 */
@Serializable
private data class ClaudeFileListResponse(
    val data: List<ClaudeFileMetadataResponse> = emptyList(),
    @SerialName("next_page") val nextPage: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("last_id") val lastId: String? = null,
)

/** Anthropic 删除响应。 */
@Serializable
private data class ClaudeDeleteResponse(
    val id: String? = null,
    val type: String? = null,
)
