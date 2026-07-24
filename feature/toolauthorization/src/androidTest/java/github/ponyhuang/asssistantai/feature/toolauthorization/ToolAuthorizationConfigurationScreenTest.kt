package github.ponyhuang.asssistantai.feature.toolauthorization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import org.junit.Rule
import org.junit.Test

class ToolAuthorizationConfigurationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchFiltersToolsAndFilterDropdownShowsOnlyMatchingState() {
        var state by mutableStateOf(
            ToolAuthorizationConfigurationUiState(
                tools = listOf(
                    ToolDescriptor("set_alarm", "set_alarm", "创建闹钟", true),
                    ToolDescriptor("get_location", "get_location", "读取位置", false),
                ),
            ),
        )
        compose.setContent {
            ToolAuthorizationConfigurationScreen(
                state = state,
                onAction = { action ->
                    state = when (action) {
                        is ToolAuthorizationConfigurationAction.Search -> state.copy(query = action.query)
                        is ToolAuthorizationConfigurationAction.SetFilter -> state.copy(filter = action.filter)
                        is ToolAuthorizationConfigurationAction.SetEnabled -> state.copy(
                            tools = state.tools.map { tool ->
                                if (tool.id == action.toolId) tool.copy(isEnabled = action.enabled) else tool
                            },
                        )
                    }
                },
            )
        }

        compose.onNodeWithText("搜索工具").performTextInput("不存在")
        compose.onNodeWithText("没有匹配的工具").assertIsDisplayed()

        // 输入后占位符已消失，按当前内容定位输入框再清空。
        compose.onNodeWithText("不存在").performTextClearance()
        compose.onNodeWithText("set_alarm").assertIsDisplayed()
        compose.onNodeWithText("get_location").assertIsDisplayed()

        compose.onNodeWithContentDescription("过滤，当前：全部").performClick()
        compose.onNodeWithText("已关闭").performClick()
        compose.onNodeWithText("set_alarm").assertIsNotDisplayed()
        compose.onNodeWithText("get_location").assertIsDisplayed()
    }

    @Test
    fun togglingToolUpdatesState() {
        var state by mutableStateOf(
            ToolAuthorizationConfigurationUiState(
                tools = listOf(ToolDescriptor("clock", "clock", "Clock", true)),
            ),
        )
        compose.setContent {
            ToolAuthorizationConfigurationScreen(
                state = state,
                onAction = { action ->
                    state = when (action) {
                        is ToolAuthorizationConfigurationAction.SetEnabled -> state.copy(
                            tools = state.tools.map { tool ->
                                if (tool.id == action.toolId) tool.copy(isEnabled = action.enabled) else tool
                            },
                        )
                        else -> state
                    }
                },
            )
        }

        compose.onNodeWithText("已启用 1 / 1").assertIsDisplayed()
        compose.onNodeWithText("clock").performClick()
        compose.onNodeWithText("已启用 0 / 1").assertIsDisplayed()
    }
}
