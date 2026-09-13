package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole

/**
 * 聊天列表中按展示职责拆分的稳定元素。
 */
sealed interface ChatListItem {
    /**
     * 单条用户消息。
     *
     * @property message 保留附件等完整内容的原始用户消息。
     */
    data class UserMessage(val message: Message) : ChatListItem

    /**
     * 紧随一条用户消息的 assistant 轮次。
     *
     * @property timeline 该轮过程活动与最终回答的派生视图。
     */
    data class AssistantTurn(val timeline: TurnTimeline) : ChatListItem
}

/**
 * 一个用户请求及其后续 assistant 活动的派生视图。
 *
 * @property turnId 所属用户消息 id，同时作为列表稳定 key。
 * @property startedAtMs 用户提交时间；缺失或非正数时为 null。
 * @property finishedAtMs 最后一个有效 assistant 事件时间；运行中或时长无效时为 null。
 * @property isRunning 当前轮次是否仍在运行。
 * @property isFailed 本轮是否包含错误消息。
 * @property entries 按事件顺序派生出的思考、工具和文件结果。
 * @property answerMessages 仍需作为正文气泡渲染的原始 assistant 消息。
 */
data class TurnTimeline(
    val turnId: String,
    val startedAtMs: Long?,
    val finishedAtMs: Long?,
    val isRunning: Boolean,
    val isFailed: Boolean,
    val entries: List<TimelineEntry>,
    val answerMessages: List<Message>,
)

/**
 * assistant 轮次内可折叠展示的过程条目。
 */
sealed interface TimelineEntry {
    /**
     * 一段模型思考文本。
     *
     * @property partId 流式文本分段的稳定 id。
     * @property text 当前已累积的思考文本。
     */
    data class Thought(
        val partId: String,
        val text: String,
    ) : TimelineEntry

    /**
     * 一次工具调用及其可证明状态。
     *
     * @property callId 工具调用 id。
     * @property name 工具函数名。
     * @property argsSummary 已脱敏并格式化的参数摘要。
     * @property status 由响应或按调用 id 记录的活动证据确定的状态。
     */
    data class ToolCall(
        val callId: String,
        val name: String,
        val argsSummary: String,
        val status: ToolCallStatus,
    ) : TimelineEntry

    /**
     * 工具返回的结构化本地文件结果。
     *
     * @property response 携带非空文件列表的工具响应。
     */
    data class FileResults(
        val response: FunctionResponseView,
    ) : TimelineEntry
}

/** 工具调用在时间线中可展示的状态。 */
enum class ToolCallStatus {
    Running,
    Completed,
    Rejected,
    AwaitingConfirmation,
    Unknown,
}

/**
 * 工具状态关联键，避免相同名称的多次调用互相覆盖。
 *
 * @property callId 工具调用 id。
 * @property name 工具函数名。
 */
internal data class ToolCallKey(
    val callId: String,
    val name: String,
)

/**
 * 从会话运行时传入纯派生函数的瞬时活动状态。
 *
 * @property isAgentRunning 当前会话是否仍在执行 assistant 轮次。
 * @property toolStatuses 以调用 id 与名称共同关联的显式工具状态。
 */
internal data class TimelineActivityState(
    val isAgentRunning: Boolean = false,
    val toolStatuses: Map<ToolCallKey, ToolCallStatus> = emptyMap(),
)

/**
 * 把原始消息按用户轮次派生为聊天列表元素。
 *
 * 原始 [Message] 不被修改；实时流式与历史回放都通过本函数获得一致的分组、去重、
 * 协议过滤和状态降级语义。
 */
internal fun List<Message>.toChatListItems(
    activityState: TimelineActivityState,
): List<ChatListItem> {
    val result = mutableListOf<ChatListItem>()
    val lastUserIndex = indexOfLast { it.role == MessageRole.User }
    var currentUser: Message? = null
    var currentUserIndex = -1
    val assistantMessages = mutableListOf<Message>()

    fun flushTurn() {
        val user = currentUser ?: return
        val isCurrentTurn = currentUserIndex == lastUserIndex
        val isRunning = isCurrentTurn && activityState.isAgentRunning
        if (assistantMessages.isEmpty() && !isRunning) return
        result += ChatListItem.AssistantTurn(
            assistantMessages.toTurnTimeline(
                user = user,
                isRunning = isRunning,
                toolStatuses = activityState.toolStatuses,
            ),
        )
        assistantMessages.clear()
    }

    forEachIndexed { index, message ->
        when (message.role) {
            MessageRole.User -> {
                flushTurn()
                currentUser = message
                currentUserIndex = index
                result += ChatListItem.UserMessage(message)
            }
            MessageRole.Assistant -> if (currentUser != null) {
                assistantMessages += message
            }
        }
    }
    flushTurn()
    return result
}

