package github.ponyhuang.asssistantai.agent.tools.official

import github.ponyhuang.asssistantai.agent.tools.official.kimi.KimiFormulaManifest
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.AgentModelConfigurationSource
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the available functions for each [OfficialToolIds] category using
 * the currently selected model service. Web Search is a single static
 * placeholder; Kimi Formulas are pulled from the Moonshot formula manifest.
 *
 * Implementations return an empty list when the tool is not applicable to the
 * active service so callers can treat unsupported categories uniformly.
 */
@Singleton
class DefaultOfficialToolFunctionCatalog @Inject constructor(
    private val kimiFormulaCatalog: KimiFormulaCatalog,
) : OfficialToolFunctionCatalog {

    override suspend fun listFunctions(toolId: String): List<OfficialToolFunction> = when (toolId) {
        OfficialToolIds.WEB_SEARCH -> listOf(
            OfficialToolFunction(
                id = OfficialToolIds.WEB_SEARCH,
                name = "网页搜索",
                description = "搜索最新的互联网信息",
            ),
        )
        OfficialToolIds.KIMI_FORMULAS -> kimiFormulaCatalog.fetch()
        else -> emptyList()
    }
}

@Singleton
class KimiFormulaCatalog @Inject constructor(
    private val modelServices: AgentModelConfigurationSource,
    private val httpClient: OkHttpClient,
) {
    suspend fun fetch(): List<OfficialToolFunction> {
        val service = currentMoonshotService() ?: return emptyList()
        val manifest = KimiFormulaManifest(
            apiKey = service.apiKey,
            httpClient = httpClient,
        )
        return runCatching { manifest.fetch() }
            .getOrDefault(emptyList())
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