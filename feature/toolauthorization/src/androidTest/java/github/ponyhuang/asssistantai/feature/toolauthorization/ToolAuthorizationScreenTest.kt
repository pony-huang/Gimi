package github.ponyhuang.asssistantai.feature.toolauthorization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import org.junit.Rule
import org.junit.Test

class ToolAuthorizationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchEmptyStateAndCompleteCatalogBulkActionsRemainAvailable() {
        var state by mutableStateOf(
            ToolAuthorizationUiState(
                tools = listOf(
                    ToolDescriptor("set_alarm", "set_alarm", "创建闹钟", true),
                    ToolDescriptor("get_location", "get_location", "读取位置", false),
                ),
            ),
        )
        compose.setContent {
            ToolAuthorizationScreen(
                state = state,
                onAction = { action ->
                    state = when (action) {
                        is ToolAuthorizationAction.Search -> state.copy(query = action.query)
                        is ToolAuthorizationAction.SetEnabled -> state.copy(
                            tools = state.tools.map { tool ->
                                if (tool.id == action.toolId) tool.copy(isEnabled = action.enabled) else tool
                            },
                        )
                        is ToolAuthorizationAction.SetAllEnabled -> state.copy(
                            tools = state.tools.map { it.copy(isEnabled = action.enabled) },
                        )
                    }
                },
            )
        }

        compose.onNodeWithText("搜索工具").performTextInput("不存在")
        compose.onNodeWithText("没有匹配的工具").assertIsDisplayed()
        compose.onNodeWithText("全部关闭").performClick()
        compose.onNodeWithText("已启用 0 / 2").assertIsDisplayed()
        compose.onNodeWithText("全部启用").performClick()
        compose.onNodeWithText("已启用 2 / 2").assertIsDisplayed()
    }
}
