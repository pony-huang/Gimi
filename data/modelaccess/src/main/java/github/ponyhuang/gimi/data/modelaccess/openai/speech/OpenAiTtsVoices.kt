package github.ponyhuang.gimi.data.modelaccess.openai.speech

import github.ponyhuang.gimi.domain.voice.model.TtsVoice

/**
 * OpenAI 语音合成内置音色表。
 *
 * 官方没有可拉取的音色列表接口（自定义音色需上传样本走 Voice Consents，不属于本链路），
 * 因此只有本地静态表。官方音色均为多语种，`language` 留空表示不限语种。
 */
object OpenAiTtsVoices {
    val all: List<TtsVoice> = listOf(
        TtsVoice("alloy", "Alloy", null, "中性"),
        TtsVoice("ash", "Ash", null, "男声"),
        TtsVoice("ballad", "Ballad", null, "男声"),
        TtsVoice("coral", "Coral", null, "女声"),
        TtsVoice("echo", "Echo", null, "男声"),
        TtsVoice("fable", "Fable", null, "男声", "英式口音"),
        TtsVoice("onyx", "Onyx", null, "男声"),
        TtsVoice("nova", "Nova", null, "女声"),
        TtsVoice("sage", "Sage", null, "女声"),
        TtsVoice("shimmer", "Shimmer", null, "女声"),
        TtsVoice("verse", "Verse", null, "男声"),
        TtsVoice("marin", "Marin", null, "女声"),
        TtsVoice("cedar", "Cedar", null, "男声"),
    )
}
