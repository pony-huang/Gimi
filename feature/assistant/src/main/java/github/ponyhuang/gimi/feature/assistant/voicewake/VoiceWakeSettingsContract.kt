package github.ponyhuang.gimi.feature.assistant.voicewake

import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.WakePhraseError

/**
 * 语音唤醒设置页的完整展示状态。
 *
 * @property voiceState 系统识别能力、运行阶段和已保存短语。
 * @property configurationReady 默认对话模型是否可用。
 * @property permissionRequestId 待 Route 消费的一次性录音权限请求标识。
 * @property phraseDraft 尚未加入列表的短语草稿。
 * @property phraseError 草稿或列表操作的类型化错误。
 */
data class VoiceWakeSettingsUiState(
    val voiceState: VoiceWakeState = VoiceWakeState(),
    val configurationReady: Boolean = false,
    val permissionRequestId: Int? = null,
    val phraseDraft: String = "",
    val phraseError: WakePhraseError? = null,
)

sealed interface VoiceWakeSettingsAction {
    /** 切换应用可见期间的系统语音识别。 */
    data class ToggleListening(val enabled: Boolean) : VoiceWakeSettingsAction

    /** 更新待添加的短语草稿。 */
    data class PhraseChanged(val value: String) : VoiceWakeSettingsAction

    /** 校验并加入当前草稿。 */
    data object AddPhrase : VoiceWakeSettingsAction

    /** 移除一个已保存短语。 */
    data class RemovePhrase(val phrase: String) : VoiceWakeSettingsAction

    /** 提交录音权限结果。 */
    data class PermissionResult(val granted: Boolean) : VoiceWakeSettingsAction

    /** 标记一次权限请求已由 Route 消费。 */
    data class PermissionRequestHandled(val requestId: Int) : VoiceWakeSettingsAction
}
