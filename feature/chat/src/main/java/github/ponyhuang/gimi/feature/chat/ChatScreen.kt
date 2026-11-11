package github.ponyhuang.gimi.feature.chat

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import github.ponyhuang.gimi.ui.theme.GimiTheme
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 聊天界面 Scaffold：
 * - 顶部浮动操作：左胶囊（[onOpenDrawer] + 语音通话）+ 右胶囊（[onNewConversation] + 自动播报）
 * - 底部输入卡片：文本、附件、模型选择与语音/发送操作。
 * - LazyColumn：消息流 + 流式输入自动跟随滚动 + 用户离开底部时显示「回到最新」FAB
 * - ChatInputBar：草稿由聊天页面共享保存，发送按钮在流式期间被禁用。
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
 * 本页面共享保存未发送草稿，切换或新建会话时继续编辑；宿主负责抽屉开合、session 切换 /
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
    onSend: (String, List<DraftAttachment>, (ChatSubmissionResult) -> Unit) -> Unit,
    onStop: () -> Unit,
    onTranscribeVoice: VoiceTranscriptionRequest,
    onCancelVoiceTranscription: () -> Unit = {},
    onToggleSpeechPlayback: (String, String) -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onOpenVoice: () -> Unit = {},
    onOpenDocument: (github.ponyhuang.gimi.domain.conversation.model.FileAttachment) -> Unit,
    onToggleTimeline: (groupId: String) -> Unit = {},
    onToolConfirmation: (Boolean) -> Unit,
    onToolConfirmationAlwaysAllow: () -> Unit,
    onRespondToInputRequest: (callId: String, value: String) -> Unit = { _, _ -> },
    onFullAccessChange: (Boolean) -> Unit,
    onSelectModel: (github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection) -> Unit,
    onModelSwitchBlocked: () -> Unit,
    onOpenDrawer: () -> Unit,
    onConfigureModels: () -> Unit,
    onNewConversation: () -> Unit,
    onReasoningEffortChange: (ReasoningEffort) -> Unit,
    onThinkingEnabledChange: (Boolean) -> Unit = {},
    onMcpServerEnabledChange: (String, Boolean) -> Unit,
    onOfficialToolOpened: (String) -> Unit,
    onOfficialToolFunctionEnabledChange: (String, String, Boolean) -> Unit,
    onOfficialToolFunctionsRetry: (String) -> Unit,
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
    recommendations: List<AgentRecommendation> = emptyList(),
    onRecommendationClick: (String) -> Unit = {},
    onRetryFailedTurn: () -> Unit = {},
    onRetryA2ui: (String) -> Unit = {},
) {

    val listState = rememberLazyListState()
    // 草稿属于输入槽位而非会话，必须放在 session key 与挂起操作面板之外保存。
    var composerDraft by rememberSaveable(stateSaver = MessageData.Saver) {
        mutableStateOf(MessageData())
    }
    val scope = rememberCoroutineScope()
    val listItems = state.listItems
    val isSpeechRecognitionAvailable = state.isSpeechRecognitionAvailable
    val isAgentRunning = state.isAgentRunning
    val runStatus = state.currentRunStatus()
    val speechPlaybackState = state.speechPlaybackState
    val pendingToolConfirmation = state.pendingToolConfirmation
    val pendingInputRequest = state.pendingInputRequest
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
            ?.multimodal
            ?: MultimodalCapabilities()
    }
    // 只要用户仍停留在底部，就让流式内容增长持续跟随；用户向上浏览历史时则停止抢占滚动。
    var shouldFollowLatest by rememberSaveable(state.sessionId) { mutableStateOf(true) }
    var didInitialScroll by rememberSaveable(state.sessionId) { mutableStateOf(false) }
    val latestUserMessageId = (listItems.lastOrNull { it is ChatListItem.UserMessage }
        as? ChatListItem.UserMessage)?.message?.id
    var previousUserMessageId by rememberSaveable(state.sessionId) {
        mutableStateOf(latestUserMessageId)
    }
    var handledScrollRequest by rememberSaveable(state.sessionId) {
        mutableStateOf(state.scrollToLatestRequest)
    }
    var isModelPickerVisible by remember { mutableStateOf(false) }
    val renderedItemCount = remember(listItems, runStatus) {
        listItems.sumOf { item ->
            when (item) {
                is ChatListItem.UserMessage -> 1
                is ChatListItem.AssistantTurn -> item.timeline.displaySegments(runStatus.takeIf { item.timeline.isRunning }).size
            }
        }
    }
    val latestItemIndex by rememberUpdatedState(renderedItemCount)
    val showsRecommendations = listItems.isEmpty() && recommendations.isNotEmpty()
    // 推荐卡片与胶囊边缘对齐（大胶囊样式下左右无内缩）。
    val recommendationHorizontalInset = ComposerCollapsedHorizontalInset

    LaunchedEffect(
        state.sessionId,
        state.isInitializing,
        renderedItemCount,
        latestUserMessageId,
        state.scrollToLatestRequest,
        pendingToolConfirmation?.confirmationCallId,
        pendingInputRequest?.callId,
    ) {
        if (state.isInitializing || state.sessionId.isBlank()) return@LaunchedEffect
        val shouldScroll = shouldScrollChatToLatest(
            isInitialPositioning = !didInitialScroll,
            explicitRequestChanged = state.scrollToLatestRequest != handledScrollRequest,
            latestUserMessageChanged = latestUserMessageId != previousUserMessageId,
            followsLatest = shouldFollowLatest,
        )
        didInitialScroll = true
        handledScrollRequest = state.scrollToLatestRequest
        previousUserMessageId = latestUserMessageId
        if (renderedItemCount > 0 && shouldScroll) {
            shouldFollowLatest = true
            delay(100.milliseconds)
            // 等待布局期间用户可能已开始浏览历史，此时取消自动定位。
            if (shouldFollowLatest) listState.animateScrollToItem(renderedItemCount)
        }
    }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            // 手指开始拖动就交还滚动控制权，不能等拖动结束才停止流式跟随。
            if (interaction is DragInteraction.Start) shouldFollowLatest = false
        }
    }

    // `ChatTextContent` 从 channel 接收增量后只会改变气泡尺寸，不会改变 messages 引用。
    // 监听可见项的布局边界，确保每次气泡增长到新行时都能把底部锚点带回视口。
    LaunchedEffect(listState, isAgentRunning, state.sessionId) {
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
    LaunchedEffect(listState, state.sessionId) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (wasScrolling && !isScrolling) {
                    shouldFollowLatest = !listState.canScrollForward
                }
                wasScrolling = isScrolling
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
            // 与底部输入区对称：正文滚到悬浮顶栏后方时柔和淡出，状态栏保留额外遮罩。
            val background = MaterialTheme.colorScheme.background
            val fadeBrush = remember(background) {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to background,
                        0.55f to background.copy(alpha = 0.9f),
                        0.8f to background.copy(alpha = 0.4f),
                        1.0f to Color.Transparent,
                    ),
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(fadeBrush),
                )
                ChatHeaderActions(
                    autoSpeakEnabled = state.autoSpeakEnabled,
                    onOpenDrawer = onOpenDrawer,
                    onNewConversation = onNewConversation,
                    onToggleAutoSpeak = onToggleAutoSpeak,
                    onOpenVoice = onOpenVoice,
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
            // 渐隐只用固定高度，避免输入区变高时把正文过早淡没。
            val fadeHeightPx = with(LocalDensity.current) { 28.dp.toPx() }
            val fadeBrush = remember(background, fadeHeightPx) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        background.copy(alpha = 0.8f),
                        background,
                    ),
                    startY = 0f,
                    endY = fadeHeightPx,
                )
            }
            Column(modifier = Modifier.background(fadeBrush)) {
                key(state.sessionId) {
                    // Agent 挂起等待答复（授权 / 输入 / 选项）时，操作面板**取代**输入
                    // 胶囊本体 —— 两者互斥共用同一槽位，答复后胶囊恢复；期间普通发送
                    // 本就被 ViewModel 锁定，胶囊没有可用操作。
                    AnimatedContent(
                        targetState = state.pendingComposerAction,
                        contentKey = { it?.key() ?: "composer" },
                        label = "pendingComposerAction",
                    ) { action ->
                        when (action) {
                            is PendingComposerAction.Confirmation -> PendingConfirmationPanel(
                                request = action.request,
                                onConfirm = { onToolConfirmation(true) },
                                onReject = { onToolConfirmation(false) },
                                onAlwaysAllow = onToolConfirmationAlwaysAllow,
                            )

                            is PendingComposerAction.Choice -> PendingChoicePanel(
                                request = action.request,
                                onRespond = { value ->
                                    onRespondToInputRequest(action.request.callId, value)
                                },
                            )

                            is PendingComposerAction.TextInput -> PendingInputPanel(
                                request = action.request,
                                isVoiceInputAvailable = isSpeechRecognitionAvailable,
                                onTranscribeVoice = onTranscribeVoice,
                                voiceTranscriptionMode = state.voiceTranscriptionMode,
                                onCancelVoiceTranscription = onCancelVoiceTranscription,
                                onRespond = { value ->
                                    onRespondToInputRequest(action.request.callId, value)
                                },
                            )

                            null -> ChatComposer(
                                modifier = Modifier,
                                messageData = composerDraft,
                                onMessageDataChange = { composerDraft = it },
                                onSendClick = { data, onResult ->
                                    onSend(data.text, data.attachments) { result ->
                                        // 回执可能在切换会话后到达；只消费提交快照，不覆盖后续编辑。
                                        if (result == ChatSubmissionResult.ACCEPTED) {
                                            composerDraft = consumeAcceptedDraft(composerDraft, data)
                                        }
                                        onResult(result)
                                    }
                                },
                                onStopClick = onStop,
                                isGenerating = isAgentRunning,
                                isVoiceInputAvailable = isSpeechRecognitionAvailable,
                                onTranscribeVoice = onTranscribeVoice,
                                voiceTranscriptionMode = state.voiceTranscriptionMode,
                                onCancelVoiceTranscription = onCancelVoiceTranscription,
                                voiceInputContextKey = state.sessionId,
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
                                onMcpServerEnabledChange = onMcpServerEnabledChange,
                                onFullAccessChange = onFullAccessChange,
                                onOfficialToolOpened = onOfficialToolOpened,
                                onOfficialToolFunctionEnabledChange = onOfficialToolFunctionEnabledChange,
                                onOfficialToolFunctionsRetry = onOfficialToolFunctionsRetry,
                                sharedMediaUris = sharedMediaUris,
                                onSharedMediaConsumed = onSharedMediaConsumed,
                                retainExpanded = isModelPickerVisible,
                                modelSelectorContent = {
                                    ModelTitleAndPicker(
                                        services = state.availableLLMModelSettings,
                                        currentSelection = state.currentModelSelection,
                                        loadState = state.modelCatalogLoadState,
                                        isAgentRunning = isAgentRunning,
                                        onConfigureModels = onConfigureModels,
                                        onSelectModel = onSelectModel,
                                        onModelSwitchBlocked = onModelSwitchBlocked,
                                        onPickerVisibilityChange = {
                                            isModelPickerVisible = it
                                        },
                                        // 思考强度入口迁入模型抽屉二级页，由会话配置驱动。
                                        toolConfiguration = state.toolConfiguration,
                                        reasoningEnabled = !isAgentRunning,
                                        onReasoningEffortChange = onReasoningEffortChange,
                                        onThinkingEnabledChange = onThinkingEnabledChange,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                // 推荐面板被键盘挤到可滚动时也会触发 canScrollForward，
                // 但空会话里没有"最新消息"可回，这时不该冒出这个按钮。
                visible = listState.canScrollForward && !showsRecommendations,
            ) {
                ChatScrollToLatestButton(
                    onClick = {
                        shouldFollowLatest = true
                        scope.launch {
                            listState.animateScrollToItem(renderedItemCount)
                        }
                    },
                )
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
                    listItems.forEach { listItem ->
                        when (listItem) {
                            is ChatListItem.UserMessage -> item(
                                key = "user:${state.sessionId}:${listItem.message.id}",
                                contentType = "user",
                            ) {
                                MessageRow(
                                    message = listItem.message,
                                    a2uiSurfaces = state.a2uiSurfaces.filterKeys { state.a2uiOwners[it] == listItem.message.id }.values.toList(),
                                    partChannelProvider = partChannelProvider,
                                    speechPlaybackState = speechPlaybackState,
                                    onToggleSpeechPlayback = onToggleSpeechPlayback,
                                    onOpenDocument = onOpenDocument,
                                )
                            }
                            is ChatListItem.AssistantTurn -> {
                                val timeline = listItem.timeline
                                val turnStatus = runStatus.takeIf { timeline.isRunning }
                                val segments = timeline.displaySegments(turnStatus)
                                val latestActivityId = (segments.lastOrNull() as? TurnSegment.Activity)?.id
                                val lastActivityId = segments.filterIsInstance<TurnSegment.Activity>().lastOrNull()?.id
                                segments.forEach { segment ->
                                    when (segment) {
                                        is TurnSegment.Subtasks -> item(
                                            key = "subtasks:${state.sessionId}:${segment.id}",
                                            contentType = "subtasks",
                                        ) {
                                            SubtaskBatchPanel(
                                                batch = segment.batch,
                                                expanded = segment.id in state.expandedActivityGroupIds,
                                                onToggle = { onToggleTimeline(segment.id) },
                                            )
                                        }
                                        is TurnSegment.Answer -> {
                                            val message = segment.message
                                            item(
                                                key = "answer:${state.sessionId}:${timeline.turnId}:${message.id}",
                                                contentType = if (message.error != null) {
                                                    "error"
                                                } else if (message.partial) {
                                                    "answer_streaming"
                                                } else {
                                                    "answer"
                                                },
                                            ) {
                                                MessageRow(
                                                    message = message,
                                                    onRetryA2ui = onRetryA2ui,
                                                    a2uiRetryEnabled = !state.isAgentRunning,
                                                    a2uiSurfaces = state.a2uiSurfaces.filterKeys { state.a2uiOwners[it] == message.id }.values.toList(),
                                                    partChannelProvider = partChannelProvider,
                                                    speechPlaybackState = speechPlaybackState,
                                                    onToggleSpeechPlayback = onToggleSpeechPlayback,
                                                    onOpenDocument = onOpenDocument,
                                                )
                                            }
                                        }
                                        is TurnSegment.Activity -> item(
                                            key = "activity:${state.sessionId}:${segment.id}",
                                            contentType = "activity",
                                        ) {
                                            TurnActivityGroupPanel(
                                                segment = segment,
                                                expanded = segment.id in state.expandedActivityGroupIds,
                                                onToggle = { onToggleTimeline(segment.id) },
                                                runStatus = turnStatus.takeIf { segment.id == latestActivityId },
                                                isRunning = timeline.isRunning,
                                                startedAtMillis = timeline.startedAtMillis,
                                                endedAtMillis = timeline.endedAtMillis,
                                                showElapsed = segment.id == lastActivityId,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    state.failedTurn?.let { failedTurn ->
                        if (!state.isAgentRunning) {
                            item(
                                key = "failed-turn-actions:${state.sessionId}",
                                contentType = "failed_turn_actions",
                            ) {
                                FailedTurnActions(
                                    onRetry = onRetryFailedTurn,
                                )
                            }
                        }
                    }
                    item(
                        key = "chat-bottom-anchor:${state.sessionId}",
                        contentType = "bottom_anchor",
                    ) {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

        }

    }
}

/** 回到底部的悬浮入口，复用聊天顶栏与输入胶囊的中性容器样式。 */
@Composable
private fun ChatScrollToLatestButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = chatCapsuleColor(),
            border = chatCapsuleBorder(),
        ) {}
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("chat_scroll_to_latest"),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.chat_fab_back_to_latest),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 顶栏浮动操作：左胶囊（抽屉｜语音通话）+ 右胶囊（新建会话｜自动播报）。
 * 设置与版本更新提醒统一由侧边抽屉承载，顶栏不再重复放入口。
 */
@Composable
internal fun ChatHeaderActions(
    autoSpeakEnabled: Boolean,
    onOpenDrawer: () -> Unit,
    onNewConversation: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onOpenVoice: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 与输入胶囊共用容器色和描边，浅色主题下靠描边勾出边界；各自保留 48dp 点击区。
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.matchParentSize().padding(vertical = 2.dp),
                shape = CircleShape,
                color = chatCapsuleColor(),
                border = chatCapsuleBorder(),
            ) {}
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.chat_open_drawer),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChatHeaderDivider()
                // 语音通话与「新建会话」对换：常用通话留在抽屉组，右侧胶囊统一为「新会话｜播报」。
                IconButton(
                    onClick = onOpenVoice,
                    modifier = Modifier.size(48.dp)
                        .testTag("chat_header_voice_call"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = stringResource(R.string.voice_open_call),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.matchParentSize().padding(vertical = 2.dp),
                shape = CircleShape,
                color = chatCapsuleColor(),
                border = chatCapsuleBorder(),
            ) {}
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 新建会话：与语音页右胶囊使用同一枚列表加号图标，两页入口视觉一致。
                IconButton(
                    onClick = onNewConversation,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = stringResource(R.string.chat_new_conversation),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChatHeaderDivider()
                // 自动语音播报全局开关：开启态用 primary 高亮，关闭态回到普通前景色。
                IconButton(
                    onClick = onToggleAutoSpeak,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_header_auto_speak"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(
                            if (autoSpeakEnabled) {
                                R.string.chat_auto_speak_on
                            } else {
                                R.string.chat_auto_speak_off
                            },
                        ),
                        modifier = Modifier.size(24.dp),
                        tint = if (autoSpeakEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * 顶栏胶囊内按钮间的竖分隔线：1dp 细线，高度约两倍图标，颜色与胶囊描边同源弱化。
 * 语音页顶栏胶囊（新会话｜通话设备）复用同一分隔样式。
 */
@Composable
internal fun ChatHeaderDivider() {
    Box(
        modifier = Modifier
            .padding(vertical = 13.dp)
            .size(width = 1.dp, height = 22.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    )
}

/**
 * 失败轮次的重试入口，使用居右的胶囊按钮。
 */
@Composable
private fun FailedTurnActions(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .testTag("failed_turn_retry")
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.chat_failed_turn_retry),
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
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
    a2uiSurfaces: List<androidx.a2ui.model.processor.A2uiSurfaceModel> = emptyList(),
    a2uiRetryEnabled: Boolean = false,
    onRetryA2ui: (String) -> Unit = {},
    partChannelProvider: (partId: String) -> ReceiveChannel<String>?,
    speechPlaybackState: github.ponyhuang.gimi.domain.voice.model.SpeechPlaybackState,
    onToggleSpeechPlayback: (messageId: String, text: String) -> Unit,
    onOpenDocument: (github.ponyhuang.gimi.domain.conversation.model.FileAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    if (message.role == MessageRole.Assistant && message.textParts.none { !it.thought && it.text.isNotBlank() } &&
        message.fileAttachments.isEmpty() && message.functionResponses.any { it.a2uiDocument != null } &&
        a2uiSurfaces.isEmpty() && message.functionResponses.none { it.a2uiFailed ||
            (it.a2uiTask != null && it.a2uiTask?.status != github.ponyhuang.gimi.domain.a2ui.A2uiGenerationStatus.COMPLETED) }) return
    if (message.error != null) {
        ErrorBubble(message = message, modifier = modifier)
    } else {
        MessageBubble(
            message = message,
            partChannelProvider = partChannelProvider,
            speechPlaybackState = speechPlaybackState,
            onToggleSpeechPlayback = onToggleSpeechPlayback,
            onOpenDocument = onOpenDocument,
            modifier = modifier,
            a2uiContent = {
                val tasks = message.functionResponses.mapNotNull { it.a2uiTask }
                if (a2uiSurfaces.isNotEmpty() || tasks.isNotEmpty() || message.functionResponses.any { it.a2uiFailed }) {
                    github.ponyhuang.gimi.feature.chat.a2ui.ChatA2uiContent(
                        surfaces = a2uiSurfaces,
                        failed = message.functionResponses.any { it.a2uiFailed },
                        tasks = tasks,
                        retryEnabled = a2uiRetryEnabled,
                        onRetry = onRetryA2ui,
                    )
                }
            },
        )
    }
}

private fun previewChatRecommendations(): List<AgentRecommendation> = listOf(
    AgentRecommendation(
        id = "rec-1",
        prompt = "帮我总结这篇文档的核心要点",
        category = RecommendationCategory.REASONING,
    ),
    AgentRecommendation(
        id = "rec-2",
        prompt = "分析这张截图里的报错信息",
        category = RecommendationCategory.VISION,
    ),
    AgentRecommendation(
        id = "rec-3",
        prompt = "把会议记录整理成待办事项清单",
        category = RecommendationCategory.PRODUCTIVITY,
    ),
)

@Preview(showBackground = true)
@Composable
private fun ChatScaffoldEmptyPreview() {
    GimiTheme {
        ChatScaffold(
            state = ChatUiState(sessionId = "preview-session"),
            partChannelProvider = { null },
            onSend = { _, _, reply -> reply(ChatSubmissionResult.ACCEPTED) },
            onStop = {},
            onTranscribeVoice = { _, _, _, _ -> },
            onToggleSpeechPlayback = { _, _ -> },
            onToggleAutoSpeak = {},
            onOpenDocument = {},
            onToolConfirmation = {},
            onToolConfirmationAlwaysAllow = {},
            onFullAccessChange = {},
            onSelectModel = {},
            onModelSwitchBlocked = {},
            onOpenDrawer = {},
            onConfigureModels = {},
            onNewConversation = {},
            onReasoningEffortChange = {},
            onMcpServerEnabledChange = { _, _ -> },
            onOfficialToolOpened = {},
            onOfficialToolFunctionEnabledChange = { _, _, _ -> },
            onOfficialToolFunctionsRetry = {},
            recommendations = previewChatRecommendations(),
        )
    }
}

@Preview(name = "聊天正文 · 浅色", showBackground = true, widthDp = 390)
@Preview(name = "聊天正文 · 深色", showBackground = true, widthDp = 390,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScaffoldWithMessagesPreview() {
    GimiTheme {
        val messages = listOf(
            Messages.fromUser("帮我查一下今天上海的天气"),
            Message(
                author = "DefaultAssistant",
                role = MessageRole.Assistant,
                textParts = listOf(TextPart(text = """
                    先确认两点：

                    1. **写什么主题：** 围绕这份 Excel 来写，还是产品推广、活动通知或小红书笔记？
                    2. **发在哪：** 公众号 / 小红书 / 朋友圈？

                    你可以粘贴关键内容，或重新上传文件，我再动笔。
                """.trimIndent())),
            ),
        )
        ChatScaffold(
            state = ChatUiState(
                sessionId = "preview-session",
                messages = messages,
                listItems = messages.toChatListItems(TimelineActivityState()),
            ),
            partChannelProvider = { null },
            onSend = { _, _, reply -> reply(ChatSubmissionResult.ACCEPTED) },
            onStop = {},
            onTranscribeVoice = { _, _, _, _ -> },
            onToggleSpeechPlayback = { _, _ -> },
            onToggleAutoSpeak = {},
            onOpenDocument = {},
            onToolConfirmation = {},
            onToolConfirmationAlwaysAllow = {},
            onFullAccessChange = {},
            onSelectModel = {},
            onModelSwitchBlocked = {},
            onOpenDrawer = {},
            onConfigureModels = {},
            onNewConversation = {},
            onReasoningEffortChange = {},
            onMcpServerEnabledChange = { _, _ -> },
            onOfficialToolOpened = {},
            onOfficialToolFunctionEnabledChange = { _, _, _ -> },
            onOfficialToolFunctionsRetry = {},
        )
    }
}

@Preview(name = "回到底部 · 浅色", showBackground = true, widthDp = 120, heightDp = 96)
@Preview(
    name = "回到底部 · 深色",
    showBackground = true,
    widthDp = 120,
    heightDp = 96,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ChatScrollToLatestButtonPreview() {
    GimiTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            ChatScrollToLatestButton(onClick = {})
        }
    }
}

@Preview(name = "顶部操作 · 浅色", showBackground = true)
@Preview(name = "顶部操作 · 深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatHeaderActionsPreview() {
    GimiTheme {
        ChatHeaderActions(
            autoSpeakEnabled = true,
            onOpenDrawer = {},
            onNewConversation = {},
            onToggleAutoSpeak = {},
        )
    }
}

@Preview(name = "顶部操作 · 语音入口 · 浅色", showBackground = true)
@Preview(name = "顶部操作 · 语音入口 · 深色", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatHeaderVoiceEntryPreview() {
    GimiTheme {
        ChatHeaderActions(
            autoSpeakEnabled = false,
            onOpenDrawer = {},
            onNewConversation = {},
            onToggleAutoSpeak = {},
            onOpenVoice = {},
        )
    }
}

/** 构造一个可重试的失败轮次，供失败态相关 Preview 使用。 */
private fun previewFailedTurn(): ChatTurn = ChatTurn(
    id = "preview-turn-1",
    sessionId = "preview-session",
    userMessage = Messages.fromUser("帮我查一下今天上海的天气"),
    messages = listOf(Messages.fromUser("帮我查一下今天上海的天气")),
    status = ChatTurnStatus.FAILED,
)

@Preview(showBackground = true)
@Composable
private fun FailedTurnActionsPreview() {
    GimiTheme {
        FailedTurnActions(
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageRowUserPreview() {
    GimiTheme {
        MessageRow(
            message = Messages.fromUser("帮我查一下今天上海的天气"),
            partChannelProvider = { null },
            speechPlaybackState = github.ponyhuang.gimi.domain.voice.model.SpeechPlaybackState(),
            onToggleSpeechPlayback = { _, _ -> },
            onOpenDocument = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageRowAssistantPreview() {
    GimiTheme {
        MessageRow(
            message = Message(
                author = "DefaultAssistant",
                role = MessageRole.Assistant,
                textParts = listOf(TextPart(text = "上海今天晴，28°C，适合出门。")),
            ),
            partChannelProvider = { null },
            speechPlaybackState = github.ponyhuang.gimi.domain.voice.model.SpeechPlaybackState(),
            onToggleSpeechPlayback = { _, _ -> },
            onOpenDocument = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScaffoldFailedTurnPreview() {
    GimiTheme {
        val messages = listOf(Messages.fromUser("帮我查一下今天上海的天气"))
        ChatScaffold(
            state = ChatUiState(
                sessionId = "preview-session",
                messages = messages,
                listItems = messages.toChatListItems(TimelineActivityState()),
                failedTurn = previewFailedTurn(),
            ),
            partChannelProvider = { null },
            onSend = { _, _, reply -> reply(ChatSubmissionResult.ACCEPTED) },
            onStop = {},
            onTranscribeVoice = { _, _, _, _ -> },
            onToggleSpeechPlayback = { _, _ -> },
            onToggleAutoSpeak = {},
            onOpenDocument = {},
            onToolConfirmation = {},
            onToolConfirmationAlwaysAllow = {},
            onFullAccessChange = {},
            onSelectModel = {},
            onModelSwitchBlocked = {},
            onOpenDrawer = {},
            onConfigureModels = {},
            onNewConversation = {},
            onReasoningEffortChange = {},
            onMcpServerEnabledChange = { _, _ -> },
            onOfficialToolOpened = {},
            onOfficialToolFunctionEnabledChange = { _, _, _ -> },
            onOfficialToolFunctionsRetry = {},
            recommendations = previewChatRecommendations(),
        )
    }
}
