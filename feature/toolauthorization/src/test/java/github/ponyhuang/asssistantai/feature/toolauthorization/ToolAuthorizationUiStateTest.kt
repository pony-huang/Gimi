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
    fun outerStateCountsEnabledTools() {
        val state = ToolAuthorizationUiState(tools = tools)

        assertEquals(1, state.enabledCount)
        assertEquals(2, state.totalCount)
    }
}

class ToolAuthorizationConfigurationUiStateTest {
    private val tools = listOf(
        ToolDescriptor("set_alarm", "set_alarm", "创建闹钟", true),
        ToolDescriptor("get_location", "get_location", "读取当前位置", false),
    )

    @Test
    fun enabledCountAlwaysUsesCompleteCatalog() {
        val state = ToolAuthorizationConfigurationUiState(
            query = "位置",
            tools = tools,
        )

        assertEquals(1, state.enabledCount)
        assertEquals(listOf("get_location"), state.visibleTools.map { it.id })
    }

    @Test
    fun searchMatchesNameAndDescriptionIgnoringCase() {
        assertEquals(
            listOf("set_alarm"),
            ToolAuthorizationConfigurationUiState(query = "ALARM", tools = tools).visibleTools.map { it.id },
        )
        assertEquals(
            listOf("get_location"),
            ToolAuthorizationConfigurationUiState(query = "当前", tools = tools).visibleTools.map { it.id },
        )
    }

    @Test
    fun unmatchedSearchProducesEmptyResult() {
        assertEquals(
            emptyList<ToolDescriptor>(),
            ToolAuthorizationConfigurationUiState("不存在", tools = tools).visibleTools,
        )
    }

    @Test
    fun filterEnabledShowsOnlyEnabledTools() {
        assertEquals(
            listOf("set_alarm"),
            ToolAuthorizationConfigurationUiState(
                filter = ToolAuthorizationFilter.ENABLED,
                tools = tools,
            ).visibleTools.map { it.id },
        )
    }

    @Test
    fun filterDisabledShowsOnlyDisabledTools() {
        assertEquals(
            listOf("get_location"),
            ToolAuthorizationConfigurationUiState(
                filter = ToolAuthorizationFilter.DISABLED,
                tools = tools,
            ).visibleTools.map { it.id },
        )
    }

    @Test
    fun searchAndFilterCombine() {
        assertEquals(
            listOf("get_location"),
            ToolAuthorizationConfigurationUiState(
                query = "位置",
                filter = ToolAuthorizationFilter.DISABLED,
                tools = tools,
            ).visibleTools.map { it.id },
        )
        assertEquals(
            emptyList<ToolDescriptor>(),
            ToolAuthorizationConfigurationUiState(
                query = "闹钟",
                filter = ToolAuthorizationFilter.DISABLED,
                tools = tools,
            ).visibleTools,
        )
    }
}
