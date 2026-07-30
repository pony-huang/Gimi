package github.ponyhuang.gimi.data.conversation.local

import com.google.gson.Gson
import com.google.gson.JsonParser
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode

internal object ConversationToolConfigurationCodec {
    private val gson = Gson()

    fun encode(configuration: ConversationToolConfiguration): String =
        gson.toJson(configuration)

    fun decode(payload: String?): ConversationToolConfiguration? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val json = JsonParser.parseString(payload).asJsonObject
            val storedMode = json.get("toolAccessMode")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
            val supportedModes = setOf(
                ToolAccessMode.ON_DEMAND.name,
                ToolAccessMode.ALWAYS_AVAILABLE.name,
            )
            if (storedMode !in supportedModes) {
                // AUTO 已从当前产品语义移除；旧值和未知值统一收敛到明确的默认策略。
                json.addProperty("toolAccessMode", ToolAccessMode.ALWAYS_AVAILABLE.name)
            }
            gson.fromJson(json, ConversationToolConfiguration::class.java)
        }.getOrNull()
    }
}
