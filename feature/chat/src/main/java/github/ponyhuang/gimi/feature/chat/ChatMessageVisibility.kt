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
 * 动态工具检索的内部工具名。它既是 ADK 协议信令（检索结果决定后续声明注入），
 * 也是用户看得见的 Agent 活动，因此时间线照常渲染，仅由 [toolDisplayName]
 * 换成本地化展示名。
 */
internal const val ToolSearchProtocolName = "tool_search"

/**
 * 只用于协议握手、不该作为工具活动呈现的内部名。
 *
 * `tool_search` 曾在此列，导致按需加载模式下用户看不到"检索工具"这一步；
 * 它现在与业务工具同样呈现，只剩确认信令继续隐藏。
 */
private val HiddenProtocolToolNames = setOf(ConfirmationToolName)

/** 过滤掉内部协议信令后的工具调用列表。 */
internal fun Message.visibleFunctionCalls(): List<FunctionCallView> =
    functionCalls.filterNot { it.name in HiddenProtocolToolNames }

/** 过滤掉内部协议信令后的工具响应列表。 */
internal fun Message.visibleFunctionResponses(): List<FunctionResponseView> =
    functionResponses.filterNot { it.name in HiddenProtocolToolNames }
