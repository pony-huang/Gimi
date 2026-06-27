package github.ponyhuang.asssistantai.data

/**
 * 用户在聊天 TopAppBar 中央显式选择的"当前模型"。
 *
 * - 三元组 `(serviceId, groupId, modelId)` 与模型服务仓库 API 形状保持一致，
 *   不需要在调用方再补 groupId。
 * - 用 `data class` 自动生成 `equals` / `hashCode`，`MutableStateFlow` 据此做去重与变更通知。
 * - 会话级持久化由 `ConversationSettingsStore` 负责；运行时由 `ModelServiceRepository`
 *   装载当前打开会话的选择，供 `AgentFactory.selectModelConfig` 使用。
 *
 * @property serviceId 平台唯一 ID（如 `"deepseek"`）。
 * @property groupId 模型组唯一 ID（同一服务下可能多组）。
 * @property modelId 平台精确模型 ID（如 `deepseek-v4-pro`）。
 */
data class ModelSelection(
    val serviceId: String,
    val groupId: String,
    val modelId: String,
)
