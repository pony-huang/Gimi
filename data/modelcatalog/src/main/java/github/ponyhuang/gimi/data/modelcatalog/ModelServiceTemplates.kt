package github.ponyhuang.gimi.data.modelcatalog

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.DUAL_API_PROTOCOLS
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection

/**
 * 内置厂商模板清单。
 *
 * 每个模板统一承载该厂商已集成的对话、识别、合成与实时语音连接；模板是静态元数据
 * （不含用户凭据），实例由用户从模板派生并独立保存。模板 ID 同时是厂商适配
 * （语音协议、厂商工具矩阵、品牌图标）的分派键。
 *
 * 自定义 LLM 服务没有模板：协议、端点与模型全部由用户填写，只建一条对话连接。
 */
object ModelServiceTemplates {

    /** 对话连接通用的地址协议；WebSocket 仅厂商自有语音端点使用。 */
    private val HTTP_SCHEMES = setOf("http", "https")
    private val WSS_SCHEMES = setOf("wss")

    private fun chatConnection(
        defaultEndpoint: String,
        protocols: List<ApiProtocol> = DUAL_API_PROTOCOLS,
        additionalEndpoints: Map<ApiProtocol, String> = emptyMap(),
    ) = TemplateConnection(
        capability = ServiceCapability.Conversation,
        protocols = protocols,
        defaultEndpoints = additionalEndpoints + (protocols.first() to defaultEndpoint),
        endpointPolicy = EndpointPolicy.UserConfigured,
        endpointSchemes = HTTP_SCHEMES,
    )

    private fun vendorSpeechConnection(
        capability: ServiceCapability,
        endpoint: String,
        supportsWorkspace: Boolean = false,
        catalog: List<Model> = emptyList(),
    ) = TemplateConnection(
        capability = capability,
        // 语音连接不使用聊天协议切换，这里只是让统一的数据形态成立。
        protocols = listOf(ApiProtocol.OpenaiChatcompletions),
        defaultEndpoints = mapOf(ApiProtocol.OpenaiChatcompletions to endpoint),
        endpointPolicy = EndpointPolicy.VendorManaged,
        endpointSchemes = if (endpoint.startsWith("wss")) WSS_SCHEMES else HTTP_SCHEMES,
        supportsWorkspace = supportsWorkspace,
        catalog = catalog,
    )

    private fun stt(modelId: String) = Model(modelId, modelId, capabilities = setOf(ModelCapability.STT))
    private fun tts(modelId: String) = Model(modelId, modelId, capabilities = setOf(ModelCapability.TTS))
    private fun realtime(modelId: String) = Model(modelId, modelId, capabilities = setOf(ModelCapability.Realtime))

    private val openAi = ModelServiceTemplate(
        id = "openai",
        name = "OpenAI",

        homepageUrl = "https://openai.com/",
        keyHelpUrl = "https://platform.openai.com/api-keys",
        connections = listOf(
            chatConnection(defaultEndpoint = "https://api.openai.com/v1", protocols = listOf(ApiProtocol.OpenaiChatcompletions)),
            vendorSpeechConnection(
                ServiceCapability.Recognition,
                SpeechEndpoints.OPENAI_SPEECH_BASE_URL,
                catalog = listOf(
                    stt("whisper-1"),
                    stt("gpt-4o-mini-transcribe"),
                    stt("gpt-4o-transcribe"),
                ),
            ),
            vendorSpeechConnection(
                ServiceCapability.Synthesis,
                SpeechEndpoints.OPENAI_SPEECH_BASE_URL,
                catalog = listOf(
                    tts("gpt-4o-mini-tts"),
                    tts("tts-1"),
                    tts("tts-1-hd"),
                ),
            ),
            vendorSpeechConnection(
                ServiceCapability.Realtime,
                SpeechEndpoints.OPENAI_REALTIME_DEFAULT,
                catalog = listOf(
                    realtime("gpt-realtime-2.1"),
                    realtime("gpt-realtime-2.1-mini"),
                ),
            ),
        ),
    )

