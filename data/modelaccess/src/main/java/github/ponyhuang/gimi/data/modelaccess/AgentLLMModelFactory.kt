package github.ponyhuang.gimi.data.modelaccess

import java.security.MessageDigest
import github.ponyhuang.gimi.domain.modelcatalog.model.OPENAI_CHATBOT_SEARCH_MODEL
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.adk.kt.models.Gemini
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.openai.ChatCompletions
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.llm.InputAudioDataEncoding
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.llm.OpenAiCompatibleChatCompletions
import com.google.adk.kt.types.HttpOptions
import github.ponyhuang.gimi.domain.modelaccess.AgentLlmModelSource
import github.ponyhuang.gimi.domain.modelaccess.ModelConfig
import github.ponyhuang.gimi.domain.modelaccess.isOfficialDeepSeekAnthropicEndpoint
import github.ponyhuang.gimi.domain.modelaccess.toolLoadingMode
import github.ponyhuang.gimi.domain.modelaccess.toRuntimeMetadata
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.FileIngestMode
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedAgentModel
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import javax.inject.Singleton
import github.ponyhuang.gimi.data.modelaccess.anthropic.llm.Claude
import github.ponyhuang.gimi.data.modelaccess.deepseek.llm.DeepSeekMessageChat
import github.ponyhuang.gimi.data.modelaccess.deepseek.llm.DeepSeekMessageToolLoadingRequestConverter
import github.ponyhuang.gimi.data.modelaccess.deepseek.llm.latestReminderContent

/**
 * 协议适配装配：把 [AgentLlmModelSource] 的模型选择实现为可绑定具体类，
 * 并按 [ModelConfig] 构造 ADK Model（厂商 SDK 适配的唯一入口）。
 */
