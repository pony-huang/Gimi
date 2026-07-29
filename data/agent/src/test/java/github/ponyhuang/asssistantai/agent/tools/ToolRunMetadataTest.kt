package github.ponyhuang.asssistantai.agent.tools

import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRunMetadataTest {

    @Test
    fun roundTripPreservesTheFullConfiguration() {
        val configuration = ConversationToolConfiguration(
            enabledLocalToolIds = setOf("clock", "location"),
            enabledMcpServerIds = setOf("github"),
            enabledOfficialFunctionIdsByService = mapOf(
                "kimi" to mapOf("kimi_formulas" to setOf("translate", "code")),
            ),
            toolAccessMode = ToolAccessMode.ON_DEMAND,
        )

        val decoded = ToolRunMetadata.toolConfiguration(
            ToolRunMetadata.of(configuration, allowConfirmationRequiredTools = false),
        )

        assertEquals(configuration, decoded)
    }

    @Test
    fun absentConfigurationReadsBackAsNull() {
        val metadata = ToolRunMetadata.of(
            toolConfiguration = null,
            allowConfirmationRequiredTools = true,
        )

        assertNull(ToolRunMetadata.toolConfiguration(metadata))
        assertTrue(ToolRunMetadata.allowConfirmationRequiredTools(metadata))
        assertNull(null.toolConfigurationOrNull())
    }

    @Test
    fun emptySelectionStaysEmptyRatherThanAbsent() {
        val configuration = ConversationToolConfiguration()

        val decoded = ToolRunMetadata.toolConfiguration(ToolRunMetadata.of(configuration, true))

        assertEquals(configuration, decoded)
    }

    @Test
    fun allowConfirmationFlagDefaultsToTrue() {
        assertTrue(ToolRunMetadata.allowConfirmationRequiredTools(null))
        assertTrue(ToolRunMetadata.allowConfirmationRequiredTools(emptyMap()))
        assertFalse(
            ToolRunMetadata.allowConfirmationRequiredTools(
                ToolRunMetadata.of(null, allowConfirmationRequiredTools = false),
            ),
        )
    }

    /**
     * ADK 会把 customMetadata 合并进每个持久化 Event，Room 侧 AnySerializer 只接受
     * JSON-native 值 —— 这里用 kotlinx Json 做结构等价校验，防止放入不可序列化类型。
     */
    @Test
    fun metadataIsJsonNative() {
        val metadata = ToolRunMetadata.of(
            ConversationToolConfiguration(
                enabledLocalToolIds = setOf("clock"),
                enabledMcpServerIds = setOf("github"),
                enabledOfficialFunctionIdsByService = mapOf(
                    "kimi" to mapOf("kimi_formulas" to setOf("translate")),
                ),
                toolAccessMode = ToolAccessMode.AUTO,
            ),
            allowConfirmationRequiredTools = false,
        )

        val json = Json.parseToJsonElement(Json.encodeToString(toJsonElement(metadata))).jsonObject

        assertEquals("AUTO", json.getValue("selkie.tool_config.access_mode").jsonPrimitive.content)
        assertEquals(
            "clock",
            json.getValue("selkie.tool_config.local_tool_ids").jsonArray.single()
                .jsonPrimitive.content,
        )
        assertFalse(
            json.getValue("selkie.allow_confirmation_required_tools").jsonPrimitive.content
                .toBoolean(),
        )
    }

    private fun toJsonElement(value: Any?): kotlinx.serialization.json.JsonElement =
        when (value) {
            null -> kotlinx.serialization.json.JsonNull
            is String -> kotlinx.serialization.json.JsonPrimitive(value)
            is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
            is Number -> kotlinx.serialization.json.JsonPrimitive(value)
            is Map<*, *> -> kotlinx.serialization.json.JsonObject(
                value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) },
            )
            is List<*> -> kotlinx.serialization.json.JsonArray(value.map(::toJsonElement))
            else -> error("Non JSON-native value: ${value::class.simpleName}")
        }
}
