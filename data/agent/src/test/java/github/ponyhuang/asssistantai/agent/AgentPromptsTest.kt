package github.ponyhuang.asssistantai.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptsTest {

    @Test
    fun dynamicModeExplainsHowToSearchBeforeCallingUnavailableTools() {
        val instruction = AgentPrompts.defaultAssistantInstruction(
            toolNames = setOf("tool_search"),
            dynamicToolSearchEnabled = true,
        )

        assertTrue(instruction.contains("tool_search"))
        assertTrue(instruction.contains("next model step"))
        assertTrue(instruction.contains("English capability keywords"))
    }

    @Test
    fun alwaysAvailableModeDoesNotAddDynamicSearchInstructions() {
        val instruction = AgentPrompts.defaultAssistantInstruction(
            toolNames = setOf("clock"),
            dynamicToolSearchEnabled = false,
        )

        assertFalse(instruction.contains("next model step"))
    }
}
