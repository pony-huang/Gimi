package github.ponyhuang.gimi.data.modelcatalog.official

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolAvailability
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolBinding
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.domain.modelcatalog.model.canProvideOfficialTools
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.modelcatalog.repository.KimiFormulaSource
import javax.inject.Inject
import javax.inject.Singleton

/** 官方工具目录实现：组合纯支持规则、来源服务配置和动态函数目录。 */
@Singleton
class DefaultOfficialToolFunctionCatalog @Inject constructor(
    private val support: OfficialToolSupport,
    private val modelServices: AgentModelConfigurationSource,
    private val kimiFormulas: KimiFormulaSource,
) : OfficialToolFunctionCatalog {
    override val all: List<OfficialToolSpec> = support.all
    private val specsById = all.associateBy { it.toolId }

    override fun supportedToolIds(serviceId: String, protocol: ApiProtocol): Set<String> =
        support.supportedToolIds(serviceId, protocol)

    override fun availableSpecsFor(
        serviceId: String,
        protocol: ApiProtocol,
        modelId: String,
    ): List<OfficialToolSpec> = support.availableSpecsFor(
        serviceId, protocol, modelId, modelServices.currentServices(),
    )

    override fun availableTools(
        activeService: LLMModelSetting,
        activeModelId: String,
    ): List<OfficialToolAvailability> = availableSpecsFor(
        activeService.id, activeService.apiProtocol, activeModelId,
    ).map { OfficialToolAvailability(toolId = it.toolId, serviceId = it.serviceId) }

    override fun enabledSearchCandidateSpecs(): List<OfficialToolSpec> = all.filter { spec ->
        spec.searchCandidate &&
            spec.binding is OfficialToolBinding.LocalFunctions &&
            sourceService(spec.serviceId) != null
    }

    override suspend fun listFunctions(toolId: String): List<OfficialToolFunction> {
        val spec = specsById[toolId] ?: return emptyList()
        if (spec.staticFunctionIds.isNotEmpty()) {
            return spec.staticFunctionIds.map { OfficialToolFunction(id = it, name = it, description = it) }
        }
        if (toolId != OfficialToolIds.KIMI_FORMULAS) return emptyList()
        val service = sourceService(spec.serviceId) ?: return emptyList()
        return kimiFormulas.fetch(service.id, service.apiKey).map { declaration ->
            OfficialToolFunction(
                id = declaration.name,
                name = declaration.name,
                description = declaration.description,
            )
        }
    }

    private fun sourceService(serviceId: String): LLMModelSetting? = modelServices.currentServices()
        .firstOrNull { it.id == serviceId && it.canProvideOfficialTools() }
}
