package github.ponyhuang.gimi.data.modelaccess.deepseek.speech

import github.ponyhuang.gimi.domain.voice.model.TtsVoice

/** DeepSeek 语音合成内置音色；官方仅提供男女两款，stella 为默认音色。 */
object DeepSeekTtsVoices {
    val all: List<TtsVoice> = listOf(
        TtsVoice("stella", "海星", "多语种", "女声", "默认音色 · 通用"),
        TtsVoice("tide", "暗潮", "多语种", "男声", null),
    )
}
