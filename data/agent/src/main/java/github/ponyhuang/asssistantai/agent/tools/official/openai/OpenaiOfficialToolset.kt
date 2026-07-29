package github.ponyhuang.asssistantai.agent.tools.official.openai

import com.google.adk.kt.tools.BaseTool
import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.chat.completions.ChatCompletionFunctionTool
import com.openai.models.chat.completions.ChatCompletionTool
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.official.NativeToolSpec
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import javax.inject.Inject

/**
 * Official toolset for the OpenAI-Compatible protocol (OpenAI, Mimo).
 *
 * Owns no local [BaseTool]s; contributes vendor-native tool specs (e.g.
 * hosted `web_search`) which the OpenAI protocol adapter merges into the
 * request. New native tools extend [SUPPORTED_NATIVE_IDS] and [nativeSpecFor].
 */
class OpenaiOfficialToolset @Inject constructor() : OfficialToolset {
    override val protocolId: String = "openai"

    override fun isApplicable(config: ModelConfig): Boolean =
        config.baseType == ApiProtocol.Standard &&
                config.serviceId in SUPPORTED_SERVICE_IDS

    override suspend fun getTools(config: ModelConfig): List<BaseTool> = emptyList()

    override fun openAiNativeSpecs(config: ModelConfig): List<NativeToolSpec> {
        if (!isApplicable(config)) return emptyList()
        return SUPPORTED_NATIVE_IDS
            .filter { it in config.officialTools }
            .mapNotNull { id -> nativeSpecFor(id) }
    }

    override fun anthropicNativeSpecs(config: ModelConfig): List<NativeToolSpec> = emptyList()

    private fun nativeSpecFor(id: String): NativeToolSpec? = when (id) {
        OfficialToolIds.WEB_SEARCH -> NativeToolSpec.OpenAi(
            toolId = id,
            tool = ChatCompletionTool.ofFunction(
                ChatCompletionFunctionTool.builder()
                    .type(JsonValue.from(id))
                    .function(
                        FunctionDefinition.builder()
                            .name(id)
                            .putAdditionalProperty("type", JsonValue.from(id))
                            .build(),
                    )
                    .build(),
            ),
        )
        // Extension point: OfficialToolIds.FILE_SEARCH -> NativeToolSpec.OpenAi(...),
        else -> null
    }

    companion object {
        // 与 :data:modelcatalog 的 LLMModelType.serviceId 保持一致；data 层模块
        // 不允许互相依赖，此处复制字面量。
        val SUPPORTED_SERVICE_IDS: Set<String> = setOf("openai", "mimo")
        val SUPPORTED_NATIVE_IDS: List<String> = listOf(OfficialToolIds.WEB_SEARCH)
    }
}
