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
    val officialTools: List<OfficialToolDescriptor> = emptyList(),
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

    /**
     * Number of functions the user has selected for [toolId] within the current
     * service. Empty configuration / unknown tool returns 0. When the
     * [ConversationToolConfiguration.ALL_FUNCTIONS_MARKER] sentinel is present
     * the count is read from the matching [OfficialToolDescriptor] (which may
     * not yet have its [OfficialToolDescriptor.functions] loaded — in that
     * case the count is reported as 1 because the marker itself is one entry).
     */
    fun enabledOfficialFunctionCount(toolId: String): Int {
        val serviceId = serviceId ?: return 0
        val config = configuration ?: return 0
        val raw = config.enabledOfficialFunctionIds(serviceId, toolId)
        if (ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in raw) {
            val descriptor = officialTools.firstOrNull { it.id == toolId }
            return descriptor?.functions?.size ?: 1
        }
        return raw.size
    }

    /**
     * Total number of functions the user has selected across every official
     * tool in the active service. Used for the single "官方内置工具" row on
     * the home sheet so it can advertise an aggregate summary without
     * exposing each provider separately at the top level.
     */
    fun enabledOfficialFunctionTotal(): Int =
        officialTools.sumOf { enabledOfficialFunctionCount(it.id) }

    /**
     * Whether a specific function is currently enabled, taking the
     * [ConversationToolConfiguration.ALL_FUNCTIONS_MARKER] sentinel into
     * account. Returns false when no configuration is loaded or the tool /
     * function is unknown.
     */
    fun isOfficialFunctionEnabled(toolId: String, functionId: String): Boolean {
        val serviceId = serviceId ?: return false
        val config = configuration ?: return false
        val raw = config.enabledOfficialFunctionIds(serviceId, toolId)
        return ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in raw ||
            functionId in raw
    }
}