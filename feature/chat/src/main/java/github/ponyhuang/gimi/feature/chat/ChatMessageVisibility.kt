package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.FunctionCallView
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.AdkRequestConfirmationToolName

/**
 * ADK 确认协议的内部工具名（等价于 ADK `FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME`；
 * feature 层统一引用领域常量）。它只是"请求用户确认"的信令，确认结果已由
 * ToolConfirmationCard 单独呈现，chip 渲染与可见性判断都应把它过滤掉。
 */
internal const val ConfirmationToolName = AdkRequestConfirmationToolName

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
