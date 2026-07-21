package github.ponyhuang.asssistantai.domain.toolauthorization.repository

import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import kotlinx.coroutines.flow.StateFlow

interface LocalToolDefinitionSource {
    fun definitions(): List<ToolDefinition>
}

interface ToolAuthorizationRepository {
    val tools: StateFlow<List<ToolDescriptor>>
    val revision: StateFlow<Long>
    val isCustomizationEnabled: StateFlow<Boolean>

    fun enabledToolIds(): Set<String>
    fun setEnabled(toolId: String, enabled: Boolean)
    fun setAllEnabled(enabled: Boolean)
    fun setCustomizationEnabled(enabled: Boolean)
}