private fun List<Message>.toTurnTimeline(
    user: Message,
    isRunning: Boolean,
    toolStatuses: Map<ToolCallKey, ToolCallStatus>,
): TurnTimeline {
    val selectedResponses = selectToolResponses()
    val historicalStatuses = historicalConfirmationStatuses()
    val callsByKey = flatMap { it.visibleFunctionCalls() }
        .associateBy { ToolCallKey(it.id, it.name) }
    val entries = mutableListOf<TimelineEntry>()
    val seenToolEntries = mutableSetOf<ToolCallKey>()
    val seenFileResults = mutableSetOf<ToolCallKey>()

    forEach { message ->
        message.textParts
            .filter { it.thought && it.text.isNotBlank() }
            .forEach { part -> entries += TimelineEntry.Thought(part.id, part.text) }

        message.visibleFunctionCalls().forEach { call ->
            val key = ToolCallKey(call.id, call.name)
            if (seenToolEntries.add(key)) {
                entries += TimelineEntry.ToolCall(
                    callId = call.id,
                    name = call.name,
                    argsSummary = call.argsSummary,
                    status = if (key in selectedResponses) {
                        ToolCallStatus.Completed
                    } else {
                        toolStatuses[key] ?: historicalStatuses[key] ?: ToolCallStatus.Unknown
                    },
                )
            }
        }

        message.visibleFunctionResponses().forEach { response ->
            val key = ToolCallKey(response.id, response.name)
            if (key !in callsByKey && seenToolEntries.add(key)) {
                entries += TimelineEntry.ToolCall(
                    callId = response.id,
                    name = response.name,
                    argsSummary = "",
                    status = ToolCallStatus.Completed,
                )
            }
            val selected = selectedResponses[key]
            if (selected?.hasStructuredResult() == true && seenFileResults.add(key)) {
                entries += TimelineEntry.FileResults(selected)
            }
        }
    }

    val startedAtMs = user.timestamp.takeIf { it > 0L }
    val lastAssistantTimestamp = lastOrNull { it.timestamp > 0L }?.timestamp
    val finishedAtMs = if (!isRunning && startedAtMs != null &&
        lastAssistantTimestamp != null && lastAssistantTimestamp > startedAtMs
    ) {
        lastAssistantTimestamp
    } else {
        null
    }
    return TurnTimeline(
        turnId = user.id,
        startedAtMs = startedAtMs,
        finishedAtMs = finishedAtMs,
        isRunning = isRunning,
        isFailed = any { it.error != null },
        entries = entries,
        answerMessages = filter(Message::hasAnswerContent),
    )
}

private fun List<Message>.historicalConfirmationStatuses(): Map<ToolCallKey, ToolCallStatus> {
    val originalCallsByConfirmationId = flatMap(Message::functionCalls)
        .filter { it.name == ConfirmationToolName }
        .mapNotNull { call ->
            val originalCallId = call.confirmationOriginalCallId ?: return@mapNotNull null
            val originalToolName = call.confirmationOriginalToolName ?: return@mapNotNull null
            call.id to ToolCallKey(originalCallId, originalToolName)
        }
        .toMap()
    return flatMap(Message::functionResponses)
        .filter { it.name == ConfirmationToolName && it.confirmationApproved == false }
        .mapNotNull { response ->
            originalCallsByConfirmationId[response.id]?.let { it to ToolCallStatus.Rejected }
        }
        .toMap()
}

private fun List<Message>.selectToolResponses(): Map<ToolCallKey, FunctionResponseView> {
    val selected = linkedMapOf<ToolCallKey, FunctionResponseView>()
    forEach { message ->
        message.visibleFunctionResponses().forEach { response ->
            val key = ToolCallKey(response.id, response.name)
            val previous = selected[key]
            if (previous == null || (!previous.hasStructuredResult() && response.hasStructuredResult())) {
                selected[key] = response
            }
        }
    }
    return selected
}

private fun FunctionResponseView.hasStructuredResult(): Boolean =
    localFileSearchResult?.files?.isNotEmpty() == true

private fun Message.hasAnswerContent(): Boolean =
    error != null ||
        textParts.any { !it.thought && it.text.isNotBlank() } ||
        fileAttachments.isNotEmpty()
