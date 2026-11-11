package github.ponyhuang.gimi.data.modelcatalog

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints

/**
 * 语音服务目录。
 *
 * 与 [LLMModelConfigs] 分开维护：聊天服务走 HTTP 文本接口，语音服务走厂商自有的语音协议
 * （包括 HTTP 和阿里云百炼的 WebSocket 实时接口）。独立服务标识隔离密钥和开关，避免
 * "LLM 供应商"这一概念在后续接入千问聊天 API 时被迫同时承载聊天与语音两套配置。
 *
 * 原生实时语音不再单独设服务：realtime 模型以 [ModelCapability.Realtime] 标记挂在同厂商
 * 语音服务下，与级联识别/合成共用一套密钥、开关和业务空间配置。
 */
object SpeechModelConfigs {
    /** 按 serviceId 查找语音服务模板。 */
    fun fromServiceId(serviceId: String): LLMModelProvider? =
        services.firstOrNull { it.serviceId == serviceId }

    /** 取该 serviceId 允许的接口标准；聊天服务优先，其次语音服务。 */
    fun supportedBaseTypesFor(serviceId: String): List<ApiBaseType> =
        LLMModelConfigs.fromServiceId(serviceId)?.supportedBaseTypes
            ?: fromServiceId(serviceId)?.supportedBaseTypes
            ?: DUAL_API_BASE_TYPES

    /** 华北2（北京）默认接入端点；端点由厂商自有，业务空间专属域名由业务空间 ID 在请求期构造。 */
    const val QWEN_AUDIO_ENDPOINT = SpeechEndpoints.QWEN_AUDIO_CN

    private val qwenAudioProvider = LLMModelProvider(
        serviceId = "qwen-audio",
        serviceName = "Qwen",
        isEnabled = false,
        apiKey = "",
        apiBaseUrl = QWEN_AUDIO_ENDPOINT,
        baseType = ApiBaseType.OpenaiChatcompletions,
        // 语音服务没有可切换的接口标准，这里只是让统一的数据形态成立，取值不被实际使用。
        supportedBaseTypes = listOf(ApiBaseType.OpenaiChatcompletions),
        anthropicBaseUrl = QWEN_AUDIO_ENDPOINT,
        lLMModelGroups = listOf(
            LLMModelGroup(
                groupId = "qwen-audio-asr",
                groupName = "语音识别",
                models = listOf(
                    // message 面向整段短语音，streaming 面向连续流式识别；
                    // 两者协议不同但同属专用转写服务，能力都是 STT。
                    LLMModelItem(
                        modelId = "qwen-audio-3.1-asr-flash-message",
                        modelName = "qwen-audio-3.1-asr-flash-message",
                        capabilities = setOf(ModelCapability.STT),
                    ),
                    LLMModelItem(
                        modelId = "qwen-audio-3.1-asr-flash-streaming",
                        modelName = "qwen-audio-3.1-asr-flash-streaming",
                        capabilities = setOf(ModelCapability.STT),
                    ),
                    LLMModelItem(
                        modelId = "qwen-audio-3.0-asr-flash-streaming",
                        modelName = "qwen-audio-3.0-asr-flash-streaming",
                        capabilities = setOf(ModelCapability.STT),
                    ),
                ),
            ),
            LLMModelGroup(
                groupId = "qwen-audio-tts",
                groupName = "语音合成",
                models = listOf(
                    // flash 与 plus 的音色表不同，详见 QwenAudioTtsVoices。
                    LLMModelItem(
                        modelId = "qwen-audio-3.1-tts-flash",
                        modelName = "qwen-audio-3.1-tts-flash",
                        capabilities = setOf(ModelCapability.TTS),
                    ),
                    LLMModelItem(
                        modelId = "qwen-audio-3.1-tts-plus",
                        modelName = "qwen-audio-3.1-tts-plus",
                        capabilities = setOf(ModelCapability.TTS),
                    ),
                    LLMModelItem(
                        modelId = "qwen-audio-3.0-tts-flash",
                        modelName = "qwen-audio-3.0-tts-flash",
                        capabilities = setOf(ModelCapability.TTS),
                    ),
                    LLMModelItem(
                        modelId = "qwen-audio-3.0-tts-plus",
                        modelName = "qwen-audio-3.0-tts-plus",
                        capabilities = setOf(ModelCapability.TTS),
                    ),
                ),
            ),
            LLMModelGroup(
                groupId = "qwen-audio-realtime",
                groupName = "实时语音对话",
                models = listOf(
                    // 会话音色由服务配置（realtimeVoice）下发而非级联 TTS；Realtime 标记
                    // 不参与级联选择（级联只按 STT/TTS/ASR 挑模型），实时语音入口按该标记分派。
                    LLMModelItem(
                        modelId = "qwen-audio-3.1-realtime-plus",
                        modelName = "qwen-audio-3.1-realtime-plus",
                        capabilities = setOf(ModelCapability.Realtime),
                    ),
                    LLMModelItem(
                        modelId = "qwen-audio-3.1-realtime-flash",
                        modelName = "qwen-audio-3.1-realtime-flash",
                        capabilities = setOf(ModelCapability.Realtime),
                    ),
                    LLMModelItem(
                        modelId = "qwen-audio-3.0-realtime-plus",
                        modelName = "qwen-audio-3.0-realtime-plus",
                        capabilities = setOf(ModelCapability.Realtime),
                    ),
                    LLMModelItem(
                        modelId = "qwen-audio-3.0-realtime-flash",
                        modelName = "qwen-audio-3.0-realtime-flash",
                        capabilities = setOf(ModelCapability.Realtime),
                    ),
                ),
            ),
        ),
        kind = ModelServiceKind.Speech,
        homepageUrl = "https://bailian.console.aliyun.com/",
        keyHelpUrl = "https://bailian.console.aliyun.com/?tab=model#/api-key",
    )

