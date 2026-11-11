package github.ponyhuang.gimi.feature.chat

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.voice.repository.VoiceFailureKind

/**
 * 语音失败分类到文案资源的映射。
 *
 * 合成与识别共用同一套分类文案：用户要解决的是同一类问题（缺配置 / 密钥错 / 断网 /
 * 服务报错），不必区分是哪个入口触发的。只有"未分类"这一档按入口给不同措辞，
 * 因为调用方比通用映射更清楚自己这次失败发生在什么动作上。
 *
 * @param kind 失败分类。
 * @param unknownRes 未分类失败在该入口使用的文案。
 */
@StringRes
internal fun voiceFailureMessageRes(kind: VoiceFailureKind, unknownRes: Int): Int = when (kind) {
    VoiceFailureKind.NotConfigured -> R.string.chat_voice_failure_not_configured
    VoiceFailureKind.CapabilityNotSupported -> R.string.chat_voice_failure_capability_not_supported
    VoiceFailureKind.Authentication -> R.string.chat_voice_failure_authentication
    VoiceFailureKind.Network -> R.string.chat_voice_failure_network
    VoiceFailureKind.Service -> R.string.chat_voice_failure_service
    VoiceFailureKind.Unknown -> unknownRes
}
