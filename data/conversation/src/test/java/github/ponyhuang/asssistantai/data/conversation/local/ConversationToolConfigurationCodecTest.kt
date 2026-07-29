package github.ponyhuang.asssistantai.data.conversation.local

import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
        )

        val encoded = ConversationToolConfigurationCodec.encode(configuration)

        assertEquals(true, encoded.contains("\"toolAccessMode\":\"ALWAYS_AVAILABLE\""))
        assertEquals(configuration, ConversationToolConfigurationCodec.decode(encoded))
    }

    @Test
    fun missingToolAccessModeDefaultsToAutomatic() {
        val decoded = ConversationToolConfigurationCodec.decode(
            """
            {
              "enabledLocalToolIds": ["clock"],
              "enabledMcpServerIds": ["server-1"]
            }
            """.trimIndent(),
        )

        assertEquals(ToolAccessMode.AUTO, decoded?.toolAccessMode)
    }

    @Test
    fun blankOrMalformedPayloadIsTreatedAsUninitialized() {
        assertNull(ConversationToolConfigurationCodec.decode(null))
        assertNull(ConversationToolConfigurationCodec.decode(""))
        assertNull(ConversationToolConfigurationCodec.decode("{broken"))
    }
}
