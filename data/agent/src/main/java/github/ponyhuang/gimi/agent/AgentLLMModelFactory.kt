package github.ponyhuang.gimi.agent

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.adk.kt.models.Gemini
import com.google.adk.kt.models.Model
import com.openai.client.okhttp.OpenAIOkHttpClient
import github.ponyhuang.gimi.agent.model.Claude
import github.ponyhuang.gimi.agent.model.Openai
import github.ponyhuang.gimi.agent.tools.official.anthropic.AnthropicOfficialToolset
import github.ponyhuang.gimi.agent.tools.official.openai.OpenaiOfficialToolset
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedAgentModel
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 解析后的模型配置（[AgentLLMModelFactory.createModel] 用）。
 *
 * 只描述当前请求的不可变模型运行参数（服务、协议、模型 ID、端点和凭据）。
 * 会话级工具勾选仍由 invocation context 提供，不与凭据混存。
 *
 * @property serviceId 模型服务稳定 ID，用于匹配当前会话的官方函数选择。
 * @property baseType 当前服务使用的 API 协议。
 * @property modelId 实际请求模型 ID，也是厂商模型家族判断的唯一依据。
 * @property apiKey 当前模型服务的凭据，仅保留在请求模型配置中。
 * @property fullBaseUrl 已解析到协议所需层级的模型服务端点。
 */
data class ModelConfig(
    val serviceId: String,
    val baseType: ApiProtocol,
    val modelId: String,
    val apiKey: String,
    val fullBaseUrl: String,
)

/**
 * 可安全写入 ADK `RunConfig.customMetadata` 的模型运行信息。
 *
 * 该类型刻意不包含 API Key：ADK 会把 custom metadata 合并进持久化 Event，
 * 凭据必须在官方工具真正执行前根据 [serviceId] 从安全配置源重新解析。
 *
 * @property serviceId 模型服务稳定 ID。
 * @property baseType 当前请求使用的协议。
 * @property modelId 实际模型 ID。
 * @property fullBaseUrl 当前协议对应的完整服务端点。
 */
data class ModelRuntimeMetadata(
    val serviceId: String,
    val baseType: ApiProtocol,
    val modelId: String,
    val fullBaseUrl: String,
)

internal fun ModelConfig.toRuntimeMetadata(): ModelRuntimeMetadata = ModelRuntimeMetadata(
    serviceId = serviceId,
    baseType = baseType,
    modelId = modelId,
    fullBaseUrl = fullBaseUrl,
)

@Singleton
class AgentLLMModelFactory @Inject constructor(
    private val modelServices: AgentModelConfigurationSource,
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


    fun createModel(cfg: ModelConfig): Model =
        when (cfg.baseType) {
            ApiProtocol.Standard -> Openai(
                name = cfg.modelId,
                client = OpenAIOkHttpClient.builder()
                    .baseUrl(cfg.fullBaseUrl)
                    .apiKey(cfg.apiKey)
                    .build(),
                providerBuiltInToolNames = cfg.providerBuiltInToolNames(),
            )

            ApiProtocol.Anthropic -> Claude(
                name = cfg.modelId,
                client = AnthropicOkHttpClient.builder()
                    .baseUrl(cfg.fullBaseUrl)
                    .apiKey(cfg.apiKey)
                    .build(),
                providerBuiltInToolNames = cfg.providerBuiltInToolNames(),
            )

            // ADK 原生 Gemini：只需 API Key，端点固定为 Google 默认地址。
            ApiProtocol.Gemini -> Gemini(
                name = cfg.modelId,
                apiKey = cfg.apiKey,
            )
        }

    /**
     * 当前服务以厂商原生形态执行的官方内置工具名。
     *
     * 与各 official toolset 的服务门控保持一致：仅 openai/mimo（Standard）和
     * anthropic/minimax（Anthropic）的 web_search 由厂商远端执行；其余服务的
     * 同名声明（GLM 本地搜索、Kimi 公式、MCP 工具）都是可执行函数，不得转换。
     */
    private fun ModelConfig.providerBuiltInToolNames(): Set<String> = when (baseType) {
        ApiProtocol.Standard -> when (serviceId) {
            "openai", "mimo" -> setOf(OpenaiOfficialToolset.WEB_SEARCH_TOOL_ID)
            else -> emptySet()
        }

        ApiProtocol.Anthropic -> when (serviceId) {
            "anthropic", "minimax" -> setOf(AnthropicOfficialToolset.WEB_SEARCH_TOOL_ID)
            else -> emptySet()
        }

        // Gemini 暂无厂商远端执行的官方内置工具。
        ApiProtocol.Gemini -> emptySet()
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
        fullBaseUrl = modelBaseUrl
    )
}
