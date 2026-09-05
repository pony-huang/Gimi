package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAddToChatStateTest {

    @Test
    fun upwardRemainderAtListEndIsConsumedBeforeItReachesSheet() {
        assertEquals(
            -18f,
            consumeAtLowerScrollBoundary(availableY = -18f, canScrollForward = false),
        )
        assertEquals(
            0f,
            consumeAtLowerScrollBoundary(availableY = 18f, canScrollForward = false),
        )
        assertEquals(
            0f,
            consumeAtLowerScrollBoundary(availableY = 0f, canScrollForward = false),
        )
    }

    @Test
    fun upwardMotionBeforeListEndRemainsAvailableToTheList() {
        assertEquals(
            0f,
            consumeAtLowerScrollBoundary(availableY = -18f, canScrollForward = true),
        )
    }

    @Test
    fun enabledOfficialFunctionCountUsesTheMarkerAsAFullSelection() {
        val state = ChatAddToChatState(
            serviceId = "kimi",
            configuration = ConversationToolConfiguration(
                enabledOfficialFunctionIdsByService = mapOf(
                    "kimi" to mapOf(
                        KIMI_FORMULAS_TOOL_ID to
                            setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER),
                    ),
                ),
            ),
            officialTools = listOf(
                OfficialToolDescriptor(
                    id = KIMI_FORMULAS_TOOL_ID,
                    functions = listOf(
                        OfficialToolFunction("convert", "convert", "convert formula"),
                        OfficialToolFunction("rethink", "rethink", "rethink formula"),
                    ),
                ),
            ),
        )

        assertEquals(2, state.enabledOfficialFunctionCount(KIMI_FORMULAS_TOOL_ID))
    }
}

private const val KIMI_FORMULAS_TOOL_ID: String = "kimi_formulas"
