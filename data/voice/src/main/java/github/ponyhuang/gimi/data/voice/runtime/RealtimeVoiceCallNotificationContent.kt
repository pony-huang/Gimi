package github.ponyhuang.gimi.data.voice.runtime

import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase

/** 常驻通知的文案分支；由领域阶段映射而来，Service 负责取对应语言的字符串资源。 */
enum class VoiceCallNotificationText {
    Idle,
    Listening,
    Transcribing,
    Thinking,
    Speaking,
    Muted,
    Failed,
    Ending,
}

/**
 * 常驻通知的稳定快照：只保留影响通知外观的字段，电平、字幕等高频变化不会触发通知刷新。
 * @property active 是否仍有通话；与 [ending] 同时为 false 时服务退出并移除通知。
 * @property ending 结束清理进行中，此时隐藏所有操作按钮。
 * @property backgrounded 应用是否处于后台，决定后台后缀与切后台那一次轻提醒。
 * @property muted 当前是否静音，决定快捷操作是「静音」还是「取消静音」。
 * @property conversationTitle 当前通话绑定的会话名，为空时使用默认标题。
 * @property text 当前应显示的状态文案分支。
 */
data class VoiceCallNotificationContent(
    val active: Boolean,
    val ending: Boolean,
    val backgrounded: Boolean,
    val muted: Boolean,
    val conversationTitle: String,
    val text: VoiceCallNotificationText,
)

/** 把进程级通话状态压缩成通知快照，过滤掉只影响语音页面的高频状态变化。 */
fun RealtimeVoiceCallState.toNotificationContent(backgrounded: Boolean): VoiceCallNotificationContent =
    VoiceCallNotificationContent(
        active = active,
        ending = ending,
        backgrounded = backgrounded,
        muted = voice.muted,
        conversationTitle = conversationTitle,
        text = notificationText(),
    )

private fun RealtimeVoiceCallState.notificationText(): VoiceCallNotificationText = when {
    ending -> VoiceCallNotificationText.Ending
    voice.failure != null || voice.phase == RealtimeVoicePhase.Failed -> VoiceCallNotificationText.Failed
    voice.muted -> VoiceCallNotificationText.Muted
    else -> when (voice.phase) {
        RealtimeVoicePhase.Listening -> VoiceCallNotificationText.Listening
        RealtimeVoicePhase.Transcribing -> VoiceCallNotificationText.Transcribing
        RealtimeVoicePhase.Thinking -> VoiceCallNotificationText.Thinking
        RealtimeVoicePhase.Speaking -> VoiceCallNotificationText.Speaking
        // 显式列出全部枚举分支：新增枚举值时编译期即可暴露遗漏，而不是落回「点击返回通话」。
        RealtimeVoicePhase.Failed -> VoiceCallNotificationText.Failed
        RealtimeVoicePhase.Closed,
        RealtimeVoicePhase.Idle,
        -> VoiceCallNotificationText.Idle
    }
}

/**
 * 只在「通话进行中、应用第一次进入后台」时允许一次轻提醒。
 * 返回前台、后台期间的文案刷新与结束清理都不再打扰用户。
 */
fun shouldAlertOnBackground(
    previous: VoiceCallNotificationContent?,
    current: VoiceCallNotificationContent,
): Boolean = previous != null &&
    !previous.backgrounded &&
    current.backgrounded &&
    current.active &&
    !current.ending
