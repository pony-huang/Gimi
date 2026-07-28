package github.ponyhuang.asssistantai.data.conversation.local

import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationToolConfigurationCodecTest {

    @Test
    fun roundTripPreservesEverySessionSetting() {
        val configuration = ConversationToolConfiguration(
            enabledLocalToolIds = setOf("clock", "location"),
            enabledMcpServerIds = setOf("server-1"),
            enabledOfficialFunctionIdsByService = mapOf(
                "mimo" to mapOf("web_search" to setOf("web_search")),
                "kimi" to mapOf(
                    "kimi_formulas" to setOf(
                        ConversationToolConfiguration.ALL_FUNCTIONS_MARKER,
                    ),
                ),
            ),
        )

        val encoded = ConversationToolConfigurationCodec.encode(configuration)

        assertFalse(encoded.contains("toolAccessMode"))
        assertEquals(configuration, ConversationToolConfigurationCodec.decode(encoded))
    }

    @Test
    fun legacyToolAccessModeIsIgnoredWhenReadingSavedConfiguration() {
        val decoded = ConversationToolConfigurationCodec.decode(
            """
            {
              "enabledLocalToolIds": ["clock"],
              "enabledMcpServerIds": ["server-1"],
              "toolAccessMode": "ON_DEMAND"
            }
            """.trimIndent(),
        )

        assertEquals(
            ConversationToolConfiguration(
                enabledLocalToolIds = setOf("clock"),
                enabledMcpServerIds = setOf("server-1"),
            ),
            decoded,
        )
    }

    @Test
    fun legacyEnabledOfficialToolIdsByServiceIsMigratedToAllFunctionsMarker() {
        val decoded = ConversationToolConfigurationCodec.decode(
            """
            {
              "enabledLocalToolIds": ["clock"],
              "enabledMcpServerIds": [],
              "enabledOfficialToolIdsByService": {
                "kimi": ["kimi_formulas", "web_search"],
                "mimo": ["web_search"]
              }
            }
            """.trimIndent(),
        )!!

        assertTrue(
            ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in
                decoded.enabledOfficialFunctionIds("kimi", "kimi_formulas"),
        )
        assertTrue(
            ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in
                decoded.enabledOfficialFunctionIds("kimi", "web_search"),
        )
        assertTrue(
            ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in
                decoded.enabledOfficialFunctionIds("mimo", "web_search"),
        )
    }

    @Test
    fun blankOrMalformedPayloadIsTreatedAsUninitialized() {
        assertNull(ConversationToolConfigurationCodec.decode(null))
        assertNull(ConversationToolConfigurationCodec.decode(""))
        assertNull(ConversationToolConfigurationCodec.decode("{broken"))
    }
}