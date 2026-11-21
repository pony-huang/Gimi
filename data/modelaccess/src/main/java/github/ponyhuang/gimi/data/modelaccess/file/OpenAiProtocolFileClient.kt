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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * OpenAI Files 协议客户端（`{base}/files`），覆盖 DeepSeek 等兼容 OpenAI 协议的厂商。
 *
 * 协议要点：
 * - multipart 表单中 `purpose` 字段必须写在 `file` 之前，否则厂商拒绝请求；
 * - purpose 由实现方映射：DeepSeek 按 mime（PDF/Word/PPT → rich_layout_recognition，
 *   其余 → user_data）；通用 OpenAI 兼容厂商默认 user_data；调用方可用
 *   [VendorFileUpload.purposeHint] 显式覆盖（batch/fine-tune 等场景）；
 * - 分页用 `after` 游标，响应 `has_more` + `last_id` 映射为统一 `nextCursor`；
 * - 删除响应 `{id, deleted}`；内容端点为 `/files/{id}/content`；
 * - 文档须在 120 秒内完成上传，客户端超时取 150 秒留出余量。
 *
 * @property downloadDir 下载内容的落盘目录（DI 注入应用 cache 子目录）。
 */
@Singleton
class OpenAiProtocolFileClient @Inject constructor(
    private val downloadDir: File,
) : FileProtocolClient {

    override val protocol: FileProtocol = FileProtocol.OPENAI_COMPATIBLE

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
        val url = url(endpoint, "files")
            ?: return VendorFileUploadResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        val body = MultipartBody.Builder().apply {
            // Files API 按表单解析；Builder 默认的 multipart/mixed 会被厂商拒绝。
            setType(MultipartBody.FORM)
            // purpose 必须先于 file：厂商按字段顺序校验表单。
            addFormDataPart("purpose", FileClientSupport.purposeFor(vendor, request.mimeType, request.purposeHint))
            addFormDataPart(
                "file",
                FileClientSupport.asciiFileName(request.displayName.ifEmpty { file.name }),
                file.asRequestBody(request.mimeType.toMediaType()),
            )
        }.build()
        val httpRequest = authorized(url, apiKey).post(body).build()
        return execute(httpRequest, { r, m -> VendorFileUploadResult.Failure(r, m) }) { payload ->
            val decoded = runCatching {
                FileClientSupport.json.decodeFromString<UploadResponse>(payload)
            }.getOrNull() ?: return@execute VendorFileUploadResult.Failure(
                VendorFileFailureReason.VENDOR_ERROR,
                "malformed upload response",
            )
            // 上传成功返回的 id 即聊天 file 块引用的 file_id；bytes/created_at 进注册表。
            VendorFileUploadResult.Uploaded(
                fileId = decoded.id,
                bytes = decoded.bytes ?: file.length(),
                createdAtEpochSeconds = decoded.createdAt ?: 0L,
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
        val query = url(endpoint, "files")?.newBuilder()?.apply {
            limit?.let { addQueryParameter("limit", it.toString()) }
            cursor?.let { addQueryParameter("after", it) }
        }?.build() ?: return VendorFileListResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        return execute(query.requestBuilder(apiKey).build(), { r, m -> VendorFileListResult.Failure(r, m) }) { payload ->
            val decoded = runCatching {
                FileClientSupport.json.decodeFromString<OpenAiFileListResponse>(payload)
            }.getOrNull() ?: return@execute VendorFileListResult.Failure(
                VendorFileFailureReason.VENDOR_ERROR,
                "malformed list response",
            )
            VendorFileListResult.Success(
                files = decoded.data.map { it.toDomain(FileClientSupport.rawObject(payload)) },
                // OpenAI 用 has_more + last_id 表达分页；last_id 即下一页 after 游标。
                nextCursor = decoded.lastId.takeIf { decoded.hasMore },
            )
        }
    }

    override suspend fun getFile(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileObjectResult {
        val url = url(endpoint, "files", fileId)
            ?: return VendorFileObjectResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        return execute(url.requestBuilder(apiKey).build(), { r, m -> VendorFileObjectResult.Failure(r, m) }) { payload ->
            val decoded = runCatching {
                FileClientSupport.json.decodeFromString<OpenAiFileResponse>(payload)
            }.getOrNull() ?: return@execute VendorFileObjectResult.Failure(
                VendorFileFailureReason.VENDOR_ERROR,
                "malformed file response",
            )
            VendorFileObjectResult.Success(decoded.toDomain(FileClientSupport.rawObject(payload)))
        }
    }

    override suspend fun deleteFile(
        vendor: String,
        endpoint: String,
        apiKey: String,
        fileId: String,
    ): VendorFileDeleteResult {
        val url = url(endpoint, "files", fileId)
            ?: return VendorFileDeleteResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        return execute(url.requestBuilder(apiKey).delete().build(), { r, m -> VendorFileDeleteResult.Failure(r, m) }) { payload ->
            val decoded = runCatching {
                FileClientSupport.json.decodeFromString<OpenAiDeleteResponse>(payload)
            }.getOrNull()
            // 必须确认厂商明确 reported deleted 才算成功：2xx 但响应体异常时不移除本地
            // 注册表记录，否则会产生远端孤儿且本地失去管理入口。
            if (decoded?.deleted != true) {
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
        val url = url(endpoint, "files", fileId, "content")
            ?: return VendorFileContentResult.Failure(VendorFileFailureReason.VENDOR_ERROR, "invalid endpoint: $endpoint")
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(url.requestBuilder(apiKey).build()).awaitCancellable { response ->
                    when {
                        response.code == 404 -> VendorFileContentResult.Failure(VendorFileFailureReason.NOT_FOUND, "HTTP 404")
                        response.code == 413 -> VendorFileContentResult.Failure(VendorFileFailureReason.TOO_LARGE, "HTTP 413")
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

    /** 统一响应处理：413/404/非 2xx 归类，2xx 交给 [onSuccess]；传输层随协程取消而中断。 */
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
                code == 413 -> failure(VendorFileFailureReason.TOO_LARGE, "HTTP 413")
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

    private fun authorized(url: okhttp3.HttpUrl, apiKey: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer $apiKey")

    private fun okhttp3.HttpUrl.requestBuilder(apiKey: String): Request.Builder =
        Request.Builder().url(this).header("Authorization", "Bearer $apiKey")

    /** 拼接 `{base}/{segments...}`；base 可含 `/v1` 路径，段内非法字符由 OkHttp 转义。 */
    private fun url(endpoint: String, vararg segments: String): okhttp3.HttpUrl? =
        endpoint.trimEnd('/').toHttpUrlOrNull()?.newBuilder()?.apply {
            segments.forEach { addPathSegment(it) }
        }?.build()
}

/** OpenAI Files 协议上传响应的最小字段集（DeepSeek 同构）。 */
@Serializable
private data class UploadResponse(
    val id: String,
    val bytes: Long? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)

/** OpenAI Files 协议的 FileObject 线格式（列表/查询单个共用）。 */
@Serializable
private data class OpenAiFileResponse(
    val id: String,
    val filename: String? = null,
    val bytes: Long? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
) {
    fun toDomain(raw: kotlinx.serialization.json.JsonObject?): VendorFileObject = VendorFileObject(
        id = id,
        filename = filename,
        sizeBytes = bytes,
        mimeType = null,
        createdAtEpochSeconds = createdAt ?: 0L,
        expiresAtEpochSeconds = expiresAt,
        raw = raw,
    )
}

/** OpenAI Files 协议列表响应；`has_more` + `last_id` 构成游标翻页。 */
@Serializable
private data class OpenAiFileListResponse(
    val data: List<OpenAiFileResponse> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("last_id") val lastId: String? = null,
)

/** OpenAI Files 协议删除响应。 */
@Serializable
private data class OpenAiDeleteResponse(
    val id: String? = null,
    val deleted: Boolean = false,
)
