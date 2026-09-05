package github.ponyhuang.gimi.domain.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationToolConfigurationTest {

    @Test
    fun newConversationsDefaultToMediumReasoningEffort() {
        assertEquals(
            ReasoningEffort.MEDIUM,
            ConversationToolConfiguration().reasoningEffort,
        )
    }

    @Test
    fun reasoningEffortOnlyContainsTheFourSupportedLevels() {
        assertEquals(
            listOf(
                ReasoningEffort.MINIMAL,
                ReasoningEffort.LOW,
                ReasoningEffort.MEDIUM,
                ReasoningEffort.HIGH,
            ),
            ReasoningEffort.entries,
        )
    }

    @Test
    fun newConversationsDefaultToAlwaysAvailableToolAccess() {
        assertEquals(
            ToolAccessMode.ALWAYS_AVAILABLE,
            ConversationToolConfiguration().toolAccessMode,
        )
    }

    @Test
    fun toolAccessModesOnlyContainExplicitLoadingPolicies() {
        assertEquals(
            listOf(ToolAccessMode.ON_DEMAND, ToolAccessMode.ALWAYS_AVAILABLE),
            ToolAccessMode.entries,
        )
    }

    @Test
    fun toolAccessModeSurvivesOtherConfigurationUpdates() {
        val configuration = ConversationToolConfiguration(
            toolAccessMode = ToolAccessMode.ON_DEMAND,
        )

        val updated = configuration
            .initializeOfficialFunctions(setOf("kimi_formulas"))
            .sanitize(availableMcpServerIds = emptySet())

        assertEquals(ToolAccessMode.ON_DEMAND, updated.toolAccessMode)
    }

    @Test
    fun officialFunctionsDefaultToTheAllMarkerForUnseenTools() {
        val configuration = ConversationToolConfiguration()

        val initialized = configuration.initializeOfficialFunctions(
            supportedToolIds = setOf("mimo_web_search", "kimi_formulas"),
        )

        assertEquals(
            setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            initialized.enabledOfficialFunctionIds("mimo_web_search"),
        )
        assertEquals(
            setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
            initialized.enabledOfficialFunctionIds("kimi_formulas"),
        )
    }

    @Test
    fun officialToolSelectionsRemainIndependentBetweenTools() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialFunctions(setOf("mimo_web_search", "kimi_formulas"))
            .setOfficialFunctionEnabled(
                toolId = "mimo_web_search",
                functionId = "mimo_web_search",
                supportedFunctionIds = setOf("mimo_web_search"),
                enabled = false,
            )

        assertFalse("mimo_web_search" in configuration.enabledOfficialFunctionIds("mimo_web_search"))
        assertTrue(
            ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in
                configuration.enabledOfficialFunctionIds("kimi_formulas"),
        )
    }

    @Test
    fun setOfficialFunctionEnabledExpandsMarkerBeforeApplyingChange() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialFunctions(setOf("kimi_formulas"))

        val updated = configuration.setOfficialFunctionEnabled(
            toolId = "kimi_formulas",
            functionId = "convert",
            supportedFunctionIds = setOf("convert", "rethink"),
            enabled = false,
        )

        val enabled = updated.enabledOfficialFunctionIds("kimi_formulas")
        assertFalse(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in enabled)
        assertTrue("convert" !in enabled)
        assertTrue("rethink" in enabled)
    }

    @Test
    fun expandOfficialFunctionsMarkerReplacesTheSentinelWithConcreteIds() {
        val configuration = ConversationToolConfiguration()
            .initializeOfficialFunctions(setOf("kimi_formulas"))

        val expanded = configuration.expandOfficialFunctionsMarker(
            toolId = "kimi_formulas",
            supportedFunctionIds = setOf("convert", "rethink"),
        )

        assertEquals(
            setOf("convert", "rethink"),
            expanded.enabledOfficialFunctionIds("kimi_formulas"),
        )
    }

    @Test
    fun expandOfficialFunctionsMarkerIsANoOpWhenMarkerAbsent() {
        val configuration = ConversationToolConfiguration(
            enabledOfficialFunctionIds = mapOf(
                "kimi_formulas" to setOf("convert"),
            ),
        )

        val expanded = configuration.expandOfficialFunctionsMarker(
            toolId = "kimi_formulas",
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
