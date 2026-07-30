package github.ponyhuang.gimi.domain.conversation.model

import java.util.UUID
import java.security.MessageDigest
import kotlin.time.Clock


/**
 * 消息角色：用于驱动气泡的显示样式和对齐方向。
 */
enum class MessageRole {
    /** 用户消息 —— 靠右，主题色背景 */
    User,

    /** AI 助手消息 —— 靠左，浅色背景 */
    Assistant
}

/**
 * 渲染相关的消息模型 — 对应 adk-web 的 UiEvent 字段最小集。
 *
 * @param id 唯一标识（与上游 `Event.id` 对齐；合并 partial 时保持不变）
 * @param invocationId 上游 invocation id，用于关联同一 turn 的多个 Event
 * @param author 上游 Event.author（"user" 或 agent 名）
 * @param role 派生出的气泡角色
 * @param textParts 文本分段（支持 thought / 普通文本分段，与 adk-web `textParts` 对齐）
 * @param functionCalls 工具调用列表
 * @param functionResponses 工具响应列表
 * @param error 错误信息（来自 Event.errorMessage / errorCode）
 * @param partial 当前 Event 块是否仍是 partial（流式累积未结束）
 * @param turnComplete 当前 turn 是否已结束
 * @param timestamp 毫秒时间戳
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val invocationId: String? = null,
    val author: String,
    val role: MessageRole,
    val textParts: List<TextPart> = emptyList(),
    val fileAttachments: List<FileAttachment> = emptyList(),
    val functionCalls: List<FunctionCallView> = emptyList(),
    val functionResponses: List<FunctionResponseView> = emptyList(),
    val error: String? = null,
    val partial: Boolean = false,
    val turnComplete: Boolean = false,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
)

/**
 * A validated image, audio, or document attached to a chat message.
 *
 * The original bytes are deliberately retained: ADK serializes inline data with the
 * session event, allowing a restored conversation to show the same attachment.
 */
data class FileAttachment(
    val mimeType: String,
    val data: ByteArray,
    val displayName: String = "",
    val sizeBytes: Long = data.size.toLong(),
    val payloadReference: String? = null,
    val category: AttachmentCategory = requireNotNull(
        AttachmentCategory.from(mimeType, displayName),
    ) { "Unsupported attachment type: $mimeType ($displayName)" },
    val id: String = stableAttachmentId(mimeType, displayName, data),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileAttachment

        if (mimeType != other.mimeType) return false
        if (!data.contentEquals(other.data)) return false
        if (id != other.id) return false
        if (displayName != other.displayName) return false
        if (sizeBytes != other.sizeBytes) return false
        if (payloadReference != other.payloadReference) return false
        if (category != other.category) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mimeType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + (payloadReference?.hashCode() ?: 0)
        result = 31 * result + category.hashCode()
        return result
    }
}

private fun stableAttachmentId(
    mimeType: String,
    displayName: String,
    data: ByteArray,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(mimeType.toByteArray())
    digest.update(0)
    digest.update(displayName.toByteArray())
    digest.update(0)
    return digest.digest(data).joinToString("") { byte -> "%02x".format(byte) }
}

/**
 * 文本分段 — 与 adk-web 的 `{ text, thought }` 对齐。
 *
 * @param id 稳定 id；用于把 reducer 产生的文本 delta 路由到对应的渲染订阅者
 *           （`StreamingMarkdownState`）。同一段文本在 partial 合并中保持 id 不变。
 * @param text 文本内容
 * @param thought 是否是思考段；与上一段 `thought` 不同时将开启新的分段
 */
data class TextPart(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val thought: Boolean = false,
)

/**
 * 工具调用视图（`FunctionCall` 的 UI 适配类型，避免把 ADK 的内部类型直接透到 UI 层）。
 *
 * @param id 工具调用 id（与 FunctionResponse 配对）
 * @param name 工具名
 * @param argsSummary 参数摘要字符串（用于 chip 显示）
 */
data class FunctionCallView(
    val id: String,
    val name: String,
    val argsSummary: String,
)

/**
 * 工具响应视图。
 *
 * @param id 工具调用 id（与 FunctionCall 配对）
 * @param name 工具名
 */
data class FunctionResponseView(
    val id: String,
    val name: String,
)

/**
 * 消息工厂方法。集中管理 `Message` 的常见构造方式，避免在 ViewModel 里重复字段。
 */
object Messages {

    /**
     * 用户消息 — 直接来自输入文本，无 thought、无工具调用、无错误。
     */
    fun fromUser(
        text: String,
        fileAttachments: List<FileAttachment> = emptyList(),
        id: String = UUID.randomUUID().toString(),
        timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ): Message = Message(
        id = id,
        author = "user",
        role = MessageRole.User,
        textParts = text.takeIf(String::isNotBlank)?.let {
            listOf(TextPart(text = it, thought = false))
        }.orEmpty(),
        fileAttachments = fileAttachments,
        timestamp = timestamp,
    )

    /**
     * 助手消息 — 由 reducer 构造，初始无文本，由后续 Event 填充。
     */
    fun fromAssistant(
        id: String = UUID.randomUUID().toString(),
        invocationId: String? = null,
        author: String = "assistant",
        timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ): Message = Message(
        id = id,
        invocationId = invocationId,
        author = author,
        role = MessageRole.Assistant,
        timestamp = timestamp,
    )

    /**
     * 错误消息 — 由 reducer 在收到带 errorCode / errorMessage 的 Event 时构造。
     */
    fun fromError(
        error: String,
        invocationId: String? = null,
        timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    ): Message = Message(
        invocationId = invocationId,
        author = "assistant",
        role = MessageRole.Assistant,
        error = error,
        textParts = emptyList(),
        timestamp = timestamp,
    )
}
