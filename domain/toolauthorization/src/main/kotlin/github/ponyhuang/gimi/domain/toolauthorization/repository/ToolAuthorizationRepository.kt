package github.ponyhuang.gimi.domain.toolauthorization.repository

import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import kotlinx.coroutines.flow.StateFlow

interface LocalToolDefinitionSource {
    fun definitions(): List<ToolDefinition>
}

interface ToolAuthorizationRepository {
    val tools: StateFlow<List<ToolDescriptor>>
    val revision: StateFlow<Long>

    fun enabledToolIds(): Set<String>
}
