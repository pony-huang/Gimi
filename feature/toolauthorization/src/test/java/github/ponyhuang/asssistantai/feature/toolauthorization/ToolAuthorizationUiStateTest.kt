package github.ponyhuang.asssistantai.feature.toolauthorization

import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolAuthorizationUiStateTest {
    private val tools = listOf(
        ToolDescriptor("set_alarm", "set_alarm", "创建闹钟", true),
        ToolDescriptor("get_location", "get_location", "读取当前位置", false),
    )

    @Test
    fun enabledCountAlwaysUsesCompleteCatalog() {
        val state = ToolAuthorizationUiState(query = "位置", tools = tools)

        assertEquals(1, state.enabledCount)
        assertEquals(listOf("get_location"), state.visibleTools.map { it.id })
    }

    @Test
    fun searchMatchesAdkNameAndDescriptionIgnoringCase() {
        assertEquals(
            listOf("set_alarm"),
            ToolAuthorizationUiState(query = "ALARM", tools = tools).visibleTools.map { it.id },
        )
        assertEquals(
            listOf("get_location"),
            ToolAuthorizationUiState(query = "当前", tools = tools).visibleTools.map { it.id },
        )
    }

    @Test
    fun unmatchedSearchProducesEmptyResult() {
        assertEquals(emptyList<ToolDescriptor>(), ToolAuthorizationUiState("不存在", tools).visibleTools)
    }
}
