package github.ponyhuang.gimi.data.conversation.mapper

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.domain.conversation.model.FunctionCallView
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.parseLocalFileSearchResult
import github.ponyhuang.gimi.domain.conversation.model.parseRemoteImageResult
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import java.io.File

/**
 * ADK `Event` ↔ UI `Message` 的映射工具。
 *
 * `ChatViewModel` 在 streaming 时按"partial → merge → addTextToParts"reducer 构造 `Message`；
 * 进程重启后从 Room 拉回 `Session.events` 时同样要还原成 `Message` 喂给 UI —
 * 两条路径 MUST 共用同一映射规则，保证 `Event.id → Message.id`、`TextPart.id` 在运行时与历史回放时保持一致。
 *
 * 设计：
 * - `Message.id` = `Event.id`（不是新生成的 UUID），让 Compose LazyColumn 的 `key = message.id` 在重渲染时稳定。
 * - `TextPart.id` 由 `Event.id + ":" + partIndex` 派生；同一 Event 在 reducer 与 repository 路径下生成相同的 id。
 * - 空内容 Event（无 text part、无 tool call/response、无 error）返回 `null`，调用方负责 filter — 与 `ChatViewModel.appendCompleteEvent` 的"空 Event 跳过"行为一致。
 */
object EventMapper {

    /**
     * 单个 Event → Message。
     *
     * 规则：
     * - 错误事件（`errorCode` 或 `errorMessage` 非空）→ Message with `error` 字段，返回非 null。
     * - user author → `Messages.fromUser(text = 首段 textPart.text)`。
     * - 其它 author（agent 名）→ `Messages.fromAssistant(...)` + 逐 part 累积 `textParts` + 追加 `functionCalls` / `functionResponses`。
     * - 内容为空的 Event（无 text part、无 tool call/response、无 error）→ 返回 `null`。
     */
    fun fromEvent(event: Event): Message? {
        // 错误优先：与 ChatViewModel.applyEvent 的"错误优先"分支对齐。
        val errMsg = event.errorMessage
        if (event.errorCode != null || !errMsg.isNullOrBlank()) {
            return Message(
                id = event.id,
                invocationId = event.invocationId,
                author = "assistant",
                role = MessageRole.Assistant,
                error = errMsg ?: event.errorCode ?: "Unknown error",
                textParts = emptyList(),
                timestamp = event.timestamp,
            )
        }

        val parts = event.content?.parts.orEmpty()
        val calls = event.functionCalls()
        val responses = event.functionResponses()

        if (parts.isEmpty() && calls.isEmpty() && responses.isEmpty()) {
            // 空 Event（turnComplete 标记等）— 不渲染。
            return null
        }

        // ADK 工具确认的选择以 role=user 的 FunctionResponse 回送给 runner。
        // 它不是用户可读的聊天内容；若仍构造 User Message，聊天气泡会因固定内边距
        // 被绘制成没有内容的灰色圆角块。只含 function response 的 user event 直接忽略。
        if (event.author == "user" &&
            parts.all { it.text.isNullOrEmpty() && it.inlineData == null } &&
            calls.isEmpty() &&
            responses.isNotEmpty()
        ) {
            return null
        }

        return if (event.author == "user") {
            buildUserMessage(event, parts)
        } else {
            buildAssistantMessage(event, parts, calls, responses)
        }
    }

    /**
     * 把 Session 中所有 events 还原为 `List<Message>`，按 `session.events` 的存储顺序（即 Room DAO 查询顺序），
     * 过滤掉 [fromEvent] 返回 null 的项。
     *
     * **不要** 重新按 `timestamp` 排序：
     * - Room DAO 用 `ORDER BY timestamp ASC, id ASC` 拉取；ties 走 `Event.id`（随机 UUID）。
     * - 如果再 `sortedBy { it.timestamp }`，会丢掉 `id` 那个 tie-breaker，反而引入非确定性。
     *   DAO 查询结果 = append 顺序 = runner emit 顺序 = 用户 streaming 时看到的顺序。
     */
    fun fromSession(session: Session): List<Message> =
        session.events
            .mapNotNull { fromEvent(it) }

    // ── 内部 helper ─────────────────────────────────────────────────────

