package github.ponyhuang.gimi.data.modelaccess.qwen.realtime
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.VoiceCategory
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.QwenAudioTtsVoices

/**
 * 千问音频 Realtime 的系统会话音色。
 *
 * 官方按代际给了两套互不相通的音色集（跨代际使用会被服务端拒绝 `InvalidParameter`）：
 * - 3.1：`longanqian_v3.1` 加上 3.1 TTS plus / flash 音色列表中的全部音色。plus 的四个音色
 *   本身就是 flash 的子集，因此直接取 [QwenAudioTtsVoices.flash31] 即可覆盖两者。
 * - 3.0：固定五个音色，且与 3.0 TTS 的音色表并不重合（多出默认音色 `longanqian`，
 *   少掉 3.0 TTS flash 独有的角色音），所以单独建表。
 *
 * 数据来源：阿里云百炼《Qwen-Audio 实时语音对话客户端事件》的 `session.voice` 说明。
 * 其中 `longanqian` 官方没有给出中文显示名，这里按 `longan*` 系列的命名规则写作「龙安千」。
 */
object QwenRealtimeVoices {
    private const val V30_MODEL_PREFIX = "qwen-audio-3.0-"
    private const val V31_MODEL_PREFIX = "qwen-audio-3.1-"

    /** 3.1 代际的缺省会话音色。 */
    const val DEFAULT_VOICE_31 = "longanqian_v3.1"

    /** 3.0 代际的缺省会话音色。 */
    const val DEFAULT_VOICE_30 = "longanqian"

    private const val REALTIME_LANGUAGE = "中文（普通话）、英文"
    private const val DEFAULT_VOICE_DESCRIPTION = "默认音色 · 实时语音对话"

    /** Realtime 专属的默认音色；不在任何 TTS 音色表里。 */
    private val defaultVoice31 = TtsVoice(
        id = DEFAULT_VOICE_31,
        name = "龙安千",
        language = REALTIME_LANGUAGE,
        gender = "女",
        description = DEFAULT_VOICE_DESCRIPTION,
        category = VoiceCategory.Flagship,
    )

    /** 3.1 代际：默认音色 + 3.1 TTS 全部系统音色。 */
    val v31: List<TtsVoice> = listOf(defaultVoice31) + QwenAudioTtsVoices.flash31

    /** 3.0 代际：固定的五个音色，顺序与官方列表一致。 */
    val v30: List<TtsVoice> = listOf(
        TtsVoice(DEFAULT_VOICE_30, "龙安千", REALTIME_LANGUAGE, "女", DEFAULT_VOICE_DESCRIPTION, VoiceCategory.Flagship),
        TtsVoice("longanlingxin", "龙安灵心", REALTIME_LANGUAGE, "女", "知心温暖音 · 社交陪伴", VoiceCategory.Flagship),
        TtsVoice("longanlingxi", "龙安灵希", REALTIME_LANGUAGE, "女", "可爱甜美音 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longanxiaoxin", "龙安小昕", REALTIME_LANGUAGE, "女", "亲切活泼音 · 社交陪伴", VoiceCategory.QualityChinese),
        TtsVoice("longanlufeng", "龙安鲁风", REALTIME_LANGUAGE, "男", "明亮开朗音 · 社交陪伴", VoiceCategory.Flagship),
    )

    /**
     * 指定模型可选的系统音色；未收录的模型没有可选音色。
     *
     * 用户自行填写的模型 ID 不按前缀猜测代际——猜错会把另一代的音色列表摆出来，
     * 用户选中后服务端直接拒绝。
     */
    fun forModel(modelId: String): List<TtsVoice> = when {
        modelId.startsWith(V30_MODEL_PREFIX) -> v30
        modelId.startsWith(V31_MODEL_PREFIX) -> v31
        else -> emptyList()
    }

    /**
     * 指定模型的缺省音色。
     *
     * 与 [forModel] 不同，这里对未知模型回落到 3.1 的默认音色：缺省值只是「没选过音色时
     * 发什么」，而 3.1 是当前主推代际，比发一个空的 voice 字段或旧代际音色更安全。
     */
    fun defaultFor(modelId: String): String =
        if (modelId.startsWith(V30_MODEL_PREFIX)) DEFAULT_VOICE_30 else DEFAULT_VOICE_31
}
