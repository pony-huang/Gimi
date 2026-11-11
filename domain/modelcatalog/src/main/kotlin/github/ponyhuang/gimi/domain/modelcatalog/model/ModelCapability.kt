package github.ponyhuang.gimi.domain.modelcatalog.model

import kotlinx.serialization.Serializable

/**
 * 模型可承担的任务类型。
 *
 * 与 [MultimodalCapabilities] 正交：能力集合描述**输出形态**（这个模型能产出什么），
 * 多模态能力描述**输入形态**（这个模型能接收什么）。一个模型可以两者兼有——
 * 例如能听懂音频的对话模型同时具备 [LLM] 与 [ASR]。
 */
@Serializable
enum class ModelCapability {
    /** 可用于普通对话的文本模型。 */
    LLM,

    /** 文本转语音。 */
    TTS,

    /**
     * 专用语音转写服务：协议本身即"语音→文本"。
     *
     * 对应 MiniMax 的 multipart 上传与百炼的 recognition task 一类独立转写端点。
     */
    STT,

    /**
     * 音频理解模型：经由对话协议（`chat/completions` + `input_audio`）理解音频。
     *
     * 与 [STT] 的区别在于协议语义——这类模型本身是对话模型，音频是它的一种输入模态。
     */
    ASR,

    /**
     * 原生实时语音模型：单模型承担听/想/说，走常驻全双工 WebSocket。
     *
     * 不是级联能力——级联选择只按 [STT] / [TTS] / [ASR] 挑模型，因此天然不会
     * 拾取携带该标记的模型；实时语音的入口（模型选择、会话创建）以该标记为准。
     */
    Realtime,
}

/** 是否为语音识别类能力（把音频转成文本的两种协议语义）。 */
val ModelCapability.isRecognition: Boolean
    get() = this == ModelCapability.STT || this == ModelCapability.ASR

/** 是否为语音任务能力（识别与合成）；聊天模型不具备这些能力。 */
val ModelCapability.isVoiceTask: Boolean
    get() = isRecognition || this == ModelCapability.TTS

/**
 * 该模型承担的识别能力。
 *
 * 同时具备两种识别语义时优先专用转写服务（[ModelCapability.STT]）：它的协议语义更明确，
 * 结果也更稳定。界面判定与实际调用共用这一规则，避免两处结论不一致——
 * 否则界面可能放行一个实际会被送去音频理解端点的模型。
 */
val Model.recognitionCapability: ModelCapability?
    get() = when {
        ModelCapability.STT in capabilities -> ModelCapability.STT
        ModelCapability.ASR in capabilities -> ModelCapability.ASR
        else -> null
    }
