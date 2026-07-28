package github.ponyhuang.asssistantai.feature.chat

import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
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

    @Test
    fun enabledOfficialFunctionCountUsesTheMarkerAsAFullSelection() {
        val state = ChatAddToChatState(
            serviceId = "kimi",
            configuration = ConversationToolConfiguration(
                enabledOfficialFunctionIdsByService = mapOf(
                    "kimi" to mapOf(
                        OfficialToolIds.KIMI_FORMULAS to
                            setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
                    ),
                ),
            ),
            officialTools = listOf(
                OfficialToolDescriptor(
                    id = OfficialToolIds.KIMI_FORMULAS,
                    functions = listOf(
                        OfficialToolFunction("convert", "convert", "convert formula"),
                        OfficialToolFunction("rethink", "rethink", "rethink formula"),
                    ),
                ),
            ),
        )

        assertEquals(2, state.enabledOfficialFunctionCount(OfficialToolIds.KIMI_FORMULAS))
    }

    private fun tool(id: String, name: String, enabled: Boolean) = ToolDescriptor(
        id = id,
        name = name,
        description = "$name description",
        isEnabled = enabled,
    )
}