package github.ponyhuang.gimi.data.modelcatalog.official

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolAvailability
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolBinding
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import javax.inject.Singleton

/** 官方工具目录实现：组合纯支持规则、来源实例配置和固定函数目录。 */
@Singleton
class DefaultOfficialToolFunctionCatalog @Inject constructor(
    private val support: OfficialToolSupport,
    private val modelServices: AgentModelConfigurationSource,
) : OfficialToolFunctionCatalog {
    override val all: List<OfficialToolSpec> = support.all
    private val specsById = all.associateBy { it.toolId }

    override fun supportedToolIds(templateId: String, protocol: ApiProtocol): Set<String> =
        support.supportedToolIds(templateId, protocol)

    override fun availableSpecsFor(
        templateId: String,
        protocol: ApiProtocol,
        modelId: String,
    ): List<OfficialToolSpec> = support.availableSpecsFor(
        templateId, protocol, modelId, modelServices.currentInstances(),
    )

    override fun availableTools(
        activeInstance: ModelServiceInstance,
        activeModelId: String,
    ): List<OfficialToolAvailability> {
        // 无模板的自定义服务不在厂商工具矩阵内；自定义 LLM 仍可使用 MCP 与插件等通用工具。
        val templateId = activeInstance.templateId ?: return emptyList()
        val protocol = activeInstance.conversationConnection?.protocol ?: return emptyList()
        val instances = modelServices.currentInstances()
        return support.availableSpecsFor(templateId, protocol, activeModelId, instances).mapNotNull { spec ->
            // 凭据归属解析到实例：当前模板用当前实例，跨模型工具取同模板的已启用实例。
            val source = if (spec.templateId == templateId) {
                activeInstance
            } else {
                instances.firstOrNull { it.templateId == spec.templateId && it.canProvideOfficialTools() }
            } ?: return@mapNotNull null
            if (!source.canProvideOfficialTools()) return@mapNotNull null
            OfficialToolAvailability(toolId = spec.toolId, serviceId = source.id)
        }
    }

    override fun enabledSearchCandidateSpecs(): List<OfficialToolSpec> = all.filter { spec ->
        spec.searchCandidate &&
            spec.binding is OfficialToolBinding.LocalFunctions &&
            sourceInstance(spec.templateId) != null
    }

    override suspend fun listFunctions(toolId: String): List<OfficialToolFunction> {
        val spec = specsById[toolId] ?: return emptyList()
        if (spec.staticFunctionIds.isNotEmpty()) {
            return spec.staticFunctionIds.map { OfficialToolFunction(id = it, name = it, description = it) }
        }
        return emptyList()
    }

    private fun sourceInstance(templateId: String): ModelServiceInstance? = modelServices.currentInstances()
        .firstOrNull { it.templateId == templateId && it.canProvideOfficialTools() }
}
