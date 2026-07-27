package github.ponyhuang.asssistantai.feature.chat

import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAddToChatStateTest {

    @Test
    fun upwardRemainderAtListEndIsConsumedBeforeItReachesSheet() {
        assertEquals(-18f, consumeAtLowerScrollBoundary(-18f))
        assertEquals(0f, consumeAtLowerScrollBoundary(18f))
        assertEquals(0f, consumeAtLowerScrollBoundary(0f))
    }

    @Test
    fun visibleToolsApplySearchAndEnabledFilterWithoutMixingOtherToolTypes() {
        val state = ChatAddToChatState(
            configuration = ConversationToolConfiguration(
                enabledLocalToolIds = setOf("clock"),
            ),
            localTools = listOf(
                tool("clock", "时钟", enabled = true),
                tool("location", "位置", enabled = true),
            ),
        )

        assertEquals(
            listOf("clock"),
            state.visibleLocalTools("时", SessionToolFilter.ENABLED).map { it.id },
        )
        assertEquals(
            listOf("location"),
            state.visibleLocalTools("", SessionToolFilter.DISABLED).map { it.id },
        )
    }

    private fun tool(id: String, name: String, enabled: Boolean) = ToolDescriptor(
        id = id,
        name = name,
        description = "$name description",
        isEnabled = enabled,
    )
}
