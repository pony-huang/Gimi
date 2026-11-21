package github.ponyhuang.gimi.data.agent.tools.official

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.GoogleMapsTool
import com.google.adk.kt.tools.GoogleSearchTool
import com.google.adk.kt.tools.UrlContextTool
import github.ponyhuang.gimi.data.modelaccess.VendorLocalToolBinder
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolBinding
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSpec
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import javax.inject.Singleton

/** 将纯目录声明适配为 ADK 工具；不再维护厂商支持矩阵和目录查询。 */
@Singleton
class OfficialToolFactory @Inject constructor(
    private val modelServices: AgentModelConfigurationSource,
    private val vendorTools: VendorLocalToolBinder,
) {
    /** 凭据仅在构造本地执行实例时读取，不进入 invocation metadata。 */
    suspend fun createTools(spec: OfficialToolSpec): List<BaseTool> = when (val binding = spec.binding) {
        is OfficialToolBinding.ProviderDeclaration ->
            listOf(OfficialBuiltInTool(binding.declarationName))
        OfficialToolBinding.ProviderNative -> listOf(
            when (spec.toolId) {
                OfficialToolIds.GEMINI_WEB_SEARCH -> GoogleSearchTool()
                OfficialToolIds.GEMINI_URL_CONTEXT -> UrlContextTool()
                OfficialToolIds.GEMINI_GOOGLE_MAPS -> GoogleMapsTool()
                else -> error("No native tool factory for ${spec.toolId}")
            },
        )
        OfficialToolBinding.LocalFunctions -> {
            // 独立 API 工具的凭据归属实例的对话连接；同模板多实例时任取一个已启用实例。
            val instance = modelServices.currentInstances().firstOrNull {
                it.templateId == spec.templateId && it.canProvideOfficialTools()
            }
            if (instance == null) {
                emptyList()
            } else {
                val conversation = checkNotNull(instance.conversationConnection)
                val apiKey = instance.effectiveApiKey(conversation)
                // 厂商 SDK 工具的构造收敛在 :data:modelaccess；这里只提供目录与凭据。
                vendorTools.createTools(spec, apiKey, conversation.endpoint)
                    ?: error("No local tool factory for ${spec.toolId}")
            }
        }
    }
}
