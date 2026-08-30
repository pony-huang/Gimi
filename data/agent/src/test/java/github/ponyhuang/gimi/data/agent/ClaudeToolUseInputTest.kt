package github.ponyhuang.gimi.data.agent

import com.anthropic.core.JsonValue
import com.fasterxml.jackson.core.type.TypeReference
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.PartialArg
import com.google.adk.kt.types.PartialArgValue
import github.ponyhuang.gimi.data.agent.model.toAnthropicToolUseInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaudeToolUseInputTest {

    @Test
    fun convertsCompletedArgsToAnAnthropicInputObject() {
        val input = FunctionCall(
            name = "search",
            args = mapOf(
                "query" to "Claude",
                "limit" to 3,
                "exact" to true,
                "filters" to mapOf("language" to "zh", "archived" to false),
                "tags" to listOf("sdk", "tools"),
                "cursor" to null,
            ),
        ).toAnthropicToolUseInput()

        val properties = input._additionalProperties()
        assertEquals("Claude", properties.getValue("query").asString().get())
        assertEquals(3, properties.getValue("limit").asNumber().get().toInt())
        assertEquals(true, properties.getValue("exact").asBoolean().get())
        assertEquals(
            mapOf("language" to "zh", "archived" to false),
            properties.getValue("filters").toMap(),
        )
        assertEquals(listOf("sdk", "tools"), properties.getValue("tags").toList())
        assertNull(properties.getValue("cursor").convert(Any::class.java))
    }

    @Test
    fun createsEmptyObjectForParameterlessFunction() {
        val input = FunctionCall(name = "ping").toAnthropicToolUseInput()

        assertEquals(emptyMap<String, JsonValue>(), input._additionalProperties())
    }

    @Test
    fun ignoresPartialArgsWhenCompletedArgsAreAvailable() {
        val input = FunctionCall(
            name = "search",
            args = mapOf("query" to "complete value"),
            partialArgs = listOf(
                PartialArg(
                    value = PartialArgValue.StringValue("incomplete value"),
                    jsonPath = "$.query",
                    willContinue = true,
                ),
            ),
        ).toAnthropicToolUseInput()

        assertEquals("complete value", input._additionalProperties().getValue("query").asString().get())
    }

    private fun JsonValue.toMap(): Map<String, Any?> = requireNotNull(
        convert(object : TypeReference<Map<String, Any?>>() {}),
    )

    private fun JsonValue.toList(): List<Any?> = requireNotNull(
        convert(object : TypeReference<List<Any?>>() {}),
    )
}
