package github.ponyhuang.gimi.data.conversation.local

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationToolConfigurationCodecTest {

    @Test
    fun roundTripPreservesEverySessionSetting() {
        val configuration = ConversationToolConfiguration(
            enabledMcpServerIds = setOf("server-1"),
            pendingMcpCredentialServerId = "server-1",
            enabledOfficialFunctionIds = mapOf(
                "mimo_web_search" to setOf("mimo_web_search"),
                "kimi_formulas" to setOf(
                    ConversationToolConfiguration.ALL_FUNCTIONS_MARKER,
                ),
            ),
        )

        val encoded = ConversationToolConfigurationCodec.encode(configuration)

        assertEquals(true, encoded.contains("\"pendingMcpCredentialServerId\":\"server-1\""))
        assertEquals(configuration, ConversationToolConfigurationCodec.decode(encoded))
    }

    @Test
    fun legacyPayloadWithRemovedFieldsStillDecodes() {
        val decoded = ConversationToolConfigurationCodec.decode(
            """
            {
              "enabledMcpServerIds": ["server-1"],
              "toolAccessMode": "AUTO",
              "enabledLocalToolIds": ["clock"]
            }
            """.trimIndent(),
        )

        assertEquals(setOf("server-1"), decoded?.enabledMcpServerIds)
    }

    @Test
    fun blankOrMalformedPayloadIsTreatedAsUninitialized() {
        assertNull(ConversationToolConfigurationCodec.decode(null))
        assertNull(ConversationToolConfigurationCodec.decode(""))
        assertNull(ConversationToolConfigurationCodec.decode("{broken"))
    }
}
