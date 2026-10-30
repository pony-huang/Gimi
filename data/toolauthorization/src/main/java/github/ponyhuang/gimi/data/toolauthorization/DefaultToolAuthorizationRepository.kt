package github.ponyhuang.gimi.data.toolauthorization

import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.gimi.domain.toolauthorization.repository.LocalToolDefinitionSource
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 提供完整本地工具目录，所有注册工具统一可用，不读取历史勾选配置。 */
@Singleton
class DefaultToolAuthorizationRepository @Inject constructor(
    source: LocalToolDefinitionSource,
) : ToolAuthorizationRepository {
    private val definitions = source.definitions().also { items ->
        val duplicates = items.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate local tool ids: ${duplicates.joinToString()}" }
    }
    private val ids = definitions.mapTo(linkedSetOf()) { it.id }

    override val tools: StateFlow<List<ToolDescriptor>> = MutableStateFlow(
        definitions.map { ToolDescriptor(it.id, it.name, it.description, isEnabled = true) },
    ).asStateFlow()
    override val revision: StateFlow<Long> = MutableStateFlow(0L).asStateFlow()

    override fun enabledToolIds(): Set<String> = ids.toSet()
}