    private val miniMax = ModelServiceTemplate(
        id = "minimax",
        name = "MiniMax",

        homepageUrl = "https://www.minimaxi.com/",
        keyHelpUrl = "https://platform.minimaxi.com/user-center/basic-information/interface-key",
        connections = listOf(
            chatConnection(
                defaultEndpoint = "https://api.minimaxi.com/v1",
                additionalEndpoints = mapOf(ApiProtocol.Anthropic to "https://api.minimaxi.com/anthropic"),
            ),
            vendorSpeechConnection(
                ServiceCapability.Recognition,
                "https://api.minimaxi.com/v1",
                catalog = listOf(stt("asr-1.0")),
            ),
            vendorSpeechConnection(
                ServiceCapability.Synthesis,
                "https://api.minimaxi.com/v1",
                catalog = listOf(
                    tts("speech-2.8-hd"),
                    tts("speech-2.8-turbo"),
                    tts("speech-2.6-hd"),
                    tts("speech-2.6-turbo"),
                    tts("speech-02-hd"),
                    tts("speech-02-turbo"),
                ),
            ),
        ),
    )

    private val mimo = ModelServiceTemplate(
        id = "mimo",
        name = "MiMo",

        homepageUrl = "https://mimo.mi.com/",
        keyHelpUrl = "https://platform.xiaomimimo.com/console/api-keys",
        connections = listOf(
            // 官方文档 https://mimo.mi.com/docs/zh-CN/api/audio/：ASR/TTS 都走
            // {baseUrl}/chat/completions 的 OpenAI 兼容对话协议，模型名随官方文档演进。
            chatConnection(
                defaultEndpoint = "https://api.xiaomimimo.com/v1",
                additionalEndpoints = mapOf(ApiProtocol.Anthropic to "https://api.xiaomimimo.com/anthropic"),
            ),
            vendorSpeechConnection(
                ServiceCapability.Recognition,
                "https://api.xiaomimimo.com/v1",
                catalog = listOf(
                    Model(
                        id = "mimo-v2.5-asr",
                        name = "mimo-v2.5-asr",
                        capabilities = setOf(ModelCapability.STT, ModelCapability.ASR),
                    ),
                ),
            ),
            vendorSpeechConnection(
                ServiceCapability.Synthesis,
                "https://api.xiaomimimo.com/v1",
                catalog = listOf(tts("mimo-v2.5-tts")),
            ),
        ),
    )

    private val qwen = ModelServiceTemplate(
        id = "qwen",
        name = "Qwen",

        homepageUrl = "https://bailian.console.aliyun.com/",
        keyHelpUrl = "https://bailian.console.aliyun.com/?tab=model#/api-key",
        connections = listOf(
            vendorSpeechConnection(
                ServiceCapability.Recognition,
                SpeechEndpoints.QWEN_AUDIO_CN,
                supportsWorkspace = true,
                catalog = listOf(
                    // message 面向整段短语音，streaming 面向连续流式识别；
                    // 两者协议不同但同属专用转写服务，能力都是 STT。
                    stt("qwen-audio-3.1-asr-flash-message"),
                    stt("qwen-audio-3.1-asr-flash-streaming"),
                    stt("qwen-audio-3.0-asr-flash-streaming"),
                ),
            ),
            vendorSpeechConnection(
                ServiceCapability.Synthesis,
                SpeechEndpoints.QWEN_AUDIO_CN,
                supportsWorkspace = true,
                catalog = listOf(
                    // flash 与 plus 的音色表不同，详见 QwenAudioTtsVoices。
                    tts("qwen-audio-3.1-tts-flash"),
                    tts("qwen-audio-3.1-tts-plus"),
                    tts("qwen-audio-3.0-tts-flash"),
                    tts("qwen-audio-3.0-tts-plus"),
                ),
            ),
            vendorSpeechConnection(
                ServiceCapability.Realtime,
                SpeechEndpoints.QWEN_AUDIO_REALTIME_CN,
                supportsWorkspace = true,
                catalog = listOf(
                    // 会话音色由服务配置（realtimeVoice）下发而非级联 TTS；Realtime 标记
                    // 不参与级联选择（级联只按 STT/TTS/ASR 挑模型），实时语音入口按该标记分派。
                    realtime("qwen-audio-3.1-realtime-plus"),
                    realtime("qwen-audio-3.1-realtime-flash"),
                    realtime("qwen-audio-3.0-realtime-plus"),
                    realtime("qwen-audio-3.0-realtime-flash"),
                ),
            ),
        ),
    )

