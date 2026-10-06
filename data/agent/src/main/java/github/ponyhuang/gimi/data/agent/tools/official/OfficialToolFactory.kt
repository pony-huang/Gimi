package github.ponyhuang.gimi.data.agent.tools.official

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.GoogleMapsTool
import com.google.adk.kt.tools.GoogleSearchTool
import com.google.adk.kt.tools.UrlContextTool
import github.ponyhuang.gimi.data.agent.tools.official.glm.GlmReaderTool
import github.ponyhuang.gimi.data.agent.tools.official.glm.GlmWebSearchTool
import github.ponyhuang.gimi.data.agent.tools.official.glm.GlmWebToolApi
import github.ponyhuang.gimi.data.agent.tools.official.kimi.KimiFormulaTool
import github.ponyhuang.gimi.data.agent.tools.official.minimax.MinimaxImageGenerationApi
import github.ponyhuang.gimi.data.agent.tools.official.minimax.MinimaxImageGenerationTool
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolBinding
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.model.canProvideOfficialTools
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.modelcatalog.repository.KimiFormulaSource
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** 将纯目录声明适配为 ADK 工具；不再维护厂商支持矩阵和目录查询。 */
@Singleton
class OfficialToolFactory @Inject constructor(
    private val kimiFormulas: KimiFormulaSource,
    private val httpClient: OkHttpClient,
    private val modelServices: AgentModelConfigurationSource,
) {
    /** 凭据仅在构造本地执行实例时读取，不进入 invocation metadata。 */
    suspend fun createTools(spec: OfficialToolSpec): List<BaseTool> = when (val binding = spec.binding) {
        is OfficialToolBinding.ProviderDeclaration -> listOf(OfficialBuiltInTool(binding.wireName))
        OfficialToolBinding.ProviderNative -> listOf(
            when (spec.toolId) {
                OfficialToolIds.GEMINI_WEB_SEARCH -> GoogleSearchTool()
                OfficialToolIds.GEMINI_URL_CONTEXT -> UrlContextTool()
                OfficialToolIds.GEMINI_GOOGLE_MAPS -> GoogleMapsTool()
                else -> error("No native tool factory for ${spec.toolId}")
            },
        )
        OfficialToolBinding.LocalFunctions -> {
            val service = modelServices.currentServices().firstOrNull {
                it.id == spec.serviceId && it.canProvideOfficialTools()
            }
            if (service == null) emptyList() else when (spec.toolId) {
                OfficialToolIds.MINIMAX_IMAGE_GENERATION -> {
                    val api = MinimaxImageGenerationApi(service.apiKey, httpClient)
                    listOf(
                        MinimaxImageGenerationTool.textToImage(api),
                        MinimaxImageGenerationTool.imageToImage(api),
                    )
                }
                OfficialToolIds.GLM_WEB_SEARCH -> {
                    val api = GlmWebToolApi(service.apiKey, service.activeApiBaseUrl, httpClient)
                    listOf(GlmWebSearchTool(api), GlmReaderTool(api))
                }
                OfficialToolIds.KIMI_FORMULAS -> kimiFormulas.fetch(service.id, service.apiKey).map {
                    KimiFormulaTool(service.apiKey, it, httpClient)
                }
                else -> error("No local tool factory for ${spec.toolId}")
            }
        }
    }
}