    private fun buildUserMessage(event: Event, parts: List<Part>): Message {
        // user 消息按现有 Messages.fromUser 构造，textParts 只取首个非空 text（与 ChatViewModel 一致）。
        val firstText = parts.firstNotNullOfOrNull { it.text?.takeIf(String::isNotEmpty) }
            ?: ""
        return Message(
            id = event.id,
            invocationId = event.invocationId,
            author = "user",
            role = MessageRole.User,
            textParts = if (firstText.isEmpty()) emptyList() else listOf(textPartFor(event, 0, firstText, thought = false)),
            fileAttachments = parts.mapNotNull { part ->
                part.toFileAttachment()
            },
            partial = false,
            turnComplete = true,
            timestamp = event.timestamp,
        )
    }

    private fun Part.toFileAttachment(): FileAttachment? {
        inlineData?.let { blob ->
            val mimeType = requireNotNull(blob.mimeType) { "Attachment MIME type is missing" }
            val data = requireNotNull(blob.data) { "Attachment payload is missing" }
            return FileAttachment(
                mimeType = mimeType,
                data = data,
                displayName = blob.displayName.orEmpty(),
            )
        }
        fileData?.let { file ->
            val mimeType = requireNotNull(file.mimeType) { "Attachment MIME type is missing" }
            val reference = requireNotNull(file.fileUri) { "Attachment reference is missing" }
            val payload = File(reference.removePrefix("file://"))
            require(payload.isFile) { "Attachment payload is unavailable: $reference" }
            return FileAttachment(
                mimeType = mimeType,
                data = payload.readBytes(),
                displayName = file.displayName.orEmpty(),
                sizeBytes = payload.length(),
                payloadReference = payload.absolutePath,
            )
        }
        return null
    }

    private fun buildAssistantMessage(
        event: Event,
        parts: List<Part>,
        calls: List<FunctionCall>,
        responses: List<FunctionResponse>,
    ): Message {
        var working = Messages.fromAssistant(
            id = event.id,
            invocationId = event.invocationId,
            author = event.author,
            timestamp = event.timestamp,
        )
        parts.forEachIndexed { index, part ->
            val text = part.text
            if (!text.isNullOrEmpty()) {
                val thought = part.thought == true
                working = appendTextPart(working, event, index, text, thought)
            }
        }
        val callViews = calls.map { it.toView() }
        val responseViews = responses.map { it.toView() }
        if (callViews.isNotEmpty() || responseViews.isNotEmpty()) {
            working = working.copy(
                functionCalls = working.functionCalls + callViews,
                functionResponses = working.functionResponses + responseViews,
            )
        }
        return working.copy(
            partial = event.partial,
            turnComplete = event.turnComplete,
        )
    }

    /**
     * 与 adk-web `addTextToParts` 等价：末段 `thought` 标志相同 → 追加；异则新建段。
     *
     * `TextPart.id` 由 `Event.id + ":" + partIndex` 派生，确保 reducer 与 repository 共用映射。
     */
    private fun appendTextPart(
        message: Message,
        event: Event,
        partIndex: Int,
        text: String,
        thought: Boolean,
    ): Message {
        val parts = message.textParts.toMutableList()
        val last = parts.lastOrNull()
        if (last != null && last.thought == thought) {
            parts[parts.lastIndex] = last.copy(text = last.text + text)
        } else {
            parts += textPartFor(event, partIndex, text, thought)
        }
        return message.copy(textParts = parts)
    }

    private fun textPartFor(event: Event, partIndex: Int, text: String, thought: Boolean): TextPart =
        TextPart(
            id = "${event.id}:$partIndex",
            text = text,
            thought = thought,
        )

    // ── ADK 类型 → UI 视图 ──────────────────────────────────────────────
    private fun FunctionCall.toView(): FunctionCallView {
        if (name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) {
            return FunctionCallView(id = id.orEmpty(), name = name, argsSummary = "")
        }
        val argsText = if (args.isEmpty()) "" else args.entries.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", ",
        ) { (k, v) -> "$k=${summarizeValue(v)}" }
        return FunctionCallView(id = id.orEmpty(), name = name, argsSummary = argsText)
    }

    private fun FunctionResponse.toView(): FunctionResponseView =
        FunctionResponseView(
            id = id.orEmpty(),
            name = name,
            localFileSearchResult = parseLocalFileSearchResult(name, response),
            remoteImageResult = parseRemoteImageResult(response),
        )

    private fun summarizeValue(v: Any?): String = when (v) {
        null -> "null"
        is String -> if (v.length > 16) "\"${v.take(15)}…\"" else "\"$v\""
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> "{…}"
        is List<*> -> "[…]"
        else -> v.toString()
    }
}
