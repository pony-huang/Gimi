package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.usecase.ChatRunEventMapper
import github.ponyhuang.gimi.domain.conversation.usecase.summarizeValue
import github.ponyhuang.gimi.domain.conversation.usecase.toView
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 单条工具确认请求在确认卡片上展示的数据。
 *
 * @property confirmationCallId ADK function call 的 id，回复确认时用于定位请求。
 * @property toolName 被确认的工具名。
 * @property description 工具描述（来自工具授权仓库，缺失时为空串）。
 * @property arguments 参数摘要（多行 `key: value`，敏感键遮蔽为 `••••`）。
 */
data class PendingToolConfirmation(
    val confirmationCallId: String,
    val toolName: String,
    val description: String,
    val arguments: String,
)

/**
 * ADK `ChatRunEvent` → UI `Message` 的事件归约器。
 *
 * 从 [ChatViewModel] 拆出的纯归约逻辑：partial 事件按 author/invocationId 合并进上一条消息，
 * 完整事件经 [ChatRunEventMapper] 构造新消息；同时维护运行时阶段、工具确认捕获与错误落地。
 * 依赖以函数/引用注入，便于在 JVM 测试中替换。
 *
 * @property runtimeOrNull 查询已有会话运行时（不创建）；用于 runToken 归属校验。
 * @property runtimeFor 取运行时，缺失时创建。
 * @property publishRuntime 把运行时发布到 UI state。
 * @property emitPartDelta 把 partial 文本增量推给对应 [TextPart] 的 channel。
 * @property scope 用于派发 lease 阶段更新与会话刷新等副作用。
 * @property repository 会话仓库，turn 完成后刷新会话内容。
 * @property toolAuthorization 工具授权仓库，提供工具描述。
 * @property isAutoApproved 工具是否在本轮自动放行。
 */
