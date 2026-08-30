package github.ponyhuang.gimi.feature.toolauthorization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import org.junit.Rule
import org.junit.Test

class ToolAuthorizationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun customizationOffBlocksConfigurationEntry() {
        var navigated = false
        var state by mutableStateOf(
            ToolAuthorizationUiState(
                isCustomizationEnabled = false,
                tools = listOf(
                    ToolDescriptor("set_alarm", "set_alarm", "创建闹钟", true),
                    ToolDescriptor("get_location", "get_location", "读取位置", true),
                ),
            ),
        )
        compose.setContent {
            ToolAuthorizationScreen(
                state = state,
                onAction = { action ->
                    state = when (action) {
                        is ToolAuthorizationAction.SetCustomizationEnabled -> state.copy(
                            isCustomizationEnabled = action.enabled,
                        )
                    }
                },
                onNavigateToConfiguration = { navigated = true },
            )
        }

        compose.onNodeWithText("自定义工具").assertIsDisplayed()
        compose.onNodeWithText("已关闭").assertDoesNotExist()
        // 关闭态下依赖配置整行隐藏，而非置灰常显
        compose.onNodeWithText("配置工具").assertDoesNotExist()

        compose.onNodeWithText("自定义工具").performClick()
        compose.onNodeWithText("配置工具").assertIsDisplayed()
        compose.onNodeWithText("选择要启用的工具").assertIsDisplayed()
        compose.onNodeWithText("配置工具").performClick()
        assert(navigated)
    }

    @Test
    fun mutationBlockedShowsBlockedNotice() {
        val state = ToolAuthorizationUiState(
            isCustomizationEnabled = true,
            tools = listOf(ToolDescriptor("clock", "clock", "Clock", true)),
            isMutationBlocked = true,
        )
        compose.setContent {
            ToolAuthorizationScreen(
                state = state,
                onAction = {},
                onNavigateToConfiguration = {},
            )
        }

        compose.onNodeWithText("Agent 任务进行中，请先停止任务后再修改。").assertIsDisplayed()
    }
}
