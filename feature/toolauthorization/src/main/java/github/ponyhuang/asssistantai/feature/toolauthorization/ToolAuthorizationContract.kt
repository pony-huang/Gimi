package github.ponyhuang.asssistantai.feature.toolauthorization

import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor

data class ToolAuthorizationUiState(
    val query: String = "",
    val tools: List<ToolDescriptor> = emptyList(),
    val isMutationBlocked: Boolean = false,
    val notice: String? = null,
) {
    val visibleTools: List<ToolDescriptor>
        get() {
            val normalized = query.trim()
            if (normalized.isEmpty()) return tools
            return tools.filter { tool ->
                tool.name.contains(normalized, ignoreCase = true) ||
                    tool.description.contains(normalized, ignoreCase = true)
            }
        }

    val enabledCount: Int get() = tools.count(ToolDescriptor::isEnabled)
}

sealed interface ToolAuthorizationAction {
    data class Search(val query: String) : ToolAuthorizationAction
    data class SetEnabled(val toolId: String, val enabled: Boolean) : ToolAuthorizationAction
    data class SetAllEnabled(val enabled: Boolean) : ToolAuthorizationAction
}
