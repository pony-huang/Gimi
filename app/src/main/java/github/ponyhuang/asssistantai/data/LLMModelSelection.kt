package github.ponyhuang.asssistantai.data

/**
 * 用户在聊天 TopAppBar 中央显式选择的"当前模型"。
 *
 * @property serviceId 平台唯一 ID（如 `"deepseek"`）。
 * @property groupId 模型组唯一 ID（同一服务下可能多组）。
 * @property modelId 平台精确模型 ID（如 `deepseek-v4-pro`）。
 */
data class LLMModelSelection(
    val serviceId: String,
    val groupId: String,
    val modelId: String,
)
