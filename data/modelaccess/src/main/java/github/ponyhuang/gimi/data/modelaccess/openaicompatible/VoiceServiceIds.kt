package github.ponyhuang.gimi.data.modelaccess.openaicompatible

/**
 * 使用 OpenAI 兼容对话协议承载音频能力的服务标识。
 *
 * 这些服务的音频能力（合成与音频理解）都走 `{baseUrl}/chat/completions`：
 * 合成用请求体的 `audio` 参数，音频理解用 `input_audio` 内容块。
 *
 * 该集合是**显式声明**而非兜底：具备厂商自有语音协议的服务（MiniMax、百炼、MiMo）
 * 不在其中，由各自专用适配器承担——MiMo 虽同走对话协议，但 data-URI 音频编码与
 * `asr_options` 语种参数是它专属的契约。未列入的服务同样无法使用这些能力。
 *
 * DeepSeek 也不在此列：其对话协议不支持音频，语音能力由专属的 `/v1/tools/asr`、
 * `/v1/tools/tts` 端点适配器承担（`data/modelaccess/deepseek/speech`）。
 */
internal val OPENAI_COMPATIBLE_AUDIO_SERVICE_IDS: Set<String> = setOf(
    "openai",
    "kimi",
    "glm",
)