    private val deepSeek = ModelServiceTemplate(
        id = "deepseek",
        name = "DeepSeek",

        homepageUrl = "https://www.deepseek.com/",
        keyHelpUrl = "https://platform.deepseek.com/api_keys",
        connections = listOf(
            chatConnection(
                defaultEndpoint = "https://api.deepseek.com",
                additionalEndpoints = mapOf(ApiProtocol.Anthropic to "https://api.deepseek.com/anthropic"),
            ),
            // 语音走固定的 /v1/tools/asr、/v1/tools/tts 端点，请求不带模型参数；
            // 目录里的模型 ID 只是选择器占位（https://api-docs.deepseek.com/zh-cn/guides/asr.md）。
            vendorSpeechConnection(
                ServiceCapability.Recognition,
                "https://api.deepseek.com",
                catalog = listOf(stt("deepseek-asr")),
            ),
            vendorSpeechConnection(
                ServiceCapability.Synthesis,
                "https://api.deepseek.com",
                catalog = listOf(tts("deepseek-tts")),
            ),
        ),
    )

    private val anthropic = ModelServiceTemplate(
        id = "anthropic",
        name = "Anthropic",

        homepageUrl = "https://www.anthropic.com/",
        keyHelpUrl = "https://console.anthropic.com/settings/keys",
        connections = listOf(
            chatConnection(
                defaultEndpoint = "https://api.anthropic.com",
                protocols = listOf(ApiProtocol.Anthropic),
            ),
        ),
    )

    private val kimi = ModelServiceTemplate(
        id = "kimi",
        name = "Moonshot",

        homepageUrl = "https://www.kimi.com/",
        keyHelpUrl = "https://platform.kimi.com/console/api-keys",
        connections = listOf(
            chatConnection(
                defaultEndpoint = "https://api.moonshot.cn/v1",
                additionalEndpoints = mapOf(ApiProtocol.Anthropic to "https://api.moonshot.cn/anthropic"),
            ),
        ),
    )

    private val glm = ModelServiceTemplate(
        id = "glm",
        name = "GLM",

        homepageUrl = "https://bigmodel.cn/",
        keyHelpUrl = "https://bigmodel.cn/usercenter/proj-mgmt/apikeys",
        connections = listOf(
            chatConnection(
                defaultEndpoint = "https://open.bigmodel.cn/api/paas/v4/",
                additionalEndpoints = mapOf(ApiProtocol.Anthropic to "https://open.bigmodel.cn/api/anthropic"),
            ),
        ),
    )

    /**
     * 模板清单；展示顺序即添加目录顺序。
     *
     * Gemini 暂不进入模板目录：ADK Kotlin 的 Gemini 当前禁止在 Android 使用
     * API Key / Google Credentials（https://github.com/google/adk-kotlin）。
     */
    val all: List<ModelServiceTemplate> = listOf(
        openAi,
        miniMax,
        mimo,
        qwen,
        deepSeek,
        anthropic,
        kimi,
        glm,
    )

    fun fromId(templateId: String): ModelServiceTemplate? = all.firstOrNull { it.id == templateId }
}
