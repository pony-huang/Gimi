package github.ponyhuang.asssistantai.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptsTest {

    @Test
    fun dynamicModeExplainsHowToSearchBeforeCallingUnavailableTools() {
        val instruction = AgentPrompts.defaultAssistantInstruction(
            dynamicToolSearchEnabled = true,
        )

        assertTrue(instruction.contains("tool_search"))
        assertTrue(instruction.contains("next model step"))
        assertTrue(instruction.contains("English capability keywords"))
    }

    @Test
    fun alwaysAvailableModeDoesNotAddDynamicSearchInstructions() {
        val instruction = AgentPrompts.defaultAssistantInstruction(
            dynamicToolSearchEnabled = false,
        )

        assertFalse(instruction.contains("next model step"))
    }

    @Test
    fun instructionCoversRequestsWithoutAnyDeclaredTools() {
        val instruction = AgentPrompts.defaultAssistantInstruction()

        assertTrue(instruction.contains("If no tools are declared"))
        assertTrue(instruction.contains("do not imitate a tool call"))
    }
}
