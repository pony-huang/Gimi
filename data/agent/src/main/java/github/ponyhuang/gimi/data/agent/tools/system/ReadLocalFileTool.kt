package github.ponyhuang.gimi.data.agent.tools.system

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** ADK function call 名；唯一真源，确认卡片与测试共用。 */
internal const val READ_LOCAL_FILE_TOOL_NAME: String = "read_local_file"

/**
 * 本地文件读取工具 — 参考 ADK `LoadArtifactsTool` 的"请求 → 下一轮注入"模式实现。
 *
 * 典型链路：模型先用 `search_media_files` / `search_documents` 找到文件，再以返回的
 * `contentUri` 调用本工具。`requiresConfirmation = true` 让每次读取都先经用户确认，
 * 由宿主确认卡片完成"是否读取"的咨询；用户拒绝时 [FunctionTool.run] 直接返回拒绝错误。
 *
 * 文件内容按类型走两条路：
 * - 文本类文件：内容直接放进 function response，随会话历史持久保存；
 * - 图片 / PDF / 音频：[execute] 只回执元数据，[processLlmRequest] 在紧接着的下一次
 *   模型请求里把字节作为 inlineData 的 user content 临时注入（与 LoadArtifactsTool
 *   加载 artifact 的注入同构），避免 base64 大载荷挤进 function-response JSON。
 *   注入内容只存活一轮请求，模型后续仍需时可再次调用本工具。
 *
 * 其余二进制文档（docx/xlsx 等）不注入：部分模型服务不支持内联读取，宁可让模型拿到
 * 明确错误并转用 `open_local_file` 预览，也不让整轮请求在协议层显式失败。
 */
