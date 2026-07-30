package github.ponyhuang.gimi.agent.tools.official

import github.ponyhuang.gimi.agent.tools.official.glm.GlmReaderTool
import github.ponyhuang.gimi.agent.tools.official.glm.GlmWebSearchTool
import github.ponyhuang.gimi.agent.tools.official.glm.GlmWebSearchToolset
import github.ponyhuang.gimi.agent.tools.official.kimi.KimiFormulaCache
import github.ponyhuang.gimi.agent.tools.official.kimi.KimiFormulaToolset
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the available functions for each official-tool category using
 * the currently selected model service. Protocol-native Web Search and GLM web
 * functions are static; Kimi Formulas are pulled from the Moonshot manifest.
 *
 * Implementations return an empty list when the tool is not applicable to the
 * active service so callers can treat unsupported categories uniformly.
 */
@Singleton
class DefaultOfficialToolFunctionCatalog @Inject constructor(
    private val kimiFormulaCatalog: KimiFormulaCatalog,
) : OfficialToolFunctionCatalog {

    override suspend fun listFunctions(toolId: String): List<OfficialToolFunction> = when (toolId) {
        WEB_SEARCH_TOOL_ID -> listOf(
            OfficialToolFunction(
                id = WEB_SEARCH_TOOL_ID,
                name = "网页搜索",
                description = "搜索最新的互联网信息",
            ),
        )
        KimiFormulaToolset.TOOL_ID -> kimiFormulaCatalog.fetch()
        GlmWebSearchToolset.TOOL_ID -> listOf(
            OfficialToolFunction(
                id = GlmWebSearchTool.NAME,
                name = "网页搜索",
                description = "搜索最新的互联网信息",
            ),
            OfficialToolFunction(
                id = GlmReaderTool.NAME,
                name = "网页阅读",
                description = "读取并解析指定网页内容",
            ),
        )
        else -> emptyList()
    }
}

private const val WEB_SEARCH_TOOL_ID: String = "web_search"

@Singleton
class KimiFormulaCatalog @Inject constructor(
    private val modelServices: AgentModelConfigurationSource,
    private val cache: KimiFormulaCache,
) {
    suspend fun fetch(): List<OfficialToolFunction> {
        val service = currentMoonshotService() ?: return emptyList()
        return cache.fetch(
            serviceId = service.id,
            apiKey = service.apiKey,
        )
            .map { declaration ->
                OfficialToolFunction(
                    id = declaration.name,
                    name = declaration.name,
                    description = declaration.description,
                )
            }
    }

    private fun currentMoonshotService(): LLMModelSetting? =
        modelServices.currentServices()
            .firstOrNull { it.id == MOONSHOT_SERVICE_ID }

    private companion object {
        // 与 :data:modelcatalog 的 LLMModelType.Moonshot.serviceId 保持一致；
        // data 层模块不允许互相依赖，此处复制字面量。
        const val MOONSHOT_SERVICE_ID = "kimi"
    }
}