internal class AgentEventReducer(
    private val runtimeOrNull: (String) -> ChatSessionRuntime?,
    private val runtimeFor: (String) -> ChatSessionRuntime,
    private val publishRuntime: (ChatSessionRuntime) -> Unit,
    private val emitPartDelta: (sessionId: String, partId: String, delta: String) -> Unit,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val toolAuthorization: ToolAuthorizationRepository,
    private val isAutoApproved: (String) -> Boolean,
) {
    /**
     * 顶层 reducer：partial 合并，否则当作完整事件构造新的 `Message`。
     */
    fun applyEvent(sessionId: String, event: ChatRunEvent, runToken: Any) {
        if (runtimeOrNull(sessionId)?.runToken !== runToken) return
        // 错误优先：errorCode / errorMessage 非空 → 错误消息。
        val errMsg = event.errorMessage
        if (event.errorCode != null || !errMsg.isNullOrBlank()) {
            applyError(sessionId, errMsg ?: event.errorCode ?: "Unknown error", event.invocationId)
            return
        }

        if (event.partial) {
            mergePartialEvent(sessionId, event)
        } else {
            appendCompleteEvent(sessionId, event)
        }
        applyAgentRunEvent(sessionId, event)
        captureToolConfirmation(sessionId, event)
    }

    private fun applyAgentRunEvent(sessionId: String, event: ChatRunEvent) {
        val runtime = runtimeFor(sessionId)
        val phase = when {
            event.functionCalls.any { it.confirmationRequest == null } -> AgentTaskPhase.EXECUTING_TOOL
            event.functionResponses.isNotEmpty() -> AgentTaskPhase.GENERATING
            else -> null
        }
        if (phase != null) {
            runtime.phase = phase
            scope.launch { runtime.lease?.updatePhase(phase) }
        }
        val status = AgentRunStatus(
            isRunning = runtime.isAgentRunning,
            turnComplete = runtime.turnComplete,
        ).afterEvent(
            partial = event.partial,
            turnComplete = event.turnComplete,
        )
        runtime.isAgentRunning = status.isRunning
        runtime.turnComplete = status.turnComplete
        publishRuntime(runtime)
        if (event.turnComplete) {
            scope.launch { repository.refreshConversation(sessionId) }
        }
    }

    /** Extracts queued ADK confirmation requests without allowing later calls to overwrite them. */
    private fun captureToolConfirmation(sessionId: String, event: ChatRunEvent) {
        val runtime = runtimeFor(sessionId)
        val incoming = event.functionCalls.mapNotNull { call ->
            val confirmationId = call.id ?: return@mapNotNull null
            val request = call.confirmationRequest ?: return@mapNotNull null
            val description = toolAuthorization.tools.value
                .firstOrNull { it.id == request.toolName }
                ?.description
                ?: ""
            PendingToolConfirmation(
                confirmationCallId = confirmationId,
                toolName = request.toolName,
                description = description,
                arguments = request.args.entries.joinToString(separator = "\n") { (key, value) ->
                    "$key: ${summarizeConfirmationArgument(key, value)}"
                },
            )
        }
        if (incoming.isEmpty()) return
        // 完全批准或「总是允许」白名单命中的确认走自动放行通道：只记入 autoApprovedConfirmations
        // 等流结束后静默回复 ADK；绝不能进 pendingToolConfirmations——它直接驱动确认卡片渲染，
        // 混进去会出现"卡片闪一下再自动关闭"的观感。
        val (autoApproved, needsUser) = incoming.partition { isAutoApproved(it.toolName) }
        runtime.autoApprovedConfirmations = (runtime.autoApprovedConfirmations + autoApproved)
            .distinctBy { it.confirmationCallId }
        if (needsUser.isNotEmpty()) {
            runtime.phase = AgentTaskPhase.WAITING_FOR_CONFIRMATION
            scope.launch {
                runtime.lease?.updatePhase(AgentTaskPhase.WAITING_FOR_CONFIRMATION)
            }
            val knownIds = runtime.pendingToolConfirmations.mapTo(mutableSetOf()) {
                it.confirmationCallId
            }
            runtime.pendingToolConfirmations += needsUser.filter {
                            knownIds.add(it.confirmationCallId)
                        }
        }
        runtime.isAgentRunning = true
        publishRuntime(runtime)
    }

    fun applyError(sessionId: String, message: String, invocationId: String? = null) {
        val runtime = runtimeFor(sessionId)
        clearToolConfirmationState(runtime)
        runtime.messages = runtime.messages + Messages.fromError(error = message, invocationId = invocationId)
        runtime.isAgentRunning = false
        runtime.turnComplete = false
        runtime.failed = true
        publishRuntime(runtime)
    }

    private fun clearToolConfirmationState(runtime: ChatSessionRuntime) {
        runtime.approvedToolsThisTurn.clear()
        runtime.pendingToolConfirmations = emptyList()
        runtime.autoApprovedConfirmations = emptyList()
        publishRuntime(runtime)
    }

    /**
     * Partial 事件合并：必须满足"上一条也是 partial + 同 author"才合并。
     * 否则作为新消息起一段（保留 partial 流被打断时的鲁棒性）。
     */
    private fun mergePartialEvent(sessionId: String, event: ChatRunEvent) {
        val runtime = runtimeFor(sessionId)
        val author = event.author
        val role = authorToRole(author)

        val currentMessages = runtime.messages
        val existingIndex = currentMessages.indexOfLast { msg ->
            msg.partial &&
                msg.author == author &&
                msg.role == role &&
                msg.invocationId == event.invocationId
        }

        if (existingIndex < 0) {
            // 没有可合并的上一条 — 当成完整事件起一段。
            appendCompleteEvent(sessionId, event)
            return
        }

        val updated = mergeInto(sessionId, currentMessages[existingIndex], event)
        runtime.messages = runtime.messages.toMutableList().also { it[existingIndex] = updated }
        publishRuntime(runtime)
    }

    /**
     * 完整事件（非 partial） — 走 [ChatRunEventMapper.fromEvent] 构造 [Message]，空 Event 跳过。
     *
     * 流式收尾时,已经有一条同 author + invocationId 的 partial message 在 `_uiState.messages` 尾部
     * (`mergePartialEvent` 累积 typewriter 文本用的就是这一条);此时若再 append 一条
     * non-partial 完整 message,UI 会看到重复文本。因此这里检测 partial 尾部并就地 replace,
     * 用 final event 的稳定 id 替换 partial message,既保留打字机视觉效果又避免重复气泡。
     *
     * **就地翻标志位**(不要整体替换):原实现用 `buildMessageFromParts(event)` 返回的新 Message 整体
     * 替换 partial message,新 Message 的 `TextPart.id` 由 `finalEvent.id` 派生,与 partial 阶段累积
     * 用的 `TextPart.id` 不同,导致 `partChannelProvider(part.id)` 在收尾瞬间查不到 channel,
     * `ChatTextContent` 的 `partial` 分支条件不成立,会从 streaming 切到 static,触发整段 markdown
     * 重 parse / 重布局 — 气泡闪一下。这里改为 `old.copy(partial = false, turnComplete = ...)`,
     * 保留 `TextPart.id`,channel 订阅继续命中,ChatTextContent 不切分支。
     */
    private fun appendCompleteEvent(sessionId: String, event: ChatRunEvent) {
        val runtime = runtimeFor(sessionId)
        val message = buildMessageFromParts(event) ?: return
        val current = runtime.messages
        val mergeIndex = current.indexOfLast { msg ->
            msg.partial &&
                msg.author == event.author &&
                msg.invocationId == event.invocationId
        }
        if (mergeIndex >= 0) {
            // 流式收尾:就地翻 partial 标志位,保留原 Message.id / TextPart.id / channel 订阅。
            // 完整事件携带的 functionCalls / functionResponses（SSE 下调用经 partial 增量合入,
            // 工具结果只随完整事件到达）不能像文本那样丢弃——本地文件轮播、远程图片轮播
            // 都渲染在 functionResponses 上,丢了响应事件 = 找到文件也不出图。按 (id, name)
            // 去重并入,避免 partial 阶段已合入的调用被重复追加。
            runtime.messages = runtime.messages.toMutableList().also {
                val old = it[mergeIndex]
                it[mergeIndex] = old.copy(
                    partial = false,
                    turnComplete = message.turnComplete,
                    functionCalls = old.functionCalls + message.functionCalls.filter { call ->
                        old.functionCalls.none { it.id == call.id && it.name == call.name }
                    },
                    functionResponses = old.functionResponses + message.functionResponses.filter { response ->
                        old.functionResponses.none { it.id == response.id && it.name == response.name }
                    },
                )
            }
        } else {
            // 首个 partial 尚没有可合并的消息。EventMapper 直接构造了完整的
            // TextPart，因此必须在发布 UI state 前把它作为初始 chunk 入队；否则
            // 下一段 partial 才创建 channel 时，StreamingMarkdownState 只会收到
            // 下一段文本，导致首段文字在流式渲染中丢失。
            if (message.partial) {
                message.textParts.forEach { part ->
                    emitPartDelta(sessionId, part.id, part.text)
                }
            }
            runtime.messages = runtime.messages + message
        }
        publishRuntime(runtime)
    }

    /**
     * 把单个 Event 映射为 Message（走 [ChatRunEventMapper]，与历史回放共用同一映射规则）。
     * 返回 null 表示 Event 内容为空（无 text part / 无 tool call / 无 error），跳过。
     */
    private fun buildMessageFromParts(event: ChatRunEvent): Message? =
        ChatRunEventMapper.fromEvent(event)

    /**
     * 把 partial Event 的所有 part 合并进已有 message；tool calls / responses 追加。
     *
     * reducer 的合并阶段仍按 part-by-part 累积（保留 streaming typewriter 语义），
     * [ChatRunEventMapper] 只负责"完整 Event → Message"的入口（保证历史回放复用）。
     */
    private fun mergeInto(sessionId: String, message: Message, event: ChatRunEvent): Message {
        val parts = event.parts
        var working = message
        parts.forEachIndexed { index, part ->
            val text = part.text
            if (!text.isNullOrEmpty()) {
                val thought = part.thought
                working = appendTextPart(sessionId, working, event, index, text, thought)
            }
        }
        val newCalls = event.functionCalls.map { it.toView() }
        val newResponses = event.functionResponses.map { it.toView() }
        if (newCalls.isNotEmpty() || newResponses.isNotEmpty()) {
            working = working.copy(
                functionCalls = working.functionCalls + newCalls,
                functionResponses = working.functionResponses + newResponses,
            )
        }
        return working.copy(
            partial = true,
            turnComplete = event.turnComplete,
        )
    }

    /**
     * 与 adk-web `addTextToParts` 等价：
     * - 若末段的 `thought` 标志与本次相同 → 追加；
     * - 否则新建段。
     * - 同时把新增的文本作为 delta 推到对应 TextPart 的 channel，供渲染端做增量 markdown 解析。
     */
    private fun appendTextPart(
        sessionId: String,
        message: Message,
        event: ChatRunEvent,
        partIndex: Int,
        text: String,
        thought: Boolean,
    ): Message {
        if (text.isEmpty()) return message
        val parts = message.textParts.toMutableList()
        val last = parts.lastOrNull()
        if (last != null && last.thought == thought) {
            parts[parts.lastIndex] = last.copy(text = last.text + text)
            emitPartDelta(sessionId, last.id, text)
        } else {
            val newPart = TextPart(
                id = "${event.id}:$partIndex",
                text = text,
                thought = thought,
            )
            parts += newPart
            emitPartDelta(sessionId, newPart.id, text)
        }
        return message.copy(textParts = parts)
    }

    private fun authorToRole(author: String): MessageRole =
        if (author == "user") MessageRole.User else MessageRole.Assistant
}

private fun summarizeConfirmationArgument(key: String, value: Any?): String {
    val sensitiveKey = listOf(
        "phone",
        "contact",
        "message",
        "text",
        "content",
        "uri",
        "path",
        "file",
        "email",
        "token",
        "key",
    ).any { marker -> key.contains(marker, ignoreCase = true) }
    return if (sensitiveKey) "••••" else summarizeValue(value).take(120)
}
