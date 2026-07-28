package github.ponyhuang.asssistantai.data.conversation.local

import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationToolConfigurationCodecTest {

    @Test
    fun roundTripPreservesEverySessionSetting() {
        val configuration = ConversationToolConfiguration(
            enabledLocalToolIds = setOf("clock", "location"),
            enabledMcpServerIds = setOf("server-1"),
            enabledOfficialToolIdsByService = mapOf(
                "mimo" to setOf("web_search"),
                "kimi" to setOf("kimi_formulas"),
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
    fun blankOrMalformedPayloadIsTreatedAsUninitialized() {
        assertNull(ConversationToolConfigurationCodec.decode(null))
        assertNull(ConversationToolConfigurationCodec.decode(""))
        assertNull(ConversationToolConfigurationCodec.decode("{broken"))
    }
}
