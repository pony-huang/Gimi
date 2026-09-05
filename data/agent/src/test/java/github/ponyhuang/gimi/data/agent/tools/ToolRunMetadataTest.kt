package github.ponyhuang.gimi.data.agent.tools

import github.ponyhuang.gimi.data.agent.ModelConfig
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.toRuntimeMetadata
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
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
            enabledMcpServerIds = setOf("github"),
            enabledOfficialFunctionIdsByService = mapOf(
                "kimi" to mapOf("kimi_formulas" to setOf("translate", "code")),
            ),
            toolAccessMode = ToolAccessMode.ON_DEMAND,
        )

        val decoded = ToolRunMetadata.toolConfiguration(
            ToolRunMetadata.of(
                modelRuntime = modelRuntime(),
                toolConfiguration = configuration,
                allowConfirmationRequiredTools = false,
            ),
        )

        assertEquals(configuration, decoded)
    }

    @Test
    fun absentConfigurationReadsBackAsNull() {
        val metadata = ToolRunMetadata.of(
            modelRuntime = modelRuntime(),
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

        val decoded = ToolRunMetadata.toolConfiguration(
            ToolRunMetadata.of(modelRuntime(), configuration, true),
        )

        assertEquals(configuration, decoded)
    }

    @Test
    fun legacyAutomaticAccessMetadataDefaultsToAlwaysAvailable() {
        val decoded = ToolRunMetadata.toolConfiguration(
            mapOf(
                "selkie.tool_config.present" to true,
                "selkie.tool_config.access_mode" to "AUTO",
            ),
        )

        assertEquals(ToolAccessMode.ALWAYS_AVAILABLE, decoded?.toolAccessMode)
    }

    @Test
    fun allowConfirmationFlagDefaultsToTrue() {
        assertTrue(ToolRunMetadata.allowConfirmationRequiredTools(null))
        assertTrue(ToolRunMetadata.allowConfirmationRequiredTools(emptyMap()))
        assertFalse(
            ToolRunMetadata.allowConfirmationRequiredTools(
                ToolRunMetadata.of(
                    modelRuntime = modelRuntime(),
                    toolConfiguration = null,
                    allowConfirmationRequiredTools = false,
                ),
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
            modelRuntime = modelRuntime(),
            toolConfiguration = ConversationToolConfiguration(
                enabledMcpServerIds = setOf("github"),
                enabledOfficialFunctionIdsByService = mapOf(
                    "kimi" to mapOf("kimi_formulas" to setOf("translate")),
                ),
                toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
            ),
            allowConfirmationRequiredTools = false,
        )

        val json = Json.parseToJsonElement(Json.encodeToString(toJsonElement(metadata))).jsonObject

        assertEquals(
            "ALWAYS_AVAILABLE",
            json.getValue("selkie.tool_config.access_mode").jsonPrimitive.content,
        )
        assertEquals(
            "github",
            json.getValue("selkie.tool_config.mcp_server_ids").jsonArray.single()
                .jsonPrimitive.content,
        )
        assertFalse(
            json.getValue("selkie.allow_confirmation_required_tools").jsonPrimitive.content
                .toBoolean(),
        )
    }

    @Test
    fun modelRuntimeRoundTripExcludesCredential() {
        val expected = ModelConfig(
            serviceId = "glm",
            baseType = ApiProtocol.Anthropic,
            modelId = "glm-4.6",
            apiKey = "secret-key",
            fullBaseUrl = "https://open.bigmodel.cn/api/anthropic",
        ).toRuntimeMetadata()

        val metadata = ToolRunMetadata.of(
            modelRuntime = expected,
            toolConfiguration = null,
            allowConfirmationRequiredTools = true,
        )

        assertEquals(expected, ToolRunMetadata.modelRuntime(metadata))
        assertFalse(metadata.keys.any { it.contains("api_key") })
        assertFalse(metadata.toString().contains("secret-key"))
    }

    private fun modelRuntime() = ModelRuntimeMetadata(
        serviceId = "glm",
        baseType = ApiProtocol.Anthropic,
        modelId = "glm-4.6",
        fullBaseUrl = "https://open.bigmodel.cn/api/anthropic",
    )

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
