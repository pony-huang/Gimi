package github.ponyhuang.gimi.feature.chat

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import java.io.File
import org.junit.Rule
import org.junit.Test

/**
 * 复现「附件发送成功后输入胶囊仍保留附件」的回归测试：
 * ACCEPTED 回执必须同时清除文字与附件 chip，即使回执期间发生了
 * uiState 刷新或胶囊短暂离开组合（挂起面板替换）。
 */
@OptIn(ExperimentalTestApi::class)
class ChatComposerDraftClearingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val attachment = DraftAttachment(
        reference = "/chat-test/attachment.pdf",
        displayName = "attachment.pdf",
        mimeType = "application/pdf",
        sizeBytes = 128,
        category = AttachmentCategory.DOCUMENT,
    )

    @Test
    fun acceptedReceiptClearsAttachmentChip() {
        var reply: ((ChatSubmissionResult) -> Unit)? = null
        composeRule.setContent {
            MaterialTheme {
                ChatComposer(
                    onSendClick = { _, onResult -> reply = onResult },
                    onStopClick = {},
                    isGenerating = false,
                    messageData = MessageData(attachments = listOf(attachment)),
                )
            }
        }

        composeRule.onNodeWithTag("chat_composer_attachment").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_send").performClick()
        composeRule.runOnIdle { reply!!(ChatSubmissionResult.ACCEPTED) }

        composeRule.onNodeWithTag("chat_composer_attachment").assertDoesNotExist()
    }

    @Test
    fun acceptedReceiptClearsAttachmentChipAfterRunningStateRefresh() {
        var reply: ((ChatSubmissionResult) -> Unit)? = null
        val isGenerating = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                ChatComposer(
                    onSendClick = { _, onResult -> reply = onResult },
                    onStopClick = {},
                    isGenerating = isGenerating.value,
                    messageData = MessageData(attachments = listOf(attachment)),
                )
            }
        }

        composeRule.onNodeWithTag("chat_composer_attachment").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_composer_send").performClick()
        // 模拟发送接管前 ViewModel 已发布运行中状态（胶囊仍组合，只是重组）。
        composeRule.runOnIdle { isGenerating.value = true }
        composeRule.runOnIdle { reply!!(ChatSubmissionResult.ACCEPTED) }

        composeRule.onNodeWithTag("chat_composer_attachment").assertDoesNotExist()
    }

    @Test
    fun receiptDuringPendingPanelSwapDoesNotRestoreAttachmentChipAfterwards() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "shared-doc.pdf").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        var reply: ((ChatSubmissionResult) -> Unit)? = null
        val showPanel: MutableState<Boolean> = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                AnimatedContent(targetState = showPanel.value, label = "composerSwap") { panel ->
                    if (panel) {
                        Text(text = "panel", modifier = Modifier.testTag("pending_panel"))
                    } else {
                        ChatComposer(
                            onSendClick = { _, onResult -> reply = onResult },
                            onStopClick = {},
                            isGenerating = false,
                            sharedMediaUris = listOf(Uri.fromFile(source)),
                            onSharedMediaConsumed = { showPanel.value = showPanel.value },
                        )
                    }
                }
            }
        }

        // 走真实导入路径：附件只存在于胶囊内部草稿状态（与线上一致）。
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("chat_composer_attachment")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chat_composer_send").performClick()
        // 回执到达前胶囊被挂起面板替换（离开组合但草稿快照被保存）。
        composeRule.runOnIdle { showPanel.value = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { reply!!(ChatSubmissionResult.ACCEPTED) }
        composeRule.runOnIdle { showPanel.value = false }
        composeRule.waitForIdle()

        // 已接管的发送不得在胶囊恢复后把附件 chip 带回来。
        composeRule.onNodeWithTag("chat_composer_attachment").assertDoesNotExist()
    }
}
