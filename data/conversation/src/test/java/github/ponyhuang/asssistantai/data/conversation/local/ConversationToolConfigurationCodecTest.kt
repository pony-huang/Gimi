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
            enabledOfficialToolIdsByService = mapOf(
                "mimo" to setOf("web_search"),
                "kimi" to setOf("kimi_formulas"),
            ),
            toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
        )

        val encoded = ConversationToolConfigurationCodec.encode(configuration)

        assertEquals(configuration, ConversationToolConfigurationCodec.decode(encoded))
    }

    @Test
    fun blankOrMalformedPayloadIsTreatedAsUninitialized() {
        assertNull(ConversationToolConfigurationCodec.decode(null))
        assertNull(ConversationToolConfigurationCodec.decode(""))
        assertNull(ConversationToolConfigurationCodec.decode("{broken"))
    }
}
