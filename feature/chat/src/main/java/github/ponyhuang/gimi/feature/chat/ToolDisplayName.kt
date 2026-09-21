package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import github.ponyhuang.gimi.domain.conversation.model.GetUserChoiceToolName

/** MCP 工具历史前缀；展示时剥离，运行协议仍保留原始名称。 */
private val McpNamePrefix = Regex("^mcp_[A-Za-z0-9_-]{8}_")

/** 将协议工具名转换成面向用户的简短显示名。 */
@Composable
internal fun toolDisplayName(rawName: String): String = when (rawName) {
    GetUserChoiceToolName -> stringResource(R.string.chat_tool_get_user_choice)
    ToolSearchProtocolName -> stringResource(R.string.chat_tool_search_on_demand)
    else -> rawName.replace(McpNamePrefix, "")
}
