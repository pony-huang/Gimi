package github.ponyhuang.gimi.feature.chat

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 聊天界面 Scaffold：
 * - 顶部浮动操作：抽屉按钮（[onOpenDrawer]）+ 新建对话（[onNewConversation]）+ 设置
 * - 底部输入卡片：文本、附件、模型选择与语音/发送操作。
 * - LazyColumn：消息流 + 流式输入自动跟随滚动 + 用户离开底部时显示「回到最新」FAB
 * - ChatInputBar：草稿由输入组件管理，发送按钮在流式期间被禁用。
 *
 * ## 滚动 / FAB 自洽
 * 列表滚动状态、流式跟随信号、FAB 可见性、`didInitialScroll` 首次守卫都内化在本 Composable 内，
 * 宿主不直接持有 `LazyListState`，避免不相关的状态变化触发整屏重组。
 *
 * ## [isAgentRunning] 的两个用途
 * - **"新建对话"按钮** — Agent turn 进行期间保留旧 runner 不动；[onNewConversation] 由宿主自行实现
 *   "recreate runner + 重置会话"，但调用时机由本 Composable 决定。
 * - **"发送"按钮** — Agent turn 进行期间保持禁用，实际发送由 [ChatComposer] 触发。
 *
 * ## 宿主契约
 * 宿主负责：草稿随 session 重置（`remember(currentSessionId)`）、抽屉开合、session 切换 /
 * 删除、`viewModel.send` 实际调用、模型服务切换时的 runner 重建。本 Composable 不持有这些
 * 副作用。
 */
