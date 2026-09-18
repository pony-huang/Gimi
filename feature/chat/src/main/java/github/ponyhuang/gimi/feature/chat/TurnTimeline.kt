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
 * @property isRunning 当前轮次是否仍在运行。
 * @property segments 按事件顺序交替排列的正文段与工具活动组段。
 */
data class TurnTimeline(
    val turnId: String,
    val isRunning: Boolean,
    val segments: List<TurnSegment>,
)

/**
 * assistant 轮次内按时间顺序排列的一个展示分段。
 */
sealed interface TurnSegment {
    /**
     * 一段助手正文。保留原始 [Message] 以继续驱动流式打字机 channel、附件与错误渲染。
     *
     * @property message 含非思考文本、附件或错误的原始 assistant 消息。
     */
    data class Answer(val message: Message) : TurnSegment

    /**
     * 两段正文之间聚合的连续工具活动，折叠为一行「执行工具 N 次」。
     *
     * @property id 组内稳定 id（turnId + 组序号），作为展开态 key。
     * @property entries 组内按事件顺序排列的思考、工具调用和文件结果。
     */
    data class Activity(
        val id: String,
        val entries: List<TimelineEntry>,
    ) : TurnSegment
}

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
    val segments = mutableListOf<TurnSegment>()
    val activityEntries = mutableListOf<TimelineEntry>()
    val seenToolEntries = mutableSetOf<ToolCallKey>()
    val seenFileResults = mutableSetOf<ToolCallKey>()
    var activityGroupCount = 0

    // 思考文本不单独切组：纯思考缓冲并入后续工具组，避免正文前出现「0 次工具」的孤立折叠行；
    // 只有出现真实工具活动后才在正文前 flush。轮次末尾 force flush 兜底展示落单的思考。
    fun flushActivity(force: Boolean = false) {
        if (activityEntries.isEmpty()) return
        if (!force && activityEntries.all { it is TimelineEntry.Thought }) return
        segments += TurnSegment.Activity(
            id = "${user.id}:a$activityGroupCount",
            entries = activityEntries.toList(),
        )
        activityGroupCount += 1
        activityEntries.clear()
    }

    forEach { message ->
        message.textParts
            .filter { it.thought && it.text.isNotBlank() }
            .forEach { part -> activityEntries += TimelineEntry.Thought(part.id, part.text) }

        if (message.hasAnswerContent()) {
            flushActivity()
            segments += TurnSegment.Answer(message)
        }

        message.visibleFunctionCalls().forEach { call ->
            val key = ToolCallKey(call.id, call.name)
            if (seenToolEntries.add(key)) {
                activityEntries += TimelineEntry.ToolCall(
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
                activityEntries += TimelineEntry.ToolCall(
                    callId = response.id,
                    name = response.name,
                    argsSummary = "",
                    status = ToolCallStatus.Completed,
                )
            }
            val selected = selectedResponses[key]
            if (selected?.hasStructuredResult() == true && seenFileResults.add(key)) {
                activityEntries += TimelineEntry.FileResults(selected)
            }
        }
    }
    flushActivity(force = true)

    return TurnTimeline(
        turnId = user.id,
        isRunning = isRunning,
        segments = segments,
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
