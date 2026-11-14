package github.ponyhuang.gimi.data.modelaccess.minimax.speech

import github.ponyhuang.gimi.domain.voice.model.TtsVoice

object MinimaxTtsVoices {
    val all: List<TtsVoice> = listOf(
        TtsVoice("male-qn-qingse", "青涩男声", "中文", "男声"),
        TtsVoice("audiobook_male_1", "有声书男声 1", "中文", "男声"),
        TtsVoice("Chinese (Mandarin)_Lyrical_Voice", "中文抒情", "中文", "女声"),
        TtsVoice("English_Graceful_Lady", "Graceful Lady (EN)", "English", "女声"),
        TtsVoice("English_Persuasive_Man", "Persuasive Man (EN)", "English", "男声"),
        TtsVoice("English_Lucky_Robot", "Lucky Robot (EN)", "English", "中性"),
        TtsVoice("Japanese_Whisper_Belle", "Whisper Belle (JP)", "日本語", "女声"),
        TtsVoice("Cantonese_GentleLady", "粤语 GentleLady", "粤语", "女声"),
    )
}
