package github.ponyhuang.gimi.domain.voice.model

enum class SpeechPlaybackStatus { Idle, Loading, Playing, Paused }

data class SpeechPlaybackState(
    val messageId: String? = null,
    val status: SpeechPlaybackStatus = SpeechPlaybackStatus.Idle,
)

/**
 * 音色的系统分类，仅用于音色选择器的分组展示。
 *
 * 顺序即展示顺序：越靠前的分类越可能是用户想找的音色。厂商没有分类概念时保持
 * [Unspecified]，此时选择器退化成单个平铺列表。
 */
enum class VoiceCategory {
    /** 厂商未提供分类。 */
    Unspecified,

    /** 旗舰音色：能力最全、官方主推。 */
    Flagship,

    /** 同时支持多种中文方言与外语的音色。 */
    Multilingual,

    /** 精品中文音色。 */
    QualityChinese,

    /** 精品外语音色。 */
    QualityEnglish,

    /** 其他系统音色（角色音、童声、特定场景音等）。 */
    Other,

    /** 小语种音色。 */
    Minority,
}

/** 音色的性别归属，用于选择器的性别筛选。 */
enum class VoiceGender {
    Unspecified,
    Female,
    Male,

    /** 童声；与男女互斥，筛选时不并入成年男女。 */
    Child,
}

data class TtsVoice(
    val id: String,
    val name: String,
    val language: String?,
    val gender: String?,
    /**
     * 声线特质与适用场景，约定写法为「特质 · 场景」，两段内部各自用「、」分隔。
     *
     * 选择器只展示特质，场景仅参与搜索，因此这个字段同时承担了展示与检索两种用途。
     */
    val description: String? = null,
    val category: VoiceCategory = VoiceCategory.Unspecified,
    /** 非中文音色的本地名称（例如保加利亚语的 `Зора`），用于和音色名并列展示。 */
    val localName: String? = null,
)

/** `description` 中分隔「特质」与「场景」两个分段的符号。 */
private const val DESCRIPTION_SEPARATOR = "·"

/** `description` 中同一分段内部的分隔符号。 */
private const val ITEM_SEPARATOR = "、"

/** 声线特质，即 `description` 的第一段；没有描述时为空。 */
val TtsVoice.traits: List<String>
    get() = description.orEmpty()
        .substringBefore(DESCRIPTION_SEPARATOR)
        .split(ITEM_SEPARATOR)
        .filter { it.isNotBlank() }

/** 适用场景，即 `description` 的第二段；场景只用于搜索，不在列表行展示。 */
val TtsVoice.scenes: List<String>
    get() = description.orEmpty()
        .substringAfter(DESCRIPTION_SEPARATOR, "")
        .split(ITEM_SEPARATOR)
        .filter { it.isNotBlank() }

/**
 * 由厂商的性别字段推断性别归属。
 *
 * 千问音色表已归一化为「女」「男」「儿童」；其他厂商仍沿用「女声」「男声」「中性」，
 * 无法对应到具体性别时返回 [VoiceGender.Unspecified]，不参与筛选。
 */
val TtsVoice.voiceGender: VoiceGender
    get() = when (gender?.trim()) {
        "女", "女声" -> VoiceGender.Female
        "男", "男声" -> VoiceGender.Male
        "儿童" -> VoiceGender.Child
        else -> VoiceGender.Unspecified
    }

data class TtsVoiceContext(
    val serviceId: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    /**
     * 当前选中的 TTS 模型 ID。
     *
     * 厂商音色常与模型绑定（同一音色在不同模型上不可用），因此查询音色必须带上模型。
     */
    val modelId: String = "",
)

/** 语音识别上下文的角色。 */
enum class SpeechContextRole {
    /** 用户此前的语音识别结果或领域词表。 */
    User,

    /** 模型此前的回复内容。 */
    Assistant,
}

/**
 * 语音识别上下文，用于提升专有名词与产品名的识别准确率。
 *
 * @property role 上下文角色。
 * @property text 上下文文本。
 */
data class SpeechContextMessage(
    val role: SpeechContextRole,
    val text: String,
)

object MiMoTtsVoices {
    val all: List<TtsVoice> = listOf(
        TtsVoice("mimo_default", "MiMo-默认", null, null),
        TtsVoice("冰糖", "冰糖", "中文", "女声"),
        TtsVoice("茉莉", "茉莉", "中文", "女声"),
        TtsVoice("苏打", "苏打", "中文", "男声"),
        TtsVoice("白桦", "白桦", "中文", "男声"),
        TtsVoice("Mia", "Mia", "English", "女声"),
        TtsVoice("Chloe", "Chloe", "English", "女声"),
        TtsVoice("Milo", "Milo", "English", "男声"),
        TtsVoice("Dean", "Dean", "English", "男声"),
    )
}

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

/**
 * Maps a configured `LLMModelProvider.serviceId` to its built-in TTS voice catalog.
 * Centralizes the lookup so callers (UI state, future tests) don't hardcode which
 * list to read.
 */
object TtsVoiceCatalog {
    /**
     * 千问音频：内置表只是系统音色的一部分，3.0 还有五百余个基础音色没有列表接口，
     * 因此除了选择还需要允许直接填写音色 ID。
     */
    private const val QWEN_AUDIO_SERVICE_ID = "qwen-audio"

    /** 千问基础音色没有列表接口，用户需要按这个命名规则拼出 voice 参数。 */
    const val QWEN_BASE_VOICE_TEMPLATE = "qwen-audio-3.0-tts-{plus|flash}-{后缀}"

    /** 千问官方音色列表页，用于查基础音色的后缀。 */
    const val QWEN_VOICE_LIST_URL = "https://help.aliyun.com/zh/model-studio/qwen-tts-voice-list"

    fun forService(serviceId: String?): List<TtsVoice> = when (serviceId) {
        "minimax-speech" -> MinimaxTtsVoices.all
        "mimo-speech" -> MiMoTtsVoices.all
        "openai-speech" -> OpenAiTtsVoices.all
        else -> emptyList()
    }

    /**
     * 该服务是否允许填写内置表之外的音色 ID。
     *
     * 只对音色集不封闭的厂商开放：千问的基础音色与声音复刻音色数量远超内置表，
     * 硬拦会把合法音色一起拦掉；其余厂商的音色集是封闭的，开放自由输入只会制造失败配置。
     */
    fun allowsCustomVoice(serviceId: String?): Boolean = serviceId == QWEN_AUDIO_SERVICE_ID
}
