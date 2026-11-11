package github.ponyhuang.gimi.data.agent

import org.junit.Assert.assertFalse
import org.junit.Test

class AgentPromptsTest {

    @Test
    fun defaultInstructionDoesNotContainDynamicToolSearchInstructions() {
        val instruction = AgentPrompts.defaultAssistantInstruction()

        assertFalse(instruction.contains("next model step"))
        assertFalse(instruction.contains("<tool_search>"))
    }
}
