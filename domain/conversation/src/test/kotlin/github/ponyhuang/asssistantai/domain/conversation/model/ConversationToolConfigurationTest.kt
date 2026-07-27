package github.ponyhuang.asssistantai.domain.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationToolConfigurationTest {

    @Test
    fun officialToolsDefaultToEverySupportedToolForAnUnseenService() {
        val configuration = ConversationToolConfiguration()

        val initialized = configuration.initializeOfficialTools(
            serviceId = "kimi",
            supportedToolIds = setOf("web_search", "kimi_formulas"),
        )

        assertEquals(
            setOf("web_search", "kimi_formulas"),
            initialized.enabledOfficialToolIds("kimi"),
        )
    }

    @Test
    fun officialToolSelectionsRemainIndependentBetweenServices() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialTools("mimo", setOf("web_search"))
            .setOfficialToolEnabled("mimo", "web_search", enabled = false)
            .initializeOfficialTools("kimi", setOf("web_search", "kimi_formulas"))

        assertFalse("web_search" in configuration.enabledOfficialToolIds("mimo"))
        assertTrue("web_search" in configuration.enabledOfficialToolIds("kimi"))
        assertTrue("kimi_formulas" in configuration.enabledOfficialToolIds("kimi"))
    }

    @Test
    fun sanitizeDropsToolsAndServersThatNoLongerExist() {
        val configuration = ConversationToolConfiguration(
            enabledLocalToolIds = setOf("clock", "removed_tool"),
            enabledMcpServerIds = setOf("server-1", "deleted-server"),
        )

        val sanitized = configuration.sanitize(
            availableLocalToolIds = setOf("clock", "location"),
            availableMcpServerIds = setOf("server-1", "server-2"),
        )

        assertEquals(setOf("clock"), sanitized.enabledLocalToolIds)
        assertEquals(setOf("server-1"), sanitized.enabledMcpServerIds)
    }
}
