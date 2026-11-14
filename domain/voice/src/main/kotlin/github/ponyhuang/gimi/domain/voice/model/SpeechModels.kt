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
