package github.ponyhuang.gimi.data.agent.execution

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Part
import java.io.File
import java.net.URI
import kotlinx.coroutines.flow.Flow

/**
 * 在模型执行边界把持久化的附件引用解析为仅供本次请求使用的内联 [Part]。
 *
 * ADK 会话继续保存轻量的 `FileData` 引用；厂商模型收到的请求副本只包含已经解析好的
 * `Part.inlineData` 或明确的 HTTP(S) 远程引用，避免协议适配器感知本地文件系统。
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
    val uri = reference.fileUri
        ?: throw IllegalArgumentException("Attachment reference URI is missing")
    if (uri.isHttpReference()) return this

    val file = if (uri.startsWith(FILE_URI_SCHEME, ignoreCase = true)) {
        runCatching { File(URI(uri)) }
            .getOrElse { throw IllegalArgumentException("Invalid attachment file URI: $uri", it) }
    } else {
        File(uri)
    }
    require(file.isFile) { "Attachment payload is unavailable: $uri" }
    return copy(
        fileData = null,
        inlineData = Blob(
            mimeType = reference.mimeType,
            displayName = reference.displayName,
            data = file.readBytes(),
        ),
    )
}

private fun String.isHttpReference(): Boolean =
    startsWith(HTTP_SCHEME, ignoreCase = true) || startsWith(HTTPS_SCHEME, ignoreCase = true)

private const val FILE_URI_SCHEME = "file:"
private const val HTTP_SCHEME = "http://"
private const val HTTPS_SCHEME = "https://"
