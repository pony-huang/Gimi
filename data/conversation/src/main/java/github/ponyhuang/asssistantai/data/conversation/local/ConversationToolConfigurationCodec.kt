package github.ponyhuang.asssistantai.data.conversation.local

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration

internal object ConversationToolConfigurationCodec {
    private val gson = Gson()

    fun encode(configuration: ConversationToolConfiguration): String =
        gson.toJson(configuration)

    fun decode(payload: String?): ConversationToolConfiguration? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val raw = gson.fromJson(payload, JsonObject::class.java) ?: return@runCatching null
            val migrated = migrateLegacyOfficialTools(raw)
            gson.fromJson(migrated, ConversationToolConfiguration::class.java)
        }.getOrNull()
    }

    /**
     * Older payloads used `enabledOfficialToolIdsByService: { serviceId: [toolId] }`
     * meaning "tool enabled at conversation level". The new schema tracks per
     * function id; convert each old tool id into a function set containing the
     * [ConversationToolConfiguration.ALL_FUNCTIONS_MARKER] sentinel so the UI can
     * lazily expand it once it knows the actual function ids.
     */
    private fun migrateLegacyOfficialTools(raw: JsonObject): JsonObject {
        if (!raw.has("enabledOfficialToolIdsByService")) return raw
        val legacy = raw.getAsJsonObject("enabledOfficialToolIdsByService") ?: return raw
        val migrated = JsonObject()
        for ((serviceId, element) in legacy.entrySet()) {
            migrated.add(serviceId, legacyToolIdsToFunctionMap(element))
        }
        raw.remove("enabledOfficialToolIdsByService")
        raw.add("enabledOfficialFunctionIdsByService", migrated)
        return raw
    }

    private fun legacyToolIdsToFunctionMap(element: com.google.gson.JsonElement): JsonObject {
        val map = JsonObject()
        if (!element.isJsonArray) return map
        val tools = element.asJsonArray
        for (toolEntry in tools) {
            if (toolEntry !is JsonPrimitive || !toolEntry.isString) continue
            map.add(toolEntry.asString, allFunctionsPlaceholder())
        }
        return map
    }

    private fun allFunctionsPlaceholder(): JsonArray =
        JsonArray().apply { add(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER) }
}