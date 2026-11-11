package github.ponyhuang.gimi.feature.chat.realtime

import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset

/**
 * 简单聊天预设管理页状态。
 * @property presets 用户自建预设，按创建顺序排列。
 * @property builtInPresets 随应用发布的官方预设，只读。
 * @property selectedPresetId 当前默认使用的预设；null 表示不使用预设。
 * @property editing 正在编辑的预设草稿；id 为空串表示新建，非空表示编辑对应预设。
 * @property confirmDelete 等待删除确认的预设；非空时展示确认弹窗。
 * @property viewing 正在查看只读正文的预设；非空时展示提示词弹窗。
 */
data class SimpleChatPromptUiState(
    val presets: List<SimpleChatPromptPreset> = emptyList(),
    val builtInPresets: List<SimpleChatPromptPreset> = emptyList(),
    val selectedPresetId: String? = null,
    val editing: SimpleChatPromptPreset? = null,
    val confirmDelete: SimpleChatPromptPreset? = null,
    val viewing: SimpleChatPromptPreset? = null,
)

sealed interface SimpleChatPromptAction {
    /** 设置默认预设；null 表示回退内置语音指令。 */
    data class Select(val presetId: String?) : SimpleChatPromptAction
    data object StartCreate : SimpleChatPromptAction
    data class StartEdit(val presetId: String) : SimpleChatPromptAction
    data object DismissEdit : SimpleChatPromptAction
    data class Save(val name: String, val content: String) : SimpleChatPromptAction
    data class RequestDelete(val presetId: String) : SimpleChatPromptAction
    data object ConfirmDelete : SimpleChatPromptAction
    data object DismissDelete : SimpleChatPromptAction

    /** 查看官方预设的完整提示词；官方预设只读，不进入编辑流程。 */
    data class ViewPrompt(val presetId: String) : SimpleChatPromptAction
    data object DismissViewPrompt : SimpleChatPromptAction
}
