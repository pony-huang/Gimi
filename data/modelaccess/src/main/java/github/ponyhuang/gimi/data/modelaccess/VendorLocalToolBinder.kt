package github.ponyhuang.gimi.data.modelaccess

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.data.modelaccess.deepseek.tool.DeepSeekWebTool
import github.ponyhuang.gimi.data.modelaccess.deepseek.tool.DeepSeekWebToolApi
import github.ponyhuang.gimi.data.modelaccess.glm.tool.GlmReaderTool
import github.ponyhuang.gimi.data.modelaccess.glm.tool.GlmWebSearchTool
import github.ponyhuang.gimi.data.modelaccess.glm.tool.GlmWebToolApi
import github.ponyhuang.gimi.data.modelaccess.kimi.tool.KimiWebTool
import github.ponyhuang.gimi.data.modelaccess.kimi.tool.KimiWebToolApi
import github.ponyhuang.gimi.data.modelaccess.minimax.tool.MinimaxImageGenerationApi
import github.ponyhuang.gimi.data.modelaccess.minimax.tool.MinimaxImageGenerationTool
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 厂商本地函数工具的装配点：把厂商 SDK 工具类（DeepSeek/GLM/Kimi/Minimax）的
 * 构造收敛在适配层。编排层只凭 [OfficialToolSpec] 与已解析的凭据索取工具实例，
 * 不再 import 任何具体厂商工具类。
 */
@Singleton
class VendorLocalToolBinder @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    /**
     * 装配 [spec.toolId] 的厂商工具集。
     *
     * @return 该 toolId 的工具列表；toolId 不属于任何厂商适配器时为 null，
     *   由调用方决定如何报错（ADK 原生工具不经过本装配点）。
     */
    fun createTools(spec: OfficialToolSpec, apiKey: String, conversationEndpoint: String): List<BaseTool>? =
        when (spec.toolId) {
            OfficialToolIds.DEEPSEEK_WEB_SEARCH,
            OfficialToolIds.DEEPSEEK_WEB_FETCH,
            -> listOf(DeepSeekWebTool(spec.toolId, DeepSeekWebToolApi(apiKey, httpClient)))
            OfficialToolIds.MINIMAX_IMAGE_GENERATION -> {
                val api = MinimaxImageGenerationApi(apiKey, httpClient)
                listOf(
                    MinimaxImageGenerationTool.textToImage(api),
                    MinimaxImageGenerationTool.imageToImage(api),
                )
            }
            OfficialToolIds.GLM_WEB_SEARCH -> {
                val api = GlmWebToolApi(apiKey, conversationEndpoint, httpClient)
                listOf(GlmWebSearchTool(api), GlmReaderTool(api))
            }
            OfficialToolIds.KIMI_WEB_TOOLS -> {
                val api = KimiWebToolApi(apiKey, httpClient)
                spec.staticFunctionIds.map { KimiWebTool(it, api) }
            }
            else -> null
        }
}