    private val minimaxProvider = LLMModelType.MiniMax.toProvider(
        modelGroups = listOf(
            LLMModelGroup(
                groupId = "minimax-stt",
                groupName = "Minimax Speech Recognition",
                isExpanded = true,
                models = listOf(
                    LLMModelItem("asr-1.0", "asr-1.0", capabilities = setOf(ModelCapability.STT)),
                ),
            ),
            LLMModelGroup(
                groupId = "minimax-tts",
                groupName = "Minimax Speech",
                isExpanded = true,
                models = listOf(
                    LLMModelItem("speech-2.8-hd", "speech-2.8-hd", capabilities = setOf(ModelCapability.TTS)),
                    LLMModelItem("speech-2.8-turbo", "speech-2.8-turbo", capabilities = setOf(ModelCapability.TTS)),
                    LLMModelItem("speech-2.6-hd", "speech-2.6-hd", capabilities = setOf(ModelCapability.TTS)),
                    LLMModelItem("speech-2.6-turbo", "speech-2.6-turbo", capabilities = setOf(ModelCapability.TTS)),
                    LLMModelItem("speech-02-hd", "speech-02-hd", capabilities = setOf(ModelCapability.TTS)),
                    LLMModelItem("speech-02-turbo", "speech-02-turbo", capabilities = setOf(ModelCapability.TTS)),
                ),
            ),
        ),
    ).copy(
        serviceId = "minimax-speech",
        kind = ModelServiceKind.Speech,
        supportedBaseTypes = listOf(ApiBaseType.OpenaiChatcompletions),
        endpointSchemes = setOf("http", "https"),
    )

