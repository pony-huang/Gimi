package github.ponyhuang.gimi.data.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptsTest {

    @Test
    fun defaultInstructionSeparatesResponsibilitiesWithXmlSections() {
        val instruction = AgentPrompts.defaultAssistantInstruction()

        assertTrue(instruction.startsWith("<role>"))
        assertTrue(instruction.contains("<core_instructions>"))
        assertTrue(instruction.contains("<tool_use>"))
        assertTrue(instruction.contains("<safety>"))
        assertTrue(instruction.contains("<accuracy>"))
        assertTrue(instruction.contains("<response_format>"))
    }

    @Test
    fun defaultInstructionFormatsUriSchemesAndDeepLinksAsClickableMarkdownLinks() {
        val instruction = AgentPrompts.defaultAssistantInstruction()

        assertTrue(instruction.contains("URI scheme or deep link"))
        assertTrue(instruction.contains("[descriptive label](exact-uri)"))
        assertTrue(instruction.contains("Do not put the link in inline code or a code block"))
    }

    @Test
    fun defaultInstructionDoesNotContainDynamicToolSearchInstructions() {
        val instruction = AgentPrompts.defaultAssistantInstruction()

        assertFalse(instruction.contains("next model step"))
        assertFalse(instruction.contains("<tool_search>"))
    }

    @Test
    fun conversationTitleInstructionDefinesTaskAndExactOutputFormat() {
        val instruction = AgentPrompts.CONVERSATION_TITLE_INSTRUCTION

        assertTrue(instruction.startsWith("<task>"))
        assertTrue(instruction.contains("<output_requirements>"))
        assertTrue(instruction.contains("at most 10 words"))
        assertTrue(instruction.contains("Output only the title"))
        assertTrue(instruction.endsWith("</output_requirements>"))
    }
}
