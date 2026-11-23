package github.ponyhuang.gimi.data.modelaccess

import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.conversation.model.WebCitationMetadata
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.LiveConnection
import com.google.adk.kt.annotations.ExperimentalLiveApi
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Part
import java.io.File
import java.net.URI
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.VendorFileReference
import github.ponyhuang.gimi.domain.modelcatalog.model.FileIngestMode
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
    /** 文档进入方式；FILE_REFERENCE 时本地文档引用不再内联（上传链路负责换成厂商引用）。 */
    private val documentIngestMode: FileIngestMode = FileIngestMode.INLINE,
    private val delegate: Model,
    private val audioDelegate: Lazy<Model>? = null,
) : Model {
    override val name: String = delegate.name

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
        val resolved = request.withoutSupersededStoppedReplies().resolveAttachmentParts(documentIngestMode)
        val selected = audioDelegate?.takeIf { resolved.hasAudioInput() }?.value ?: delegate
        return selected.generateContent(resolved, stream)
    }

    @OptIn(ExperimentalLiveApi::class)
    override suspend fun connect(request: LlmRequest): LiveConnection =
        delegate.connect(request.resolveAttachmentParts(documentIngestMode))
}

/** 重试当前轮时去掉停止快照；新问题仍能引用前一轮已经展示的正文。 */
private fun LlmRequest.withoutSupersededStoppedReplies(): LlmRequest {
    val marker = WebCitationMetadata.STOPPED_REPLY_KEY
    if (contents.none { content -> content.parts.any { marker in it.partMetadata.orEmpty() } }) return this

    var hasLaterUser = false
    var hasReplacement = false
    val history = contents.asReversed().mapNotNull { content ->
        if (content.role == Role.USER) {
            hasLaterUser = true
            hasReplacement = false
        }
        val replacement = content.parts.filterNot { marker in it.partMetadata.orEmpty() }
        val retained = if (hasLaterUser && !hasReplacement) content.parts else replacement
        if ((content.role == Role.MODEL || content.role == "assistant") && replacement.any {
            it.thought != true && !it.text.isNullOrBlank()
        }) hasReplacement = true
        content.copy(parts = retained).takeIf { retained.isNotEmpty() }
    }
    return copy(contents = history.asReversed())
}

private fun LlmRequest.hasAudioInput(): Boolean = contents.any { content ->
    content.parts.any { part ->
        val mimeType = part.inlineData?.mimeType ?: part.fileData?.mimeType
        mimeType?.startsWith("audio/", ignoreCase = true) == true
    }
}

private fun LlmRequest.resolveAttachmentParts(documentIngestMode: FileIngestMode): LlmRequest = copy(
    contents = contents.map { content ->
        content.copy(parts = content.parts.map { it.resolveAttachment(documentIngestMode) })
    },
)

private fun Part.resolveAttachment(documentIngestMode: FileIngestMode): Part {
    val reference = fileData ?: return this
    val uri = reference.fileUri ?: return attachmentUnavailable(reference, "附件引用缺少 URI")
    // 厂商文件引用直通：file_id 是唯一真源，重放不读本地文件、不上传、不做内容哈希。
    if (VendorFileReference.parse(uri) != null) return this
    if (uri.isHttpReference()) return this

    // 不做内容传输的本地文档（开关关闭的 PATH_TEXT、或 FILE_REFERENCE 下的历史草稿）
    // 改发本地路径文本：agent 决定是否用环境导入工具读取；文件已不存在时才降为占位文本。
    val deliversPathText = documentIngestMode == FileIngestMode.PATH_TEXT ||
        documentIngestMode == FileIngestMode.FILE_REFERENCE
    if (deliversPathText &&
        AttachmentCategory.from(reference.mimeType.orEmpty(), reference.displayName.orEmpty()) ==
            AttachmentCategory.DOCUMENT
    ) {
        val file = resolveLocalFile(uri)
            ?: return attachmentUnavailable(reference, "附件引用无法解析")
        if (!file.isFile) return attachmentUnavailable(reference, "附件文件已不可用")
        return copy(fileData = null, text = attachmentPathText(reference, file))
    }

    val file = resolveLocalFile(uri) ?: return attachmentUnavailable(reference, "附件引用无法解析")
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

private fun resolveLocalFile(uri: String): File? =
    if (uri.startsWith(FILE_URI_SCHEME, ignoreCase = true)) {
        runCatching { File(URI(uri)) }.getOrNull()
    } else {
        File(uri)
    }

/** 把失效的 `FileData` 段替换为模型可读的占位文本，保留"这里曾有哪个附件"的语义。 */
private fun Part.attachmentUnavailable(reference: FileData, reason: String): Part =
    copy(
        fileData = null,
        text = "[$reason，附件 ${reference.displayName ?: "未命名文件"} 未包含在本次请求中]",
    )

/** 文档以本地路径文本送达：保留路径、文件名与类型，agent 可用环境工具按需读取。 */
private fun attachmentPathText(reference: FileData, file: File): String =
    "[文档附件未随本次请求传输内容。本地路径：${file.absolutePath}" +
        "（文件名 ${reference.displayName ?: file.name}，类型 ${reference.mimeType ?: "未知"}）。" +
        "需要内容时可用环境文件导入工具读取该路径]"

private fun String.isHttpReference(): Boolean =
    startsWith(HTTP_SCHEME, ignoreCase = true) || startsWith(HTTPS_SCHEME, ignoreCase = true)

private const val FILE_URI_SCHEME = "file:"
private const val HTTP_SCHEME = "http://"
private const val HTTPS_SCHEME = "https://"

/** 与发送侧 `MAX_DOCUMENT_REQUEST_BYTES` 同额度，防止历史重放时无上限读盘。 */
private const val MAX_INLINE_ATTACHMENT_BYTES = 50L * 1024 * 1024
