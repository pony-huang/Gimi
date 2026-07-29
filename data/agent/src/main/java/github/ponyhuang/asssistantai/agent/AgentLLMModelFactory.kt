package github.ponyhuang.asssistantai.agent

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.Model
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import github.ponyhuang.asssistantai.agent.model.Claude
import github.ponyhuang.asssistantai.agent.model.Openai
import github.ponyhuang.asssistantai.agent.tools.official.IAnthropicOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.IOpenAiOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.NativeToolSpec
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ResolvedAgentModel
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解析后的模型配置（[AgentLLMModelFactory.createModel] 用）。
 */
data class ModelConfig(
    val serviceId: String,
    val baseType: ApiProtocol,
    val modelId: String,
    val apiKey: String,
    val fullBaseUrl: String,
    val supportedOfficialTools: List<String> = emptyList(),
    val officialTools: List<String> = emptyList(),
    /**
     * Per-tool set of enabled function ids for [officialTools]. Empty set means
     * "tool enabled but no specific function"; the absence of an entry means
     * "tool not selected for this conversation".
     */
    val enabledOfficialFunctions: Map<String, Set<String>> = emptyMap(),
    /** Vendor tool endpoints may stay on the standard API host even for Anthropic model traffic. */
    val officialToolBaseUrl: String = fullBaseUrl,
)

internal fun ModelConfig.forConversation(
    configuration: ConversationToolConfiguration,
): ModelConfig {
    val functionsByTool = configuration.enabledOfficialFunctionIdsByService[serviceId].orEmpty()
    val enabledTools = supportedOfficialTools.filter { toolId ->
        functionsByTool[toolId].orEmpty().isNotEmpty()
    }
    return copy(
        officialTools = enabledTools,
        enabledOfficialFunctions = functionsByTool,
    )
}

@Singleton
class AgentLLMModelFactory @Inject constructor(
    private val modelServices: AgentModelConfigurationSource,
    private val openAiToolAdapters: Set<@JvmSuppressWildcards IOpenAiOfficialToolAdapter>,
    private val anthropicToolAdapters: Set<@JvmSuppressWildcards IAnthropicOfficialToolAdapter>,
) {
    /**
     * 从 [AgentModelConfigurationSource] 选当前模型配置。
     *
     * 优先用用户在聊天 TopAppBar 中央显式选择的模型；
     * 若选择为空 / 指向的服务被禁用 / 组或模型已不存在，自动回退到"第一个启用服务
     * + 第一个非空组的第一个模型"的旧逻辑。
     *
     * 没有可用配置时抛 [IllegalStateException]，由 UI 层提示用户在
     * Settings → Model Service 启用至少一个服务。不再使用任何硬编码兜底配置——
     */
    fun selectModelConfig(explicitSelection: ModelSelection?): ModelConfig {
        // 1. Explicit callers (for example the detached Bluetooth voice runner) do not mutate
        // the chat screen's process-wide current selection.
        val explicit = modelServices.resolveChatModel(explicitSelection)
        if (explicit != null) {
            return explicit.toModelConfig()
        }
        // 2. Prefer the model explicitly selected in the chat screen.
        val resolved = modelServices.resolveChatModel(
            modelServices.runtimeSelection.value
        )
        if (resolved != null) {
            return resolved.toModelConfig()
        }
        // 3. Fall back to the configured default/first available chat model.
        val fallback = modelServices.defaultSelection()
            ?: error("No enabled model service with a configured model. Enable one in Settings → Model Service.")
        val resolvedFallback = modelServices.resolveChatModel(fallback)
            ?: error("The default model selection is unavailable.")
        return resolvedFallback.toModelConfig()
    }


    fun createModel(
        cfg: ModelConfig,
        openAiNativeSpecs: List<NativeToolSpec.OpenAi> = emptyList(),
        anthropicNativeSpecs: List<NativeToolSpec.Anthropic> = emptyList(),
    ): Model =
        when (cfg.baseType) {
            ApiProtocol.Standard -> object : Openai(
                name = cfg.modelId,
                client = OpenAIOkHttpClient.builder()
                    .baseUrl(cfg.fullBaseUrl)
                    .apiKey(cfg.apiKey)
                    .build(),
            ) {
                override fun postProcessParams(
                    builder: ChatCompletionCreateParams.Builder,
                    request: LlmRequest,
                ) {
                    val tools = builder.build().tools().orElse(emptyList())
                    val adapted = openAiToolAdapters.fold(tools) { current, adapter ->
                        adapter.adapt(cfg, current, openAiNativeSpecs)
                    }
                    if (adapted !== tools) builder.tools(adapted)
                }
            }

            ApiProtocol.Anthropic -> object : Claude(
                name = cfg.modelId,
                client = AnthropicOkHttpClient.builder()
                    .baseUrl(cfg.fullBaseUrl)
                    .apiKey(cfg.apiKey)
                    .build(),
            ) {
                override fun postProcessParams(
                    builder: MessageCreateParams.Builder,
                    request: LlmRequest
                ) {
                    val tools = builder.build().tools().orElse(emptyList())
                    val adapted = anthropicToolAdapters.fold(tools) { current, adapter ->
                        adapter.adapt(cfg, current, anthropicNativeSpecs)
                    }
                    if (adapted !== tools) builder.tools(adapted)
                }
            }
        }

    fun selectFastModelConfig(): ModelConfig? {
        val resolved = modelServices.resolveChatModel(modelServices.fastSelection.value)
            ?: return null
        return resolved.toModelConfig()
    }

    private fun ResolvedAgentModel.toModelConfig(): ModelConfig = ModelConfig(
        serviceId = serviceId,
        baseType = protocol,
        modelId = modelId,
        apiKey = apiKey,
        fullBaseUrl = modelBaseUrl,
        supportedOfficialTools = supportedOfficialTools,
        officialTools = supportedOfficialTools,
        officialToolBaseUrl = officialToolBaseUrl,
    )
}
