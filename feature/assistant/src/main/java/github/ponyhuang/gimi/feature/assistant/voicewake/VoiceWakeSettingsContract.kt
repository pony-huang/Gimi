package github.ponyhuang.gimi.feature.assistant.voicewake

import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.WakeKeywordError

/**
 * 语音唤醒设置页的完整展示状态。
 *
 * @property voiceState 语音唤醒服务与模型的实时状态。
 * @property configurationReady 默认对话模型和语音识别模型是否可用。
 * @property isStartPending 是否正在等待模型安装完成后继续启动监听。
 * @property permissionRequestId 待 Route 消费的一次性权限请求标识。
 * @property keywordDraft 当前模型的唤醒词草稿。
 * @property keywordError 草稿的语言规则错误。
 * @property keywordSaveFailed 上次持久化或应用是否失败，可在保留草稿的前提下重试。
 * @property hasUnsavedKeyword 当前模型是否有未保存修改。
 * @property showUnsavedChangesDialog 返回页面前是否需要确认未保存修改。
 * @property isApplyingKeyword 运行中的监听器是否正在应用新唤醒词。
 */
data class VoiceWakeSettingsUiState(
    val voiceState: VoiceWakeState = VoiceWakeState(),
    val configurationReady: Boolean = false,
    val isStartPending: Boolean = false,
    val permissionRequestId: Int? = null,
    val keywordDraft: String = VoiceWakeState().wakeWord,
    val keywordError: WakeKeywordError? = null,
    val keywordSaveFailed: Boolean = false,
    val hasUnsavedKeyword: Boolean = false,
    val showUnsavedChangesDialog: Boolean = false,
    val isApplyingKeyword: Boolean = false,
)

sealed interface VoiceWakeSettingsAction {
    /** 切换后台监听开关。 */
    data class ToggleListening(val enabled: Boolean) : VoiceWakeSettingsAction

    /** 更新当前模型的本地草稿。 */
    data class KeywordChanged(val value: String) : VoiceWakeSettingsAction

    /** 将一个推荐词填入草稿。 */
    data class SuggestedKeywordSelected(val value: String) : VoiceWakeSettingsAction

    /** 将当前模型默认词填入草稿，不立即保存。 */
    data object UseDefaultKeyword : VoiceWakeSettingsAction

    /** 保存当前模型的唤醒词。 */
    data object SaveKeyword : VoiceWakeSettingsAction

    /** 由顶部返回按钮或系统返回手势触发。 */
    data object RequestBack : VoiceWakeSettingsAction

    /** 继续留在页面编辑。 */
    data object DismissUnsavedChanges : VoiceWakeSettingsAction

    /** 放弃本次页面会话的全部草稿并返回。 */
    data object DiscardChangesAndLeave : VoiceWakeSettingsAction

    /** 保存所有模型的有效草稿后返回。 */
    data object SaveChangesAndLeave : VoiceWakeSettingsAction

    /** 选择并准备指定语言的唤醒模型。 */
    data class SelectModel(val modelId: String) : VoiceWakeSettingsAction

    /** 安装或重试指定唤醒模型。 */
    data class InstallModel(val modelId: String) : VoiceWakeSettingsAction

    /** 取消指定唤醒模型的下载或解包。 */
    data class CancelInstall(val modelId: String) : VoiceWakeSettingsAction

    /** 移除指定唤醒模型的本地文件。 */
    data class RemoveModel(val modelId: String) : VoiceWakeSettingsAction

    /** 提交系统权限请求结果。 */
    data class PermissionsResult(val granted: Boolean) : VoiceWakeSettingsAction

    /** 标记一次权限请求已由 Route 消费。 */
    data class PermissionRequestHandled(val requestId: Int) : VoiceWakeSettingsAction
}

sealed interface VoiceWakeSettingsEffect {
    /** 唤醒词已持久化，用于展示 Snackbar。 */
    data class KeywordSaved(val wakeWord: String) : VoiceWakeSettingsEffect

    /** 所有返回前置逻辑已完成。 */
    data object NavigateBack : VoiceWakeSettingsEffect
}
