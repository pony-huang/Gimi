package github.ponyhuang.asssistantai.agent.tools.official.anthropic

import com.anthropic.models.messages.ToolUnion
import com.anthropic.models.messages.WebSearchTool20250305
import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.NativeToolSpec
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import javax.inject.Inject

/**
 * Official toolset for the Anthropic protocol (Anthropic, MiniMax).
 *
 * Owns no local [BaseTool]s; contributes vendor-native tool specs (e.g.
 * server-side `web_search`) which the Anthropic protocol adapter merges
 * into the request. New native tools extend [SUPPORTED_NATIVE_IDS] and
 * [nativeSpecFor].
 */
class AnthropicOfficialToolset @Inject constructor() : OfficialToolset {
    override val protocolId: String = "anthropic"

    override fun isApplicable(config: ModelConfig): Boolean =
        config.baseType == ApiProtocol.Anthropic &&
                config.serviceId in SUPPORTED_SERVICE_IDS

    override suspend fun getTools(config: ModelConfig): List<BaseTool> = emptyList()

    override fun openAiNativeSpecs(config: ModelConfig): List<NativeToolSpec> = emptyList()

    override fun anthropicNativeSpecs(config: ModelConfig): List<NativeToolSpec> {
        if (!isApplicable(config)) return emptyList()
        return SUPPORTED_NATIVE_IDS
            .filter { it in config.officialTools }
            .mapNotNull { id -> nativeSpecFor(id) }
    }

    private fun nativeSpecFor(id: String): NativeToolSpec? = when (id) {
        OfficialToolIds.WEB_SEARCH -> NativeToolSpec.Anthropic(
            toolId = id,
            tool = ToolUnion.ofWebSearchTool20250305(
                WebSearchTool20250305.builder().build(),
            ),
        )
        // Extension point: file_search, computer_use, ...
        else -> null
    }

    companion object {
        // 与 :data:modelcatalog 的 LLMModelType.serviceId 保持一致；data 层模块
        // 不允许互相依赖，此处复制字面量。
        val SUPPORTED_SERVICE_IDS: Set<String> = setOf("anthropic", "minimax")
        val SUPPORTED_NATIVE_IDS: List<String> = listOf(OfficialToolIds.WEB_SEARCH)
    }
}
