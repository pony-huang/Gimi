package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor

enum class SessionToolFilter {
    ALL,
    ENABLED,
    DISABLED,
}

/**
 * Prevents a list that has reached its lower boundary from forwarding upward motion to the
 * surrounding [ModalBottomSheet][androidx.compose.material3.ModalBottomSheet].
 *
 * Nested-scroll deltas use pointer direction: negative Y means the finger is moving upward and
 * positive Y means it is moving downward. We consume only negative motion at the lower boundary:
 *
 * - Before the boundary, the list must receive the motion so normal scrolling and flinging work.
 * - At the boundary, forwarding upward motion makes the already-expanded sheet repeatedly settle
 *   against the same anchor, which appears as bottom-edge jitter.
 * - Positive motion must remain available so the list can scroll back and the sheet can still be
 *   dismissed with a downward swipe.
 *
 * Keep this predicate boundary-aware. Replacing it with an unconditional `coerceAtMost(0f)` hides
 * the distinction between list scrolling and sheet dragging and can reintroduce the regression.
 */
internal fun consumeAtLowerScrollBoundary(
    availableY: Float,
    canScrollForward: Boolean,
): Float = if (canScrollForward) 0f else availableY.coerceAtMost(0f)

data class ChatAddToChatState(
    val serviceId: String? = null,
    val configuration: ConversationToolConfiguration? = null,
    val localTools: List<ToolDescriptor> = emptyList(),
    val mcpServers: List<McpServer> = emptyList(),
    val officialTools: List<OfficialToolDescriptor> = emptyList(),
    val isMutationBlocked: Boolean = false,
    /** Full access 全局开关：开启后所有需要确认的工具调用自动放行。 */
    val fullAccess: Boolean = false,
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