@SuppressLint("FrequentlyChangingValue")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ChatScaffold(
    state: ChatUiState,
    partChannelProvider: (String) -> ReceiveChannel<String>?,
    onSend: (String, List<DraftAttachment>) -> Boolean,
    onStop: () -> Unit,
    onTranscribeVoice: suspend (ByteArray) -> String,
    onToggleSpeechPlayback: (String, String) -> Unit,
    onOpenDocument: (github.ponyhuang.gimi.domain.conversation.model.FileAttachment) -> Unit,
    onOpenLocalFile: (github.ponyhuang.gimi.domain.conversation.model.LocalFileReference) -> Unit,
    onShowAllLocalFiles: (responseId: String) -> Unit,
    onToolConfirmation: (Boolean) -> Unit,
    onToolConfirmationAlwaysAllow: () -> Unit,
    onFullAccessChange: (Boolean) -> Unit,
    onSelectModel: (github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection) -> Unit,
    onModelSwitchBlocked: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onConfigureModels: () -> Unit,
    onNewConversation: () -> Unit,
    onToolAccessModeChange: (ToolAccessMode) -> Unit,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    onMcpServerEnabledChange: (String, Boolean) -> Unit,
    onOfficialToolOpened: (String) -> Unit,
    onOfficialToolFunctionEnabledChange: (String, String, Boolean) -> Unit,
    onOfficialToolFunctionsRetry: (String) -> Unit,
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
    recommendations: List<AgentRecommendation> = emptyList(),
    onRecommendationClick: (String) -> Unit = {},
) {

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val messages = state.messages
    val showToolActivity = state.showToolActivity
    val isSpeechRecognitionAvailable = state.isSpeechRecognitionAvailable
    val visibleMessages = remember(messages, showToolActivity) {
        messages.filter { message -> message.isVisibleInChat(showToolActivity) }
            .foldToolResponses()
    }
    val isAgentRunning = state.isAgentRunning
    val speechPlaybackState = state.speechPlaybackState
    val pendingToolConfirmation = state.pendingToolConfirmation
    val attachmentCapabilities = remember(
        state.availableLLMModelSettings,
        state.currentModelSelection,
    ) {
        val selection = state.currentModelSelection
        state.availableLLMModelSettings
            .firstOrNull { it.id == selection?.serviceId }
            ?.groups
            ?.firstOrNull { it.id == selection?.groupId }
            ?.models
            ?.firstOrNull { it.id == selection?.modelId }
            ?.capabilities
            ?: MultimodalCapabilities()
    }
    val awaitingConfirmationToolNames = remember(state.pendingToolConfirmations) {
        state.pendingToolConfirmations.mapTo(HashSet()) { it.toolName }
    }
    // 只要用户仍停留在底部，就让流式内容增长持续跟随；用户向上浏览历史时则停止抢占滚动。
    var shouldFollowLatest by remember { mutableStateOf(true) }
    var isModelPickerVisible by remember { mutableStateOf(false) }
    var isComposerExpanded by remember { mutableStateOf(false) }
    val latestItemIndex by rememberUpdatedState(visibleMessages.size)
    val showsRecommendations = visibleMessages.isEmpty() && recommendations.isNotEmpty()
    // 推荐卡片跟着胶囊一起收放，聚焦放大后两者左右边缘刚好对齐。
    val recommendationHorizontalInset by animateDpAsState(
        targetValue = if (isComposerExpanded) 0.dp else ComposerCollapsedHorizontalInset,
        animationSpec = ComposerInsetAnimationSpec,
        label = "recommendationHorizontalInset",
    )

    LaunchedEffect(visibleMessages.size, pendingToolConfirmation?.confirmationCallId) {
        if (state.getCurrentUserMessage() != null) {
            delay(100.milliseconds)
            listState.animateScrollToItem(visibleMessages.size)
        }
    }

    // `ChatTextContent` 从 channel 接收增量后只会改变气泡尺寸，不会改变 messages 引用。
    // 监听可见项的布局边界，确保每次气泡增长到新行时都能把底部锚点带回视口。
    LaunchedEffect(listState, isAgentRunning) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { item ->
                item.index to (item.offset + item.size)
            }
        }.collect {
            if (isAgentRunning && shouldFollowLatest) {
                listState.scrollToItem(latestItemIndex)
            }
        }
    }

    // 仅在一次实际滚动结束时更新“跟随最新”意图，避免流式内容自身增长时误判为用户离开底部。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling) {
                    shouldFollowLatest = !listState.canScrollForward
                }
            }
    }

    val statusBarProtectionColor = MaterialTheme.colorScheme.surface
    val statusBarProtectionBrush = remember(statusBarProtectionColor) {
        Brush.verticalGradient(
            colors = listOf(
                statusBarProtectionColor.copy(alpha = 0.72f),
                statusBarProtectionColor.copy(alpha = 0.36f),
                Color.Transparent,
            ),
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 与底部 composer 的 fadeBrush 对称：消息滚到悬浮顶栏后方时渐隐，
            // 避免文字从透明按钮之间透出（只保护状态栏不够，按钮行本身也是透明的）。
            val topBarBackground = MaterialTheme.colorScheme.background
            val topFadeBrush = remember(topBarBackground) {
                Brush.verticalGradient(
                    // 顶栏按钮行占区域上半部，需要接近不透明才能压住后方文字；
                    // 只在底部边缘渐隐到透明，保持"悬浮"观感。
                    colorStops = arrayOf(
                        0.0f to topBarBackground,
                        0.55f to topBarBackground.copy(alpha = 0.95f),
                        0.8f to topBarBackground.copy(alpha = 0.5f),
                        1.0f to Color.Transparent,
                    ),
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(topFadeBrush),
                )
                ChatHeaderActions(
                    onOpenDrawer = onOpenDrawer,
                    onNewConversation = onNewConversation,
                    onOpenSettings = onOpenSettings,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(statusBarProtectionBrush),
                )
            }
        },
        bottomBar = {
            val background = MaterialTheme.colorScheme.background
            val fadeBrush = remember(background) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        background.copy(alpha = 0.8f),
                        background.copy(alpha = 0.95f),
                        background,
                    ),
                )
            }
            key(state.sessionId) {
                ChatComposer(
                    modifier = Modifier.background(fadeBrush),
                    onSendClick = { data ->
                        onSend(data.text, data.attachments)
                    },
                    onStopClick = onStop,
                    isGenerating = isAgentRunning,
                    isVoiceInputAvailable = isSpeechRecognitionAvailable,
                    onTranscribeVoice = onTranscribeVoice,
                    addToChatState = ChatAddToChatState(
                        configuration = state.toolConfiguration,
                        mcpServers = state.availableMcpServers,
                        officialTools = state.officialToolDescriptors,
                        isMutationBlocked = state.isAgentRunning,
                        fullAccess = state.fullAccess,
                        errorMessage = if (state.hasToolConfigurationError) {
                            stringResource(R.string.chat_session_tool_save_failed)
                        } else {
                            null
                        },
                    ),
                    attachmentCapabilities = attachmentCapabilities,
                    onToolAccessModeChange = onToolAccessModeChange,
                    onReasoningEffortChange = onReasoningEffortChange,
                    onMcpServerEnabledChange = onMcpServerEnabledChange,
                    onFullAccessChange = onFullAccessChange,
                    onOfficialToolOpened = onOfficialToolOpened,
                    onOfficialToolFunctionEnabledChange = onOfficialToolFunctionEnabledChange,
                    onOfficialToolFunctionsRetry = onOfficialToolFunctionsRetry,
                    sharedMediaUris = sharedMediaUris,
                    onSharedMediaConsumed = onSharedMediaConsumed,
                    retainExpanded = isModelPickerVisible,
                    onExpandedChange = { isComposerExpanded = it },
                    modelSelectorContent = {
                        ModelTitleAndPicker(
                            services = state.availableLLMModelSettings,
                            currentSelection = state.currentModelSelection,
                            loadState = state.modelCatalogLoadState,
                            isAgentRunning = isAgentRunning,
                            onConfigureModels = onConfigureModels,
                            onSelectModel = onSelectModel,
                            onModelSwitchBlocked = onModelSwitchBlocked,
                            onPickerVisibilityChange = { isModelPickerVisible = it },
                        )
                    },
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                // 推荐面板被键盘挤到可滚动时也会触发 canScrollForward，
                // 但空会话里没有"最新消息"可回，这时不该冒出这个按钮。
                visible = listState.canScrollForward && !showsRecommendations,
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        shouldFollowLatest = true
                        scope.launch {
                            listState.animateScrollToItem(visibleMessages.size)
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.chat_fab_back_to_latest),
                    )
                }
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val listContentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            // 推荐面板要紧贴胶囊，省掉消息流才需要的那点呼吸位。
            bottom = innerPadding.calculateBottomPadding() +
                if (showsRecommendations) 0.dp else 8.dp,
        )

        AnimatedContent(
            targetState = state.isInitializing,
        ) { isInitializing ->
            if (isInitializing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 消息列表
                LazyColumn(
                    state = listState,
                    // 推荐面板从底部往上堆叠，紧贴胶囊；内容溢出时退化成普通的顶部对齐滚动。
                    verticalArrangement = if (showsRecommendations) {
                        Arrangement.spacedBy(8.dp, Alignment.Bottom)
                    } else {
                        Arrangement.spacedBy(8.dp)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(innerPadding),
                    contentPadding = listContentPadding,
                ) {
                    if (showsRecommendations) {
                        item(
                            key = "chat-recommendations",
                            contentType = "recommendations",
                        ) {
                            RecommendationPanel(
                                recommendations = recommendations,
                                onRecommendationClick = onRecommendationClick,
                                modifier = Modifier.padding(
                                    horizontal = recommendationHorizontalInset,
                                ),
                            )
                        }
                    }
                    items(
                        items = visibleMessages,
                        key = { it.id },
                        contentType = { msg ->
                            when {
                                msg.error != null -> "error"
                                msg.role == MessageRole.User -> "user"
                                msg.partial -> "assistant_streaming"
                                else -> "assistant_complete"
                            }
                        },
                    ) { msg ->
                        MessageRow(
                            message = msg,
                            partChannelProvider = partChannelProvider,
                            showToolActivity = showToolActivity,
                            isAgentRunning = isAgentRunning,
                            rejectedToolNames = state.rejectedToolNames,
                            awaitingConfirmationToolNames = awaitingConfirmationToolNames,
                            speechPlaybackState = speechPlaybackState,
                            onToggleSpeechPlayback = onToggleSpeechPlayback,
                            onOpenDocument = onOpenDocument,
                            onOpenLocalFile = onOpenLocalFile,
                            onShowAllLocalFiles = onShowAllLocalFiles,
                        )
                    }
                    pendingToolConfirmation?.let { request ->
                        item(
                            key = "tool-confirmation-${request.confirmationCallId}",
                            contentType = "tool_confirmation",
                        ) {
                            ToolConfirmationCard(
                                request = request,
                                onConfirm = { onToolConfirmation(true) },
                                onReject = { onToolConfirmation(false) },
                                onAlwaysAllow = onToolConfirmationAlwaysAllow,
                            )
                        }
                    }
                    item(
                        key = "chat-bottom-anchor",
                        contentType = "bottom_anchor",
                    ) {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

        }

    }
}

@Composable
private fun ToolConfirmationCard(
    request: PendingToolConfirmation,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onAlwaysAllow: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.chat_tool_confirmation_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                toolDisplayName(request.toolName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (request.description.isBlank()) {
                    stringResource(R.string.chat_tool_unknown_description)
                } else {
                    request.description
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (request.arguments.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                ) {
                    Text(
                        request.arguments,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 「总是允许」是带持久副作用的动作，放在行首与一次性动作拉开距离；
                // 拒绝是次要动作：弱化颜色，把视觉重量让给主操作「允许」。
                TextButton(
                    onClick = onAlwaysAllow,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(
                        stringResource(R.string.chat_action_always_allow),
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onReject,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Text(stringResource(R.string.chat_action_reject)) }
                Button(onClick = onConfirm) { Text(stringResource(R.string.chat_action_allow)) }
            }
        }
    }
}

/** Lightweight floating chat actions that keep model and settings controls out of the header. */
@Composable
internal fun ChatHeaderActions(
    onOpenDrawer: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onOpenDrawer,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.chat_open_drawer),
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNewConversation,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = stringResource(R.string.chat_new_conversation),
                    )
                }
                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_header_settings"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.chat_settings),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * 单条消息的派发：错误消息走 `ErrorBubble`，其它走 `MessageBubble`。
 *
 * `partChannelProvider` 用于让 `MessageBubble` 订阅 reducer 暴露的文本增量 channel，
 * 从而把每一段 partial 文本作为 chunk 推给 `StreamingMarkdownState.append(...)`，
 * 实现逐字符级流式渲染。
 */
@Composable
private fun MessageRow(
    message: Message,
    partChannelProvider: (partId: String) -> ReceiveChannel<String>?,
    showToolActivity: Boolean,
    isAgentRunning: Boolean,
    rejectedToolNames: Set<String>,
    awaitingConfirmationToolNames: Set<String>,
    speechPlaybackState: github.ponyhuang.gimi.domain.speech.model.SpeechPlaybackState,
    onToggleSpeechPlayback: (messageId: String, text: String) -> Unit,
    onOpenDocument: (github.ponyhuang.gimi.domain.conversation.model.FileAttachment) -> Unit,
    onOpenLocalFile: (github.ponyhuang.gimi.domain.conversation.model.LocalFileReference) -> Unit,
    onShowAllLocalFiles: (responseId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (message.error != null) {
        ErrorBubble(message = message, modifier = modifier)
    } else {
        MessageBubble(
            message = message,
            partChannelProvider = partChannelProvider,
            showToolActivity = showToolActivity,
            isAgentRunning = isAgentRunning,
            rejectedToolNames = rejectedToolNames,
            awaitingConfirmationToolNames = awaitingConfirmationToolNames,
            speechPlaybackState = speechPlaybackState,
            onToggleSpeechPlayback = onToggleSpeechPlayback,
            onOpenDocument = onOpenDocument,
            onOpenLocalFile = onOpenLocalFile,
            onShowAllLocalFiles = onShowAllLocalFiles,
            modifier = modifier
        )
    }
}
