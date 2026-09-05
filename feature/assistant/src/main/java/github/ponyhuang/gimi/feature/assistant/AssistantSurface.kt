package github.ponyhuang.gimi.feature.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessage
import github.ponyhuang.gimi.domain.assistant.model.AssistantMessageAuthor
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import github.ponyhuang.gimi.domain.assistant.model.shouldShowConversation
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 助手界面在不同宿主中的承载方式。 */
enum class AssistantSurfaceMode {
    SHEET,
    OVERLAY,
    LOCK_SCREEN,
}

/**
 * 语音助手的两态界面：悬浮胶囊态与全宽对话面板态。
 *
 * 唤醒后先出现底部悬浮胶囊（输入框 + 麦克风 + 发送），指令就绪后展开为通栏对话面板，
 * 消息渲染复用 [github.ponyhuang.gimi.ui.chatcontent.ChatMessageBubble]，
 * 与聊天页保持同一套视觉。三种宿主（应用内 Sheet / 系统悬浮窗 / 锁屏 Activity）渲染同一 Composable。
 * 面板态见 [AssistantConversationPanel]，胶囊态见 [AssistantCapsuleOverlay]。
 *
 * @param onInputFocusChange 输入框焦点变化回调；系统悬浮窗宿主借此切换窗口可获焦标志，
 *        让用户能在桌面上直接打字，其余宿主可忽略。
 */
@Composable
fun AssistantSurface(
    state: AssistantSessionState,
    mode: AssistantSurfaceMode,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onMicToggle: () -> Unit = {},
    onTextSubmit: (String) -> Unit = {},
    recording: Boolean = false,
    audioLevel: Float = 0f,
    onInputFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 用户下滑收回面板后的本地覆盖；新一轮指令就绪（shouldShowConversation 翻转）时自动恢复展开。
    var manuallyCollapsed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.shouldShowConversation) {
        if (state.shouldShowConversation) manuallyCollapsed = false
    }
    val expanded = state.shouldShowConversation && !manuallyCollapsed
    // 收回胶囊或关闭后通知宿主释放输入焦点（悬浮窗恢复不可获焦，避免拦截返回键/键盘）。
    LaunchedEffect(expanded) {
        if (!expanded) onInputFocusChange(false)
    }

    if (expanded) {
        AssistantConversationPanel(
            state = state,
            onDismiss = onDismiss,
            onOpenChat = onOpenChat,
            onMicToggle = onMicToggle,
            onTextSubmit = onTextSubmit,
            recording = recording,
            audioLevel = audioLevel,
            onInputFocusChange = onInputFocusChange,
            onCollapse = { manuallyCollapsed = true },
            overlayIme = mode == AssistantSurfaceMode.OVERLAY,
            modifier = modifier,
        )
    } else {
        AssistantCapsuleOverlay(
            state = state,
            onDismiss = onDismiss,
            onMicToggle = onMicToggle,
            onTextSubmit = onTextSubmit,
            recording = recording,
            audioLevel = audioLevel,
            onInputFocusChange = onInputFocusChange,
            overlayIme = mode == AssistantSurfaceMode.OVERLAY,
            modifier = modifier,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssistantSurfaceCapsulePreview() {
    AsssistantaiTheme {
        AssistantSurface(
            state = AssistantSessionState(),
            mode = AssistantSurfaceMode.SHEET,
            onDismiss = {},
            onOpenChat = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssistantSurfacePanelPreview() {
    AsssistantaiTheme {
        AssistantSurface(
            state = AssistantSessionState(
                messages = listOf(
                    AssistantMessage(1, AssistantMessageAuthor.USER, "现在几点了？"),
                    AssistantMessage(2, AssistantMessageAuthor.ASSISTANT, "现在是下午四点二十。"),
                ),
            ),
            mode = AssistantSurfaceMode.SHEET,
            onDismiss = {},
            onOpenChat = {},
        )
    }
}
