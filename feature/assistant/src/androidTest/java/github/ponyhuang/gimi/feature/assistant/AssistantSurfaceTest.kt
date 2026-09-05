package github.ponyhuang.gimi.feature.assistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessage
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessageAuthor
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import org.junit.Assert.assertEquals
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
                    messages = listOf(
                        AssistantMessage(1, AssistantMessageAuthor.USER, "打开地图"),
                        AssistantMessage(2, AssistantMessageAuthor.ASSISTANT, "已经打开地图。"),
                    ),
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
        // 头部状态标题已移除（避免与操作按钮重叠），无消息时不应出现“正在思考”。
        composeRule.onNodeWithText("正在思考").assertDoesNotExist()
    }

    @Test
    fun idleAssistantShowsVoiceInputControlAndInputCapsule() {
        composeRule.setContent {
            AssistantSurface(
                state = AssistantSessionState(
                    phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                    presentationVisible = true,
                ),
                mode = AssistantSurfaceMode.SHEET,
                onDismiss = {},
                onStop = {},
                onOpenChat = {},
            )
        }

        // 空状态：中部居中麦克风为主控，底部常驻聊天胶囊输入。
        composeRule.onNodeWithContentDescription("开始聆听").assertIsDisplayed()
        composeRule.onNodeWithTag("assistant_input_capsule").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("发送").assertIsDisplayed()
    }

    @Test
    fun typingIntoInputCapsuleSubmitsText() {
        var submitted: String? = null
        composeRule.setContent {
            AssistantSurface(
                state = AssistantSessionState(
                    phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                    presentationVisible = true,
                ),
                mode = AssistantSurfaceMode.SHEET,
                onDismiss = {},
                onStop = {},
                onOpenChat = {},
                onTextSubmit = { submitted = it },
            )
        }

        composeRule.onNodeWithTag("assistant_input_capsule").performTextInput("打开地图")
        composeRule.onNodeWithContentDescription("发送").performClick()
        composeRule.waitForIdle()
        assertEquals("打开地图", submitted)
    }
}
