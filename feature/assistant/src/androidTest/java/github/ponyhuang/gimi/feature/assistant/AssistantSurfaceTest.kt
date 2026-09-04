package github.ponyhuang.gimi.feature.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import github.ponyhuang.gimi.domain.assistant.model.AssistantTurn
import org.junit.Rule
import org.junit.Test

class AssistantSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedTurnShowsQuestionAnswerAndOpenChatAction() {
        composeRule.setContent {
            AssistantSurface(
                state = AssistantSessionState(
                    phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                    turn = AssistantTurn("打开地图", "已经打开地图。"),
                    presentationVisible = true,
                ),
                mode = AssistantSurfaceMode.SHEET,
                onDismiss = {},
                onStop = {},
                onOpenChat = {},
            )
        }

        composeRule.onNodeWithText("打开地图").assertIsDisplayed()
        composeRule.onNodeWithText("已经打开地图。").assertIsDisplayed()
        composeRule.onNodeWithText("打开聊天").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭").assertIsDisplayed()
    }

    @Test
    fun activeTaskShowsStopAction() {
        composeRule.setContent {
            AssistantSurface(
                state = AssistantSessionState(
                    phase = AssistantSessionPhase.GENERATING,
                    taskActive = true,
                    presentationVisible = true,
                ),
                mode = AssistantSurfaceMode.OVERLAY,
                onDismiss = {},
                onStop = {},
                onOpenChat = {},
            )
        }

        composeRule.onNodeWithText("停止").assertIsDisplayed()
        composeRule.onNodeWithText("正在思考").assertIsDisplayed()
    }
}
