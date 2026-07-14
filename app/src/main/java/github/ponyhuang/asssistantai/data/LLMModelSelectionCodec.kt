package github.ponyhuang.asssistantai.data

import com.google.gson.Gson

/** Keeps [Conversation.model] storage-compatible while retaining all model selection keys. */
object LLMModelSelectionCodec {
    private val gson = Gson()

    fun encode(selection: LLMModelSelection): String = gson.toJson(selection)

    fun decode(value: String): LLMModelSelection? =
        value.takeIf(String::isNotBlank)?.let { encoded ->
            runCatching { gson.fromJson(encoded, LLMModelSelection::class.java) }.getOrNull()
        }
}
