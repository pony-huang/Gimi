package github.ponyhuang.asssistantai.feature.toolauthorization

import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor

data class ToolAuthorizationUiState(
    val isCustomizationEnabled: Boolean = false,
    val tools: List<ToolDescriptor> = emptyList(),
    val isMutationBlocked: Boolean = false,
    val notice: String? = null,
) {
    val enabledCount: Int get() = tools.count(ToolDescriptor::isEnabled)
    val totalCount: Int get() = tools.size
}

sealed interface ToolAuthorizationAction {
    data class SetCustomizationEnabled(val enabled: Boolean) : ToolAuthorizationAction
}

data class ToolAuthorizationConfigurationUiState(
    val query: String = "",
    val filter: ToolAuthorizationFilter = ToolAuthorizationFilter.ALL,
    val tools: List<ToolDescriptor> = emptyList(),
    val isMutationBlocked: Boolean = false,
    val notice: String? = null,
) {
    val visibleTools: List<ToolDescriptor>
        get() {
            val normalized = query.trim()
            return tools.filter { tool ->
                val matchesQuery = normalized.isEmpty() ||
                    tool.name.contains(normalized, ignoreCase = true) ||
                    tool.description.contains(normalized, ignoreCase = true)
                val matchesFilter = when (filter) {
                    ToolAuthorizationFilter.ALL -> true
                    ToolAuthorizationFilter.ENABLED -> tool.isEnabled
                    ToolAuthorizationFilter.DISABLED -> !tool.isEnabled
                }
                matchesQuery && matchesFilter
            }
        }

    val enabledCount: Int get() = tools.count(ToolDescriptor::isEnabled)
}

enum class ToolAuthorizationFilter {
    ALL,
    ENABLED,
    DISABLED,
}

sealed interface ToolAuthorizationConfigurationAction {
    data class Search(val query: String) : ToolAuthorizationConfigurationAction
    data class SetEnabled(val toolId: String, val enabled: Boolean) : ToolAuthorizationConfigurationAction
    data class SetFilter(val filter: ToolAuthorizationFilter) : ToolAuthorizationConfigurationAction
}
