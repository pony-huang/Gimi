package github.ponyhuang.gimi.data.agent

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentPromptsTest {

    @Test
    fun defaultInstructionDoesNotContainDynamicToolSearchInstructions() {
        val instruction = AgentPrompts.defaultAssistantInstruction()

        assertFalse(instruction.contains("next model step"))
        assertFalse(instruction.contains("<tool_search>"))
    }
    @Test
    fun voiceFormatReplacesWrittenFormatAndKeepsAssistantResponsibilities() {
        val text = AgentPrompts.defaultAssistantInstruction()
        val voice = AgentPrompts.defaultAssistantInstruction(voiceResponse = true)

        assertTrue(text.contains("<response_format>"))
        assertFalse(text.contains("<voice_response>"))
        assertFalse(voice.contains("<response_format>"))
        assertTrue(voice.contains("<voice_response>"))
        assertTrue(voice.contains("<tool_use>"))
        assertTrue(voice.contains("<accuracy>"))
        assertTrue(voice.contains("<safety>"))
    }
}
