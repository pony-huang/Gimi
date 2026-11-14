package github.ponyhuang.gimi.data.modelaccess.openai.realtime

import github.ponyhuang.gimi.domain.voice.model.TtsVoice

/**
 * OpenAI Realtime 会话音色表（服务端 `session.voice` 配置，非级联 TTS 音色）。
 *
 * 内置音色清单以 GA 参考为准：alloy/ash/ballad/coral/echo/sage/shimmer/verse/marin/cedar，
 * 不含 fable/nova/onyx；默认 [DEFAULT_VOICE] 取官方推荐的最佳质量档 cedar。
 */
object OpenAiRealtimeVoices {
    const val DEFAULT_VOICE = "cedar"

    val all: List<TtsVoice> = listOf(
        TtsVoice("alloy", "Alloy", null, "中性"),
        TtsVoice("ash", "Ash", null, "男声"),
        TtsVoice("ballad", "Ballad", null, "男声"),
        TtsVoice("coral", "Coral", null, "女声"),
        TtsVoice("echo", "Echo", null, "男声"),
        TtsVoice("sage", "Sage", null, "女声"),
        TtsVoice("shimmer", "Shimmer", null, "女声"),
        TtsVoice("verse", "Verse", null, "男声"),
        TtsVoice("marin", "Marin", null, "女声"),
        TtsVoice("cedar", "Cedar", null, "男声"),
    )
}