@Singleton
class ReadLocalFileTool @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val documentSearch: DocumentSearchRepository,
) : FunctionTool(
    name = READ_LOCAL_FILE_TOOL_NAME,
    description =
        "Reads a local file found by search_media_files or search_documents and returns its " +
            "content. The user must approve every read. Text files return their text directly; " +
            "images, PDFs, and audio files are attached to the conversation right after the " +
            "tool response for direct inspection, so call this tool again whenever you need " +
            "the content again later. The contentUri must be a content URI returned by a local " +
            "search tool; raw file paths are not accepted.",
    requiresConfirmation = true,
) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "contentUri" to Schema(
                    type = Type.STRING,
                    description =
                        "The content URI of the file, taken from search_media_files or " +
                            "search_documents results.",
                ),
            ),
            required = listOf("contentUri"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
        val contentUri = (args["contentUri"] as? String)?.trim().orEmpty()
        if (contentUri.isEmpty()) {
            return error(
                "contentUri is required and must be a content URI returned by " +
                    "search_media_files or search_documents.",
            )
        }
        val uri = runCatching { contentUri.toUri() }.getOrNull()
        if (uri == null || uri.scheme != "content" || !isAllowedUri(uri, contentUri)) {
            return error(NOT_ALLOWED_MESSAGE)
        }
        // MediaStore URI 的直读依赖运行时媒体权限；文档目录走 SAF 授权，无需该检查。
        val mediaPermission = requiredMediaPermission(uri)
        if (mediaPermission != null &&
            appContext.checkSelfPermission(mediaPermission) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return error(
                "Media permission is required to read this file. Call " +
                    "request_media_file_permissions and ask the user to grant access, then retry.",
            )
        }
        val displayName = resolveDisplayName(uri)
        val mimeType = resolveMimeType(uri, displayName)

        // 类型不在支持范围内时给出明确出路，让模型改走 open_local_file 而不是反复重试。
        val textLike = isTextLikeMimeType(mimeType)
        if (!textLike && !isInlineReadableMimeType(mimeType)) {
            return error(
                "Files of type $mimeType cannot be read inline. Use open_local_file to open " +
                    "this file in a compatible app for the user instead.",
            )
        }

        val read = readBytesCapped(uri, cap = if (textLike) MAX_TEXT_BYTES else MAX_INLINE_BYTES)
            ?: return error(UNREADABLE_MESSAGE)
        if (!textLike && read.truncated) {
            return error(
                "The file is larger than ${MAX_INLINE_BYTES / (1024 * 1024)} MB and cannot be " +
                    "read inline. Use open_local_file to open it in a compatible app instead.",
            )
        }
        if (textLike) {
            val fullText = String(read.bytes, Charsets.UTF_8)
            return mapOf(
                "success" to true,
                "contentUri" to contentUri,
                "displayName" to displayName,
                "mimeType" to mimeType,
                "content" to fullText.take(MAX_TEXT_CHARS),
                "truncated" to (read.truncated || fullText.length > MAX_TEXT_CHARS),
            )
        }
        return mapOf(
            "success" to true,
            "contentUri" to contentUri,
            "displayName" to displayName,
            "mimeType" to mimeType,
            "sizeBytes" to read.bytes.size,
            // 告知模型内容随后临时注入、下一轮不再在历史里，需要时重新调用本工具。
            "status" to
                "file content is attached to the next message for this turn only. call " +
                "$READ_LOCAL_FILE_TOOL_NAME again later to access the content again.",
        )
    }

    /**
     * 参照 [com.google.adk.kt.tools.LoadArtifactsTool]：模型请求的末尾若恰好是本工具的
     * function response（二进制读取成功路径），重新读取文件并把字节以 inlineData 的
     * user content 追加进本次请求。
     */
    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val request = super.processLlmRequest(toolContext, llmRequest)
        val response = request.contents.lastOrNull()
            ?.parts
            ?.firstNotNullOfOrNull { part ->
                part.functionResponse
                    ?.takeIf { it.name == READ_LOCAL_FILE_TOOL_NAME }
                    ?.response
            }
            ?.takeIf { payload ->
                payload["success"] == true && !payload.containsKey("content")
            } ?: return request
        val contentUri = response["contentUri"] as? String ?: return request
        val mimeType = response["mimeType"] as? String ?: "application/octet-stream"
        val displayName = response["displayName"] as? String ?: contentUri

        val read = runCatching { contentUri.toUri() }.getOrNull()
            ?.let { readBytesCapped(it, MAX_INLINE_BYTES) }
        if (read == null) {
            // 回执成功但此刻读不到（文件被清理/权限变化）：显式告知，避免模型凭空臆测内容。
            return request.appendContent(
                Content(
                    role = Role.USER,
                    parts = listOf(
                        Part(text = "Local file \"$displayName\" could not be read anymore."),
                    ),
                ),
            )
        }
        return request.appendContent(
            Content(
                role = Role.USER,
                parts = listOf(
                    Part(text = "Local file \"$displayName\" ($mimeType) content:"),
                    Part(
                        inlineData = Blob(
                            mimeType = mimeType,
                            displayName = displayName,
                            data = read.bytes,
                        ),
                    ),
                ),
            ),
        )
    }

    // ---------- helpers ----------

    private suspend fun isAllowedUri(uri: Uri, originalValue: String): Boolean =
        uri.authority == MediaStore.AUTHORITY || documentSearch.isAuthorized(originalValue)

    private fun requiredMediaPermission(uri: Uri): String? {
        val segments = uri.pathSegments
        return when {
            "images" in segments -> Manifest.permission.READ_MEDIA_IMAGES
            "video" in segments -> Manifest.permission.READ_MEDIA_VIDEO
            "audio" in segments -> Manifest.permission.READ_MEDIA_AUDIO
            else -> null
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        // DISPLAY_NAME 对 MediaStore 与文档提供方通用（OpenableColumns 与 MediaColumns 同值）。
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf(String::isNotBlank)?.let { return it }
                }
            }
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun resolveMimeType(uri: Uri, displayName: String): String =
        runCatching { appContext.contentResolver.getType(uri) }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: AttachmentCategory.inferMimeType(displayName)
            ?: "application/octet-stream"

    /** 最多读 cap 字节；返回 null 表示流打不开（文件缺失、权限被收走等）。 */
    private fun readBytesCapped(uri: Uri, cap: Int): CappedRead? = try {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(cap + 1)
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read == -1) break
                offset += read
            }
            CappedRead(
                bytes = buffer.copyOf(offset.coerceAtMost(cap)),
                truncated = offset > cap,
            )
        }
    } catch (security: SecurityException) {
        null
    } catch (io: IOException) {
        null
    }

    private fun error(message: String): Map<String, Any> =
        mapOf("success" to false, "error" to message)

    /**
     * 一次受限读取的结果。
     *
     * @property bytes 实际读到的字节；源文件超过上限时只保留前 cap 字节。
     * @property truncated 是否因超过上限被截断（源文件比 cap 更长）。
     */
    private data class CappedRead(val bytes: ByteArray, val truncated: Boolean) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CappedRead

            if (truncated != other.truncated) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = truncated.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    private companion object {
        /** 文本类文件的字节读取上限；解码后仍按字符数二次截断。 */
        const val MAX_TEXT_BYTES: Int = 512 * 1024

        /** 文本内容进入 function response 的字符上限，防止长文本挤爆模型上下文。 */
        const val MAX_TEXT_CHARS: Int = 64_000

        /** 二进制内联读取的字节上限，与主流模型单请求内联容量同量级。 */
        const val MAX_INLINE_BYTES: Int = 20 * 1024 * 1024

        const val NOT_ALLOWED_MESSAGE =
            "contentUri is not an accessible media result or a file from an authorized " +
                "document directory. Only identifiers returned by search_media_files or " +
                "search_documents are accepted."
        const val UNREADABLE_MESSAGE =
            "The file could not be read. It may have been moved or deleted; search again to " +
                "get a fresh identifier."
    }
}

/** 文本类 MIME：直接以文本进入 function response，不经过 base64 内联注入。 */
internal fun isTextLikeMimeType(mimeType: String): Boolean {
    val normalized = mimeType.lowercase().substringBefore(';').trim()
    return normalized.startsWith("text/") || normalized in TEXT_MIME_TYPES
}

/** 可以内联注入给模型直读的二进制 MIME：图片、音频、PDF。 */
internal fun isInlineReadableMimeType(mimeType: String): Boolean {
    val normalized = mimeType.lowercase().substringBefore(';').trim()
    return normalized.startsWith("image/") ||
        normalized.startsWith("audio/") ||
        normalized == "application/pdf"
}

private val TEXT_MIME_TYPES = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/typescript",
    "application/x-yaml",
    "application/yaml",
    "application/toml",
    "application/csv",
    "application/rtf",
)
