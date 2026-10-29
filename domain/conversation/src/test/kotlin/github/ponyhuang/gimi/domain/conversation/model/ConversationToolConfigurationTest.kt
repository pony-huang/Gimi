package github.ponyhuang.gimi.domain.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationToolConfigurationTest {

    @Test
    fun officialFunctionsDefaultToTheAllMarkerForUnseenTools() {
        val configuration = ConversationToolConfiguration()

        val initialized = configuration.initializeOfficialFunctions(
            supportedToolIds = setOf("mimo_web_search", "kimi_web_tools"),
        )

        assertEquals(
            setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            initialized.enabledOfficialFunctionIds("mimo_web_search"),
        )
        assertEquals(
            setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            initialized.enabledOfficialFunctionIds("kimi_web_tools"),
        )
    }

    @Test
    fun officialToolSelectionsRemainIndependentBetweenTools() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialFunctions(setOf("mimo_web_search", "kimi_web_tools"))
            .setOfficialFunctionEnabled(
                toolId = "mimo_web_search",
                functionId = "mimo_web_search",
                supportedFunctionIds = setOf("mimo_web_search"),
                enabled = false,
            )

        assertFalse("mimo_web_search" in configuration.enabledOfficialFunctionIds("mimo_web_search"))
        assertTrue(
            ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in
                configuration.enabledOfficialFunctionIds("kimi_web_tools"),
        )
    }

    @Test
    fun setOfficialFunctionEnabledExpandsMarkerBeforeApplyingChange() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialFunctions(setOf("kimi_web_tools"))

        val updated = configuration.setOfficialFunctionEnabled(
            toolId = "kimi_web_tools",
            functionId = "convert",
            supportedFunctionIds = setOf("convert", "rethink"),
            enabled = false,
        )

        val enabled = updated.enabledOfficialFunctionIds("kimi_web_tools")
        assertFalse(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in enabled)
        assertTrue("convert" !in enabled)
        assertTrue("rethink" in enabled)
    }

    @Test
    fun expandOfficialFunctionsMarkerReplacesTheSentinelWithConcreteIds() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialFunctions(setOf("kimi_web_tools"))

        val expanded = configuration.expandOfficialFunctionsMarker(
            toolId = "kimi_web_tools",
            supportedFunctionIds = setOf("convert", "rethink"),
        )

        assertEquals(
            setOf("convert", "rethink"),
            expanded.enabledOfficialFunctionIds("kimi_web_tools"),
        )
    }

    @Test
    fun expandOfficialFunctionsMarkerIsANoOpWhenMarkerAbsent() {
        val configuration = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf(
                "kimi_web_tools" to setOf("convert"),
            ),
        )

        val expanded = configuration.expandOfficialFunctionsMarker(
            toolId = "kimi_web_tools",
            supportedFunctionIds = setOf("convert", "rethink"),
        )

        assertEquals(configuration, expanded)
    }

    @Test
    fun sanitizeDropsServersThatNoLongerExist() {
        val configuration = ConversationToolConfiguration(
            enabledMcpServerIds = setOf("server-1", "deleted-server"),
        )

        val sanitized = configuration.sanitize(
            availableMcpServerIds = setOf("server-1", "server-2"),
        )

        assertEquals(setOf("server-1"), sanitized.enabledMcpServerIds)
    }
}
