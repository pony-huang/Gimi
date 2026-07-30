package github.ponyhuang.gimi.agent

import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.agent.model.normalizeForAnthropic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ClaudeMessageHistoryTest {

    @Test
    fun mergesAdjacentAssistantFragmentsBeforeToolResult() {
        val call = FunctionCall(id = "call-1", name = "clock", args = emptyMap())
        val result = FunctionResponse(id = "call-1", name = "clock", response = mapOf("time" to "11:04"))
        val history = listOf(
            Content(role = Role.USER, parts = listOf(Part(text = "What time is it?"))),
            Content(role = Role.MODEL, parts = listOf(Part(text = "I'll check."))),
            Content(role = Role.MODEL, parts = listOf(Part(functionCall = call))),
            Content(role = Role.USER, parts = listOf(Part(functionResponse = result))),
        )

        val normalized = history.normalizeForAnthropic()

        assertEquals(listOf(Role.USER, Role.MODEL, Role.USER), normalized.map { it.role })
        assertEquals(2, normalized[1].parts.size)
        assertSame(call, normalized[1].parts[1].functionCall)
        assertSame(result, normalized[2].parts.single().functionResponse)
    }

    @Test
    fun putsToolResultsBeforeTextInUserMessage() {
        val result = FunctionResponse(id = "call-1", name = "clock", response = mapOf("time" to "11:04"))
        val history = listOf(
            Content(
                role = Role.USER,
                parts = listOf(
                    Part(text = "Please continue."),
                    Part(functionResponse = result),
                ),
            ),
        )

        val normalized = history.normalizeForAnthropic()

        assertSame(result, normalized.single().parts[0].functionResponse)
        assertEquals("Please continue.", normalized.single().parts[1].text)
    }

    @Test
    fun mergesAdjacentUserFragmentsAndKeepsToolResultsFirst() {
        val result = FunctionResponse(id = "call-1", name = "clock", response = mapOf("time" to "11:04"))
        val history = listOf(
            Content(role = Role.USER, parts = listOf(Part(text = "Please continue."))),
            Content(role = Role.USER, parts = listOf(Part(functionResponse = result))),
        )

        val normalized = history.normalizeForAnthropic()

        assertEquals(1, normalized.size)
        assertSame(result, normalized.single().parts[0].functionResponse)
        assertEquals("Please continue.", normalized.single().parts[1].text)
    }
}
