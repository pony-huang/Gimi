package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.FunctionCallView
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole

/**
 * ADK 确认协议的内部工具名（等价于 ADK `FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME`；
 * feature 层不依赖 ADK，故复制字面量）。它只是"请求用户确认"的信令，确认结果已由
 * ToolConfirmationCard 单独呈现，chip 渲染与可见性判断都应把它过滤掉。
 */
internal const val ConfirmationToolName = "adk_request_confirmation"

/**
 * 动态工具检索的内部工具名。检索调用和响应必须保留在 ADK session 中恢复声明选择，
 * 但不属于面向用户的工具执行活动，因此聊天界面不渲染它。
 */
internal const val ToolSearchProtocolName = "tool_search"

private val HiddenProtocolToolNames = setOf(ConfirmationToolName, ToolSearchProtocolName)

/** 过滤掉内部协议信令后的工具调用列表。 */
internal fun Message.visibleFunctionCalls(): List<FunctionCallView> =
    functionCalls.filterNot { it.name in HiddenProtocolToolNames }

/** 过滤掉内部协议信令后的工具响应列表。 */
internal fun Message.visibleFunctionResponses(): List<FunctionResponseView> =
    functionResponses.filterNot { it.name in HiddenProtocolToolNames }

/** Whether a message has any content that can be rendered with the current display preference. */
internal fun Message.isVisibleInChat(showToolActivity: Boolean): Boolean =
    error != null ||
        textParts.isNotEmpty() ||
        fileAttachments.isNotEmpty() ||
        functionResponses.any { it.localFileSearchResult?.files?.isNotEmpty() == true } ||
        functionResponses.any { it.remoteImageResult?.images?.isNotEmpty() == true } ||
        (showToolActivity && (visibleFunctionCalls().isNotEmpty() || visibleFunctionResponses().isNotEmpty()))

/**
 * 把"只含工具响应"的消息折叠进上一条消息，并按 `(id, name)` 去重。
 *
 * ADK 事件流里工具的 call 与 response 是两条独立 Event（确认流程下还跨两次 run），
 * 直接渲染会出现"灰 call chip 一行 + 蓝 ✓ chip 一行"的双行噪声；确认恢复时同一
 * response 可能被投递两次，叠出重复 ✓。折叠进同一条 Message 后，MessageBubble
 * 的按 id 配对逻辑才能把 call/response 合成单个状态 chip。
 *
 * 注意调用顺序：必须放在 `filter(isVisibleInChat)` 之后，否则夹在中间的
 * 确认信令消息会成为折叠目标，真正的 call 消息反而配对不上。
 */
internal fun List<Message>.foldToolResponses(): List<Message> {
    val result = mutableListOf<Message>()
    for (message in this) {
        val target = result.lastOrNull()
        if (message.isFunctionResponseOnly() && target != null && target.role == MessageRole.Assistant) {
            var merged: Message = target
            val seen = merged.functionResponses.mapTo(HashSet()) { it.id to it.name }
            val fresh = mutableListOf<FunctionResponseView>()
            message.functionResponses.forEach { response ->
                val key = response.id to response.name
                if (seen.add(key)) {
                    fresh += response
                } else if (hasStructuredResult(response)) {
                    // 确认流程下占位响应（confirmation-required 的 error 占位）与真实结果
                    // 共用同一 call id；占位先到会被上面的去重保留。真实结构化结果
                    // （本地文件轮播/远程图片轮播的数据源）到达时必须反向替换占位，
                    // 否则用户只看得到 ✓ chip，永远等不到图片。
                    val responses = merged.functionResponses.toMutableList()
                    val index = responses.indexOfFirst {
                        (it.id to it.name) == key && !hasStructuredResult(it)
                    }
                    if (index >= 0) {
                        responses[index] = response
                        merged = merged.copy(functionResponses = responses)
                    } else {
                        // 占位与真实结果落在同一条消息里时，占位还停在 fresh 中未并入。
                        val freshIndex = fresh.indexOfFirst {
                            (it.id to it.name) == key && !hasStructuredResult(it)
                        }
                        if (freshIndex >= 0) fresh[freshIndex] = response else fresh += response
                    }
                }
            }
            if (fresh.isNotEmpty()) {
                merged = merged.copy(functionResponses = merged.functionResponses + fresh)
            }
            result[result.lastIndex] = merged
        } else {
            result += message
        }
    }
    return result
}

/** 该响应是否携带可渲染的结构化内容（本地文件列表或远程图片列表）。 */
private fun hasStructuredResult(response: FunctionResponseView): Boolean =
    response.localFileSearchResult?.files?.isNotEmpty() == true ||
        response.remoteImageResult?.images?.isNotEmpty() == true

/** 除工具响应外没有任何可渲染内容（折叠候选）。 */
private fun Message.isFunctionResponseOnly(): Boolean =
    error == null &&
        textParts.isEmpty() &&
        fileAttachments.isEmpty() &&
        functionCalls.isEmpty() &&
        functionResponses.isNotEmpty()
