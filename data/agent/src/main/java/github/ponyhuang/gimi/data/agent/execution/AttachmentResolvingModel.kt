package github.ponyhuang.gimi.data.agent.execution

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Part
import java.io.File
import java.net.URI
import kotlinx.coroutines.flow.Flow

/**
 * 在模型执行边界把持久化的附件引用解析为仅供本次请求使用的内联 [Part]。
 *
 * ADK 会话继续保存轻量的 `FileData` 引用；厂商模型收到的请求副本只包含已经解析好的
 * `Part.inlineData` 或明确的 HTTP(S) 远程引用，避免协议适配器感知本地文件系统。
 *
 * 解析失败必须按 part 降级为文本标记而不能抛错：`FileData` 指向 workspace 中用户可
 * 自行删除/移动的文件，且每次请求都会重放整段历史；一处抛错会让该会话之后的每一次
 * 模型调用都失败，整个会话永久不可用。
 */
internal class AttachmentResolvingModel(
    private val delegate: Model,
) : Model {
    override val name: String = delegate.name

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> =
        delegate.generateContent(request.resolveAttachmentParts(), stream)
}

private fun LlmRequest.resolveAttachmentParts(): LlmRequest = copy(
    contents = contents.map { content ->
        content.copy(parts = content.parts.map(Part::resolveAttachment))
    },
)

private fun Part.resolveAttachment(): Part {
    val reference = fileData ?: return this
    val uri = reference.fileUri ?: return attachmentUnavailable(reference, "附件引用缺少 URI")
    if (uri.isHttpReference()) return this

    val file = if (uri.startsWith(FILE_URI_SCHEME, ignoreCase = true)) {
        runCatching { File(URI(uri)) }.getOrNull()
            ?: return attachmentUnavailable(reference, "附件引用无法解析")
    } else {
        File(uri)
    }
    if (!file.isFile) return attachmentUnavailable(reference, "附件文件已不可用")
    if (file.length() > MAX_INLINE_ATTACHMENT_BYTES) {
        return attachmentUnavailable(reference, "附件超出大小上限")
    }
    return copy(
        fileData = null,
        inlineData = Blob(
            mimeType = reference.mimeType,
            displayName = reference.displayName,
            data = file.readBytes(),
        ),
    )
}

/** 把失效的 `FileData` 段替换为模型可读的占位文本，保留"这里曾有哪个附件"的语义。 */
private fun Part.attachmentUnavailable(reference: FileData, reason: String): Part =
    copy(
        fileData = null,
        text = "[$reason，附件 ${reference.displayName ?: "未命名文件"} 未包含在本次请求中]",
    )

private fun String.isHttpReference(): Boolean =
    startsWith(HTTP_SCHEME, ignoreCase = true) || startsWith(HTTPS_SCHEME, ignoreCase = true)

private const val FILE_URI_SCHEME = "file:"
private const val HTTP_SCHEME = "http://"
private const val HTTPS_SCHEME = "https://"

/** 与发送侧 `MAX_DOCUMENT_REQUEST_BYTES` 同额度，防止历史重放时无上限读盘。 */
private const val MAX_INLINE_ATTACHMENT_BYTES = 50L * 1024 * 1024
