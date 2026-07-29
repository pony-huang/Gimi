package github.ponyhuang.asssistantai.data.conversation.local

import com.google.gson.Gson
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration

internal object ConversationToolConfigurationCodec {
    private val gson = Gson()

    fun encode(configuration: ConversationToolConfiguration): String =
        gson.toJson(configuration)

    fun decode(payload: String?): ConversationToolConfiguration? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            gson.fromJson(payload, ConversationToolConfiguration::class.java)
        }.getOrNull()
    }
}
