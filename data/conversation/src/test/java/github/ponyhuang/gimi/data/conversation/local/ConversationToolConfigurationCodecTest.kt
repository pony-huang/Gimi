package github.ponyhuang.gimi.data.conversation.local

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
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
            toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
        )

        val encoded = ConversationToolConfigurationCodec.encode(configuration)

        assertEquals(true, encoded.contains("\"toolAccessMode\":\"ALWAYS_AVAILABLE\""))
        assertEquals(true, encoded.contains("\"pendingMcpCredentialServerId\":\"server-1\""))
        assertEquals(configuration, ConversationToolConfigurationCodec.decode(encoded))
    }

    @Test
    fun missingToolAccessModeDefaultsToAlwaysAvailable() {
        val decoded = ConversationToolConfigurationCodec.decode(
            """
            {
              "enabledMcpServerIds": ["server-1"]
            }
            """.trimIndent(),
        )

        assertEquals(ToolAccessMode.ALWAYS_AVAILABLE, decoded?.toolAccessMode)
    }

    @Test
    fun legacyAutomaticToolAccessDefaultsToAlwaysAvailable() {
        // 旧版本持久化 payload 含已移除的 enabledLocalToolIds，必须继续可解码。
        val decoded = ConversationToolConfigurationCodec.decode(
            """
            {
              "enabledLocalToolIds": ["clock"],
              "toolAccessMode": "AUTO"
            }
            """.trimIndent(),
        )

        assertEquals(ToolAccessMode.ALWAYS_AVAILABLE, decoded?.toolAccessMode)
    }

    @Test
    fun onDemandToolAccessRoundTrips() {
        val configuration = ConversationToolConfiguration(
            toolAccessMode = ToolAccessMode.ON_DEMAND,
        )

        assertEquals(
            configuration,
            ConversationToolConfigurationCodec.decode(
                ConversationToolConfigurationCodec.encode(configuration),
            ),
        )
    }

    @Test
    fun blankOrMalformedPayloadIsTreatedAsUninitialized() {
        assertNull(ConversationToolConfigurationCodec.decode(null))
        assertNull(ConversationToolConfigurationCodec.decode(""))
        assertNull(ConversationToolConfigurationCodec.decode("{broken"))
    }
}