    private val mimoProvider = LLMModelType.Mimo.toProvider(
        // 官方文档 https://mimo.mi.com/docs/zh-CN/api/audio/：ASR/TTS 都走
        // {baseUrl}/chat/completions 的 OpenAI 兼容对话协议，模型名随官方文档演进。
        modelGroups = listOf(
            LLMModelGroup(
                groupId = "mimo-speech-asr",
                groupName = "语音识别",
                isExpanded = true,
                models = listOf(
                    LLMModelItem(
                        modelId = "mimo-v2.5-asr",
                        modelName = "mimo-v2.5-asr",
                        capabilities = setOf(ModelCapability.STT, ModelCapability.ASR),
                    ),
                ),
            ),
            LLMModelGroup(
                groupId = "mimo-speech-tts",
                groupName = "语音合成",
                isExpanded = true,
                models = listOf(
                    LLMModelItem(
                        modelId = "mimo-v2.5-tts",
                        modelName = "mimo-v2.5-tts",
                        capabilities = setOf(ModelCapability.TTS),
                    ),
                ),
            ),
        ),
    ).copy(
        serviceId = "mimo-speech",
        serviceName = "MiMo",
        kind = ModelServiceKind.Speech,
        supportedBaseTypes = listOf(ApiBaseType.OpenaiChatcompletions),
        endpointSchemes = setOf("http", "https"),
    )

    private val openAiSpeechProvider = LLMModelType.OpenAI.toProvider(
        // 官方文档 https://developers.openai.com/api/reference：
        // 识别走 POST {baseUrl}/audio/transcriptions，合成走 POST {baseUrl}/audio/speech；
        // 实时语音走常驻 wss 会话（wss://api.openai.com/v1/realtime）。三类端点官方唯一、
        // 不可自定义，端点地址由能力层按厂商自有策略构造，基址仅作兜底展示。
        modelGroups = listOf(
            LLMModelGroup(
                groupId = "openai-speech-stt",
                groupName = "语音识别",
                isExpanded = true,
                models = listOf(
                    LLMModelItem(
                        modelId = "whisper-1",
                        modelName = "whisper-1",
                        capabilities = setOf(ModelCapability.STT),
                    ),
                    LLMModelItem(
                        modelId = "gpt-4o-mini-transcribe",
                        modelName = "gpt-4o-mini-transcribe",
                        capabilities = setOf(ModelCapability.STT),
                    ),
                    LLMModelItem(
                        modelId = "gpt-4o-transcribe",
                        modelName = "gpt-4o-transcribe",
                        capabilities = setOf(ModelCapability.STT),
                    ),
                ),
            ),
            LLMModelGroup(
                groupId = "openai-speech-tts",
                groupName = "语音合成",
                isExpanded = true,
                models = listOf(
                    LLMModelItem(
                        modelId = "gpt-4o-mini-tts",
                        modelName = "gpt-4o-mini-tts",
                        capabilities = setOf(ModelCapability.TTS),
                    ),
                    LLMModelItem(
                        modelId = "tts-1",
                        modelName = "tts-1",
                        capabilities = setOf(ModelCapability.TTS),
                    ),
                    LLMModelItem(
                        modelId = "tts-1-hd",
                        modelName = "tts-1-hd",
                        capabilities = setOf(ModelCapability.TTS),
                    ),
                ),
            ),
            LLMModelGroup(
                groupId = "openai-speech-realtime",
                groupName = "实时语音对话",
                models = listOf(
                    // 官方文档 https://developers.openai.com/api/docs/guides/realtime：
                    // 常驻全双工会话直连 wss，模型以查询参数携带。会话音色由服务配置
                    // （realtimeVoice）下发，见 OpenAiRealtimeVoices；Realtime 标记不参与级联选择。
                    LLMModelItem(
                        modelId = "gpt-realtime-2.1",
                        modelName = "gpt-realtime-2.1",
                        capabilities = setOf(ModelCapability.Realtime),
                    ),
                    LLMModelItem(
                        modelId = "gpt-realtime-2.1-mini",
                        modelName = "gpt-realtime-2.1-mini",
                        capabilities = setOf(ModelCapability.Realtime),
                    ),
                ),
            ),
        ),
    ).copy(
        serviceId = "openai-speech",
        kind = ModelServiceKind.Speech,
        supportedBaseTypes = listOf(ApiBaseType.OpenaiChatcompletions),
        endpointSchemes = setOf("http", "https"),
    )

    /** 语音服务清单；配置标识独立于同厂商 LLM 服务。 */
    val services: List<LLMModelProvider> = listOf(
        qwenAudioProvider,
        minimaxProvider,
        mimoProvider,
        openAiSpeechProvider,
    )
}
