package github.ponyhuang.gimi.data.conversation.local

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import kotlinx.serialization.json.Json

internal object ConversationToolConfigurationCodec {
    /**
     * 兼容 Gson 旧持久化格式：字段名 = Kotlin 属性名、枚举按 name、null 字段省略、
     * 默认值全量写出。kotlinx 默认会省略等于默认值的字段并把 null 写成字面量，这里显式对齐旧格式。
     */
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun encode(configuration: ConversationToolConfiguration): String =
        json.encodeToString(configuration)

    fun decode(payload: String?): ConversationToolConfiguration? {
        if (payload.isNullOrBlank()) return null
        // ToolAccessMode 的 "AUTO"/未知历史值由 ToolAccessModeSerializer 收敛到 ALWAYS_AVAILABLE；
        // 真正 malformed 的 JSON 在此被兜底为未初始化。
        return runCatching {
            json.decodeFromString<ConversationToolConfiguration>(payload)
        }.getOrNull()
    }
}
