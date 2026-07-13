package github.ponyhuang.asssistantai.data

import com.google.gson.Gson

/** Keeps [Conversation.model] storage-compatible while retaining all model selection keys. */
object ModelSelectionCodec {
    private val gson = Gson()

    fun encode(selection: ModelSelection): String = gson.toJson(selection)

    fun decode(value: String): ModelSelection? =
        value.takeIf(String::isNotBlank)?.let { encoded ->
            runCatching { gson.fromJson(encoded, ModelSelection::class.java) }.getOrNull()
        }
}
