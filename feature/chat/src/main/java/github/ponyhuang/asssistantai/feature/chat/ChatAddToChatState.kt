package github.ponyhuang.asssistantai.feature.chat

import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.mcp.model.McpServer
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor

enum class SessionToolFilter {
    ALL,
    ENABLED,
    DISABLED,
}

internal fun consumeAtLowerScrollBoundary(availableY: Float): Float =
    availableY.coerceAtMost(0f)

data class ChatAddToChatState(
    val serviceId: String? = null,
    val configuration: ConversationToolConfiguration? = null,
    val localTools: List<ToolDescriptor> = emptyList(),
    val mcpServers: List<McpServer> = emptyList(),
    val supportedOfficialToolIds: Set<String> = emptySet(),
    val isMutationBlocked: Boolean = false,
    val errorMessage: String? = null,
) {
    val enabledLocalToolCount: Int
        get() = configuration?.enabledLocalToolIds.orEmpty().count { enabledId ->
            localTools.any { it.id == enabledId }
        }

    val enabledMcpServerCount: Int
        get() = configuration?.enabledMcpServerIds.orEmpty().count { enabledId ->
            mcpServers.any { it.id == enabledId }
        }

    fun visibleLocalTools(
        query: String,
        filter: SessionToolFilter,
    ): List<ToolDescriptor> {
        val normalizedQuery = query.trim()
        val enabledIds = configuration?.enabledLocalToolIds.orEmpty()
        return localTools.filter { tool ->
            val matchesQuery = normalizedQuery.isEmpty() ||
                tool.name.contains(normalizedQuery, ignoreCase = true) ||
                tool.description.contains(normalizedQuery, ignoreCase = true) ||
                tool.id.contains(normalizedQuery, ignoreCase = true)
            val matchesFilter = when (filter) {
                SessionToolFilter.ALL -> true
                SessionToolFilter.ENABLED -> tool.id in enabledIds
                SessionToolFilter.DISABLED -> tool.id !in enabledIds
            }
            matchesQuery && matchesFilter
        }
    }
}