@Singleton
class DefaultAgentLLMModelFactory @Inject constructor(
    private val modelServices: AgentModelConfigurationSource,
) : AgentLlmModelSource {
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
    override fun selectModelConfig(explicitSelection: ModelSelection?): ModelConfig {
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


    /**
     * 按协议装配模型。
     *
     * @param conversationTitleEnabled 是否请求厂商随回答返回会话标题（DeepSeek 内联标题）。
     *   仅 Chatbot 形态开启：助手形态的标题由标题插件的独立快模型请求生成。
     */
    fun createModel(
        cfg: ModelConfig,
        conversationTitleEnabled: Boolean = false,
        chatbotConnectionId: String? = null,
    ): Model {
        val replayScope = chatbotConnectionId?.let { connectionId ->
            val identity = listOf(cfg.templateId, cfg.baseType.name, cfg.serviceId, connectionId, cfg.fullBaseUrl)
                .joinToString(separator = "\n")
            MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
        return AttachmentResolvingModel(
            documentIngestMode = cfg.documentIngestMode,
            audioDelegate = if (cfg.templateId == "mimo") lazy { createMiMoAudioModel(cfg) } else null,
            delegate = when (cfg.baseType) {
                // OpenaiChatcompletions 完全采用官方后端的能力边界，不补思考流、非图片媒体或厂商原生工具。
                // 实例开启文件上传时改用 fork 的 file 块适配器：ADK 官方实现是 final class 且
                // 无转换器注入点，无法发出 file 引用块（2026-11 ADK 1.5.0 现状）。
                ApiProtocol.OpenaiChatcompletions ->
                    if (conversationTitleEnabled && cfg.templateId == "openai" && cfg.modelId == OPENAI_CHATBOT_SEARCH_MODEL) {
                        OpenAiCompatibleChatCompletions(
                            name = cfg.modelId,
                            apiKey = cfg.apiKey,
                            httpOptions = HttpOptions(baseUrl = cfg.fullBaseUrl),
                            webSearch = true,
                        )
                    } else if (cfg.documentIngestMode == FileIngestMode.FILE_REFERENCE) {
                        OpenAiCompatibleChatCompletions(
                            name = cfg.modelId,
                            apiKey = cfg.apiKey,
                            httpOptions = HttpOptions(baseUrl = cfg.fullBaseUrl),
                            audioEncoding = audioEncodingFor(cfg),
                        )
                    } else {
                        ChatCompletions(
                            name = cfg.modelId,
                            apiKey = cfg.apiKey,
                            httpOptions = HttpOptions(baseUrl = cfg.fullBaseUrl),
                        )
                    }

                ApiProtocol.Anthropic ->
                    if (isOfficialDeepSeekAnthropicEndpoint(cfg.toRuntimeMetadata())) {
                        // 仅官方端点走 DeepSeek 专属适配器：首轮注入 title 参数、转换器注入
                        // latest_reminder；第三方代理回落通用 Claude 适配器，零扩展字段风险。
                        DeepSeekMessageChat(
                            name = cfg.modelId,
                            conversationTitleEnabled = conversationTitleEnabled,
                            webSearchReplayScope = replayScope,
                            requestConverter = DeepSeekMessageToolLoadingRequestConverter(
                                cfg.modelId, 8196,
                                toolLoadingMode(cfg.toRuntimeMetadata()),
                                latestReminder = { latestReminderContent() },
                                webSearchReplayScope = replayScope,
                            )::convert,
                            client = AnthropicOkHttpClient.builder()
                                .baseUrl(cfg.fullBaseUrl)
                                .apiKey(cfg.apiKey)
                                .build(),
                        )
                    } else {
                        Claude(
                            name = cfg.modelId,
                            webSearchReplayScope = replayScope.takeIf { cfg.templateId == "anthropic" },
                            client = AnthropicOkHttpClient.builder()
                                .baseUrl(cfg.fullBaseUrl)
                                .apiKey(cfg.apiKey)
                                .build(),
                        )
                    }

                // ADK 原生 Gemini：只需 API Key，端点固定为 Google 默认地址。
                ApiProtocol.Gemini -> Gemini(
                    name = cfg.modelId,
                    apiKey = cfg.apiKey,
                )
            },
        )
    }

    override fun selectFastModelConfig(): ModelConfig? {
        val resolved = modelServices.resolveChatModel(modelServices.fastSelection.value)
            ?: return null
        return resolved.toModelConfig()
    }

    internal fun createSemiCascadedModel(config: ModelConfig): Model {
        if (config.templateId != "mimo") return createModel(config)
        return AttachmentResolvingModel(
            documentIngestMode = config.documentIngestMode,
            delegate = createMiMoAudioModel(config),
        )
    }

    private fun createMiMoAudioModel(config: ModelConfig): Model {
        // MiMo 的 WAV 输入走 Chat Completions，沿用所选连接的主机和凭据。
        val endpoint = config.fullBaseUrl.trimEnd('/')
        val baseUrl = if (config.baseType == ApiProtocol.Anthropic) {
            check(endpoint.endsWith("/anthropic")) {
                "The MiMo connection must use an /anthropic base URL or configure Chat Completions."
            }
            endpoint.removeSuffix("/anthropic") + "/v1"
        } else {
            endpoint
        }
        return OpenAiCompatibleChatCompletions(
            name = config.modelId,
            apiKey = config.apiKey,
            httpOptions = HttpOptions(baseUrl = baseUrl),
            audioEncoding = audioEncodingFor(config),
        )
    }

    /** MiMo 的 `input_audio.data` 要 data-URI；其它 OpenAI 兼容后端走裸 base64 + format。 */
    private fun audioEncodingFor(cfg: ModelConfig) = if (cfg.templateId == "mimo") {
        InputAudioDataEncoding.MIMO_DATA_URI
    } else {
        InputAudioDataEncoding.OPENAI_BASE64
    }

    /** 格式化推荐只走快速模型所属实例的 OpenAI 兼容对话端点，不沿用聊天协议。 */
    override fun forRecommendationJson(config: ModelConfig): ModelConfig {
        if (config.baseType == ApiProtocol.OpenaiChatcompletions) {
            return config
        }
        val conversation = modelServices.currentInstances()
            .firstOrNull { it.id == config.serviceId }
            ?.conversationConnection
        check(conversation != null && conversation.protocol == ApiProtocol.OpenaiChatcompletions) {
            "The fast model service does not support OpenAI-compatible JSON output."
        }
        val chatCompletionsBaseUrl = conversation.endpoint.trim().trimEnd('/')
        check(chatCompletionsBaseUrl.isNotBlank()) {
            "The fast model service has no OpenAI-compatible base URL."
        }
        return config.copy(baseType = ApiProtocol.OpenaiChatcompletions, fullBaseUrl = chatCompletionsBaseUrl)
    }

    private fun ResolvedAgentModel.toModelConfig(): ModelConfig = ModelConfig(
        serviceId = serviceId,
        templateId = templateId,
        baseType = protocol,
        modelId = modelId,
        apiKey = apiKey,
        fullBaseUrl = modelBaseUrl,
        supportsImages = supportsImages,
        documentIngestMode = documentIngestMode,
    )
}
