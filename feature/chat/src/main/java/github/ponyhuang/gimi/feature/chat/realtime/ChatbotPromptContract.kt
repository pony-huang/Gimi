package github.ponyhuang.gimi.feature.chat.realtime

import github.ponyhuang.gimi.domain.conversation.repository.ChatbotRolePreset

/**
 * Chatbot 角色预设管理页状态。
 * @property presets 用户自建预设，按创建顺序排列。
 * @property builtInPresets 随应用发布的官方预设，只读。
 * @property selectedPresetId 当前会话使用的角色；null 表示内置角色。
 * @property canSelect 是否绑定当前会话，允许选择角色。
 * @property editing 正在编辑的预设草稿；id 为空串表示新建，非空表示编辑对应预设。
 * @property confirmDelete 等待删除确认的预设；非空时展示确认弹窗。
 * @property viewing 正在查看只读正文的预设；非空时展示提示词弹窗。
 */
data class ChatbotPromptUiState(
    val presets: List<ChatbotRolePreset> = emptyList(),
    val builtInPresets: List<ChatbotRolePreset> = emptyList(),
    val selectedPresetId: String? = null,
    val canSelect: Boolean = false,
    val editing: ChatbotRolePreset? = null,
    val confirmDelete: ChatbotRolePreset? = null,
    val viewing: ChatbotRolePreset? = null,
)

sealed interface ChatbotPromptAction {
    /** 设置默认预设；null 表示回退内置语音指令。 */
    data class Select(val presetId: String?) : ChatbotPromptAction
    data object StartCreate : ChatbotPromptAction
    data class StartEdit(val presetId: String) : ChatbotPromptAction
    data object DismissEdit : ChatbotPromptAction
    data class Save(val name: String, val content: String, val description: String = "") : ChatbotPromptAction
    data class RequestDelete(val presetId: String) : ChatbotPromptAction
    data object ConfirmDelete : ChatbotPromptAction
    data object DismissDelete : ChatbotPromptAction

    /** 查看官方预设的完整提示词；官方预设只读，不进入编辑流程。 */
    data class ViewPrompt(val presetId: String) : ChatbotPromptAction
    /** 将查看中的角色复制到可编辑的新角色草稿。 */
    data object CopyViewedPreset : ChatbotPromptAction
    data object DismissViewPrompt : ChatbotPromptAction
}
