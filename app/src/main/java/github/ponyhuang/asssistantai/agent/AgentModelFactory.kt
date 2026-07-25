package github.ponyhuang.asssistantai.agent

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.Model
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import github.ponyhuang.asssistantai.agent.model.Claude
import github.ponyhuang.asssistantai.agent.model.Openai
import github.ponyhuang.asssistantai.agent.tools.official.AnthropicOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.OpenAiOfficialToolAdapter
import github.ponyhuang.asssistantai.data.ApiBaseType
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解析后的模型配置（[AgentModelFactory.createModel] 用）。
 */
data class ModelConfig(
    val serviceId: String,
    val baseType: ApiBaseType,
    val modelId: String,
    val apiKey: String,
    val fullBaseUrl: String,
    val officialTools: List<String> = emptyList(),
    /** Vendor tool endpoints may stay on the standard API host even for Anthropic model traffic. */
    val officialToolBaseUrl: String = fullBaseUrl,
)

@Singleton
class AgentModelFactory @Inject constructor(
    private val modelServices: ModelServiceRepository,
    private val openAiToolAdapters: Set<@JvmSuppressWildcards OpenAiOfficialToolAdapter>,
    private val anthropicToolAdapters: Set<@JvmSuppressWildcards AnthropicOfficialToolAdapter>,
) {
    /**
     * 从 [ModelServiceRepository] 选当前模型配置。
     *
     * 优先用用户在聊天 TopAppBar 中央显式选择的模型；
     * 若选择为空 / 指向的服务被禁用 / 组或模型已不存在，自动回退到"第一个启用服务
     * + 第一个非空组的第一个模型"的旧逻辑。
     *
     * 没有可用配置时抛 [IllegalStateException]，由 UI 层提示用户在
     * Settings → Model Service 启用至少一个服务。不再使用任何硬编码兜底配置——
     * 模型密钥与地址必须来自 Store，由 [SeedData] 在 `AsssistantaiApp.onCreate` 注入。
     */
    fun selectModelConfig(explicitSelection: LLMModelSelection?): ModelConfig {
        // 1. Explicit callers (for example the detached Bluetooth voice runner) do not mutate
        // the chat screen's process-wide current selection.
        val explicit = modelServices.resolveChatSelection(explicitSelection)
        if (explicit != null) {
            return explicit.toModelConfig()
        }
        // 2. Prefer the model explicitly selected in the chat screen.
        val resolved = modelServices.resolveChatSelection(
            modelServices.currentSelection.value
        )
        if (resolved != null) {
            return resolved.toModelConfig()
        }
        // 3. Fall back to the configured default/first available chat model.
        val fallback = modelServices.defaultSelection()
            ?: error("No enabled model service with a configured model. Enable one in Settings → Model Service.")
        val resolvedFallback = modelServices.resolveChatSelection(fallback)
            ?: error("The default model selection is unavailable.")
        return resolvedFallback.toModelConfig()
    }


    fun createModel(cfg: ModelConfig): Model =
        when (cfg.baseType) {
            ApiBaseType.Standard -> object : Openai(
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
                        adapter.adapt(cfg, current)
                    }
                    if (adapted !== tools) builder.tools(adapted)
                }
            }

            ApiBaseType.Anthropic -> object : Claude(
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
                        adapter.adapt(cfg, current)
                    }
                    if (adapted !== tools) builder.tools(adapted)
                }
            }
        }

    fun selectFastModelConfig(): ModelConfig? {
        val resolved = modelServices.resolveChatSelection(modelServices.fastModelSelection.value)
            ?: return null
        return resolved.toModelConfig()
    }

    private fun ModelServiceRepository.ResolvedModel.toModelConfig(): ModelConfig {
        val service = provider
        return ModelConfig(
            serviceId = service.serviceId,
            baseType = service.baseType,
            modelId = model.modelId,
            apiKey = service.apiKey,
            fullBaseUrl = composeUrl(service.activeApiBaseUrl),
            officialTools = service.enabledOfficialTools,
            officialToolBaseUrl = composeUrl(service.apiBaseUrl),
        )
    }

    private fun composeUrl(apiBaseUrl: String): String = apiBaseUrl.trimEnd('/')
}
