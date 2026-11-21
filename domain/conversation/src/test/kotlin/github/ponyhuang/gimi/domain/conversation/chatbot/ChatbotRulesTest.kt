package github.ponyhuang.gimi.domain.conversation.chatbot

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.CHATBOT_WEB_SEARCH_TOOL_IDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatbotRulesTest {

    @Test fun `unlocked conversation never requires a new one`() {
        assertFalse(ChatbotRules.requiresNewConversation(lockedModelId = null, selectedModelId = "another"))
    }

    @Test fun `locked conversation requires a new one when the model id changes`() {
        assertTrue(ChatbotRules.requiresNewConversation("deepseek-flash", "another"))
    }

    @Test fun `locked conversation keeps identity when only the service changes`() {
        assertFalse(ChatbotRules.requiresNewConversation("deepseek-flash", "deepseek-flash"))
    }

    @Test fun `title falls back to the first utterance truncated to 32 chars`() {
        assertEquals("hello", ChatbotRules.fallbackTitle(currentTitle = "", firstUserText = "hello"))
        assertEquals("x".repeat(32), ChatbotRules.fallbackTitle("", "x".repeat(40)))
    }

    @Test fun `blank first utterance falls back to the attachment display name`() {
        assertEquals("photo.png", ChatbotRules.fallbackTitle("", " ", "photo.png"))
        assertEquals("", ChatbotRules.fallbackTitle("", " ", null))
    }

    @Test fun `existing title always wins`() {
        assertEquals("existing", ChatbotRules.fallbackTitle("existing", "hello"))
    }

    @Test fun `default tools enable every vendor search id with all functions allowed`() {
        val config = ChatbotRules.defaultToolConfiguration("deepseek")
        assertFalse(config.thinkingEnabled)
        assertEquals(CHATBOT_WEB_SEARCH_TOOL_IDS["deepseek"], config.enabledOfficialFunctionIds.keys)
        assertEquals(2, config.enabledOfficialFunctionIds.size)
        assertTrue(config.enabledOfficialFunctionIds.values.all {
            it == setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER)
        })
    }

    @Test fun `vendors without search tools get an empty configuration`() {
        val config = ChatbotRules.defaultToolConfiguration("qwen")
        assertFalse(config.thinkingEnabled)
        assertTrue(config.enabledOfficialFunctionIds.isEmpty())
        assertTrue(ChatbotRules.defaultToolConfiguration(null).enabledOfficialFunctionIds.isEmpty())
    }
}
