package github.ponyhuang.asssistantai.domain.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationToolConfigurationTest {

    @Test
    fun newConversationsDefaultToAutomaticToolAccess() {
        assertEquals(
            ToolAccessMode.AUTO,
            ConversationToolConfiguration().toolAccessMode,
        )
    }

    @Test
    fun toolAccessModeSurvivesOtherConfigurationUpdates() {
        val configuration = ConversationToolConfiguration(
            toolAccessMode = ToolAccessMode.ON_DEMAND,
        )

        val updated = configuration
            .initializeOfficialFunctions("kimi", setOf("kimi_formulas"))
            .sanitize(
                availableLocalToolIds = emptySet(),
                availableMcpServerIds = emptySet(),
            )

        assertEquals(ToolAccessMode.ON_DEMAND, updated.toolAccessMode)
    }

    @Test
    fun officialFunctionsDefaultToTheAllMarkerForAnUnseenService() {
        val configuration = ConversationToolConfiguration()

        val initialized = configuration.initializeOfficialFunctions(
            serviceId = "kimi",
            supportedToolIds = setOf("web_search", "kimi_formulas"),
        )

        assertEquals(
            setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            initialized.enabledOfficialFunctionIds("kimi", "web_search"),
        )
        assertEquals(
            setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            initialized.enabledOfficialFunctionIds("kimi", "kimi_formulas"),
        )
    }

    @Test
    fun officialToolSelectionsRemainIndependentBetweenServices() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialFunctions("mimo", setOf("web_search"))
            .setOfficialFunctionEnabled(
                "mimo",
                "web_search",
                "web_search",
                supportedFunctionIds = setOf("web_search"),
                enabled = false,
            )
            .initializeOfficialFunctions("kimi", setOf("web_search", "kimi_formulas"))

        assertFalse("web_search" in configuration.enabledOfficialFunctionIds("mimo", "web_search"))
        assertTrue(
            ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in
                configuration.enabledOfficialFunctionIds("kimi", "web_search"),
        )
        assertTrue(
            ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in
                configuration.enabledOfficialFunctionIds("kimi", "kimi_formulas"),
        )
    }

    @Test
    fun setOfficialFunctionEnabledExpandsMarkerBeforeApplyingChange() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialFunctions("kimi", setOf("kimi_formulas"))

        val updated = configuration.setOfficialFunctionEnabled(
            serviceId = "kimi",
            toolId = "kimi_formulas",
            functionId = "convert",
            supportedFunctionIds = setOf("convert", "rethink"),
            enabled = false,
        )

        val enabled = updated.enabledOfficialFunctionIds("kimi", "kimi_formulas")
        assertFalse(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in enabled)
        assertTrue("convert" !in enabled)
        assertTrue("rethink" in enabled)
    }

    @Test
    fun expandOfficialFunctionsMarkerReplacesTheSentinelWithConcreteIds() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialFunctions("kimi", setOf("kimi_formulas"))

        val expanded = configuration.expandOfficialFunctionsMarker(
            serviceId = "kimi",
            toolId = "kimi_formulas",
            supportedFunctionIds = setOf("convert", "rethink"),
        )

        assertEquals(
            setOf("convert", "rethink"),
            expanded.enabledOfficialFunctionIds("kimi", "kimi_formulas"),
        )
    }

    @Test
    fun expandOfficialFunctionsMarkerIsANoOpWhenMarkerAbsent() {
        val configuration = ConversationToolConfiguration(
            enabledOfficialFunctionIdsByService = mapOf(
                "kimi" to mapOf("kimi_formulas" to setOf("convert")),
            ),
        )

        val expanded = configuration.expandOfficialFunctionsMarker(
            serviceId = "kimi",
            toolId = "kimi_formulas",
            supportedFunctionIds = setOf("convert", "rethink"),
        )

        assertEquals(configuration, expanded)
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
