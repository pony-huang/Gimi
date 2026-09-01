package github.ponyhuang.gimi.voice

import github.ponyhuang.gimi.data.voicewake.VoicePipelineEvent

/** 将前台服务通知固定为当前音频设备，避免监听过程中的状态文案持续跳动。 */
internal object VoiceNotificationText {
    fun forEvent(event: VoicePipelineEvent): String =
        event.deviceName?.takeIf(String::isNotBlank) ?: event.message
}
