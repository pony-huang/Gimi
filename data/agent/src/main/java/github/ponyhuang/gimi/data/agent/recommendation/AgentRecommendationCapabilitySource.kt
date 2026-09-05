package github.ponyhuang.gimi.data.agent.recommendation

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.data.agent.AgentContributionRegistry
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.data.agent.AgentToolCatalogContext
import github.ponyhuang.gimi.data.agent.AgentToolCatalogEntry
import github.ponyhuang.gimi.data.agent.toRuntimeMetadata
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCapability
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationCapabilitySource
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 汇总当前可用工具的声明元数据；该过程不会执行工具。 */
@Singleton
class AgentRecommendationCapabilitySource @Inject constructor(
    private val contributionRegistry: AgentContributionRegistry,
    private val toolAuthorization: ToolAuthorizationRepository,
    private val modelFactory: AgentLLMModelFactory,
) : RecommendationCapabilitySource {
    override suspend fun capabilities(): List<RecommendationCapability> {
        // 官方函数按当前模型服务门控；没有可用模型时跳过官方来源，不影响其它目录。
        val modelRuntime = runCatching {
            (modelFactory.selectFastModelConfig() ?: modelFactory.selectModelConfig(null))
                .toRuntimeMetadata()
        }.getOrNull()
        val enabledLocalIds = toolAuthorization.enabledToolIds()
        return contributionRegistry.toolCatalog(AgentToolCatalogContext(modelRuntime))
            .flatMap { entry ->
                when (entry.source) {
                    AgentToolCatalogEntry.SOURCE_LOCAL ->
                        entry.tools.filter { tool -> tool.name in enabledLocalIds }
                    else -> entry.tools
                }.map { tool -> tool.toCapability(entry.source) }
            }
            .distinctBy { capability -> "${capability.source}:${capability.id}" }
    }

    private fun BaseTool.toCapability(source: String) = RecommendationCapability(
        id = name,
        source = source,
        description = description.take(MAX_DESCRIPTION_LENGTH),
    )

    private companion object {
        const val MAX_DESCRIPTION_LENGTH: Int = 240
    }
}
