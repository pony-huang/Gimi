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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import github.ponyhuang.gimi.feature.chat.realtime.RoleChipsRestingHeight
import github.ponyhuang.gimi.ui.theme.GimiTheme
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 聊天界面 Scaffold：
 * - 顶部浮动操作：左胶囊 [onOpenDrawer] + 中间模式分段 + 右侧 [onNewConversation]
 * - 底部输入卡片：文本、附件、模型选择与语音输入/发送操作。
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
    onOpenVoice: (() -> Unit)? = null,
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
    /** 上传失败的 chip 原地重试。 */
    onRetryAttachmentUpload: (github.ponyhuang.gimi.domain.conversation.model.DraftAttachment) -> Unit = {},
    /** 新导入的文档草稿触发厂商上传。 */
    onDocumentDraftImported: (github.ponyhuang.gimi.domain.conversation.model.DraftAttachment) -> Unit = {},
    /** 抽屉"上传的文件"回填请求流；把 file 引用草稿插入输入框。 */
    uploadedFiles: List<github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry> = emptyList(),
    onAttachUploadedFile: (github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry) -> Unit = {},
    onUploadedFileDelete: (github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry) -> Unit = {},
    /** 草稿附件变化后回收不再被引用的上传状态（取消在途任务）；由宿主转发给 ViewModel。 */
    onUploadedFilePrune: (Collection<String>) -> Unit = {},
    attachFileRequests: Flow<github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry> = kotlinx.coroutines.flow.emptyFlow(),
    chatMode: github.ponyhuang.gimi.domain.conversation.model.ChatMode? = null,
    onOpenWebSource: ((String) -> Unit)? = null,
    roleChips: (@Composable () -> Unit)? = null,
    onChatModeChange: (github.ponyhuang.gimi.domain.conversation.model.ChatMode) -> Unit = {},
    /** 顶栏由宿主在分页容器之上统一渲染时置 false；内部保留同高占位维持正文 padding。 */
    headerVisible: Boolean = true,
    initialDraft: MessageData? = null,
    onDraftChanged: (MessageData) -> Unit = {},
) {

    val listState = rememberLazyListState()
    // 草稿属于输入槽位而非会话，必须放在 session key 与挂起操作面板之外保存。
    var composerDraft by rememberSaveable(if (initialDraft != null) state.sessionId else "assistant-draft", stateSaver = MessageData.Saver) {
        mutableStateOf(initialDraft ?: MessageData())
    }
    LaunchedEffect(composerDraft, state.sessionId) {
        if (initialDraft != null) onDraftChanged(composerDraft)
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
        val selection = state.currentModelSelection ?: return@remember MultimodalCapabilities()
        state.availableLLMModelSettings
            .firstOrNull { it.id == selection.instanceId }
            ?.findModel(selection)
            ?.multimodal
            ?: MultimodalCapabilities()
    }
    // 实例级"文件上传"开关推导出的 FILE_REFERENCE 能力决定上传链路与发送门控。
    val isFileUploadEnabled = attachmentCapabilities.documentInput?.ingestMode ==
        github.ponyhuang.gimi.domain.modelcatalog.model.FileIngestMode.FILE_REFERENCE
    // 抽屉"文件"行的厂商图标：与 uploadedFiles 数据源按同一方式解析当前实例模板。
    val currentVendorTemplateId = remember(state.availableLLMModelSettings, state.currentModelSelection) {
        state.availableLLMModelSettings
            .firstOrNull { it.id == state.currentModelSelection?.instanceId }?.templateId
    }
    // 抽屉复用请求：把已上传文件回填为 file 引用草稿（file_id 就绪态，零上传）。
    LaunchedEffect(Unit) {
        attachFileRequests.collect { entry ->
            val draft = github.ponyhuang.gimi.domain.conversation.model.DraftAttachment(
                reference = github.ponyhuang.gimi.domain.conversation.model.VendorFileReference.uri(
                    entry.vendor,
                    entry.fileId,
                ),
                displayName = entry.displayName,
                mimeType = entry.mimeType,
                sizeBytes = entry.bytes,
                category = github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory.from(
                    entry.mimeType,
                    entry.displayName,
                ) ?: return@collect,
                fileId = entry.fileId,
                fileVendor = entry.vendor,
            )
            val current = composerDraft
            if (current.attachments.none { it.reference == draft.reference }) {
                composerDraft = current.copy(attachments = current.attachments + draft)
            }
        }
    }
    // 上传完成即把草稿替换为携带 file 引用的副本；发送门控据此放行。
    LaunchedEffect(state.draftUploadStates) {
        val ready = state.draftUploadStates.mapNotNull { (reference, uploadState) ->
            (uploadState as? DraftUploadState.Ready)?.let { reference to it }
        }.toMap()
        if (ready.isEmpty()) return@LaunchedEffect
        val current = composerDraft
        val updated = current.copy(
            attachments = current.attachments.map { draft ->
                ready[draft.reference]?.let { draft.copy(fileId = it.fileId, fileVendor = it.vendor) } ?: draft
            },
        )
        if (updated != current) composerDraft = updated
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

    // 是否存在用户手势发起的滚动（拖拽及其惯性）。只有用户发起的滚动结束才重新评估跟随意图；
    // 程序化跟随滚动（流式内容增长触发的 scrollToItem）结束时内容往往又增长了，
    // 若据此重评会误判为"用户离开底部"而永久关闭跟随 —— 视图冻结在当前位置（跳顶）。
    var userDrivenScroll by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            // 手指开始拖动就交还滚动控制权，不能等拖动结束才停止流式跟随。
            if (interaction is DragInteraction.Start) {
                userDrivenScroll = true
                shouldFollowLatest = false
            }
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

    // 仅在用户手势发起的一次实际滚动结束时更新“跟随最新”意图：拖拽后惯性停在底部 → 恢复跟随，
    // 停在别处 → 保持交还。程序化滚动结束一律不重评 —— 快速流式期间内容在滚动结束后又增长，
    // 此时 canScrollForward=true 与"用户离开底部"无关，不能据此关闭跟随。
    LaunchedEffect(listState, state.sessionId) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (wasScrolling && !isScrolling) {
                    if (userDrivenScroll) shouldFollowLatest = !listState.canScrollForward
                    userDrivenScroll = false
                }
                wasScrolling = isScrolling
            }
    }

    val visibleRoleChips = roleChips.takeIf { state.pendingComposerAction == null }
    val latestMessageButton: @Composable () -> Unit = {
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
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (headerVisible) {
                ChatFloatingTopBar(
                    onOpenDrawer = onOpenDrawer,
                    onNewConversation = onNewConversation,
                    chatMode = chatMode,
                    onChatModeChange = onChatModeChange,
                )
            } else {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(ChatFloatingTopBarContentHeight),
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
                                onOpenVoice = onOpenVoice,
                                modifier = Modifier,
                                messageData = composerDraft,
                                onMessageDataChange = {
                                    composerDraft = it
                                    // 附件移除后清理对应上传状态（取消在途任务）；
                                    // 上传完成后的 Ready 态在 chip 换成厂商引用时也在此回收。
                                    onUploadedFilePrune(
                                        state.draftUploadStates.keys -
                                            it.attachments.map(DraftAttachment::reference).toSet(),
                                    )
                                },
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
                                    vendorTemplateId = currentVendorTemplateId,
                                    allowMcp = chatMode != github.ponyhuang.gimi.domain.conversation.model.ChatMode.CHATBOT,
                                ),
                                attachmentCapabilities = attachmentCapabilities,
                                uploadedFiles = uploadedFiles,
                                onAttachUploadedFile = onAttachUploadedFile,
                                onUploadedFileDelete = onUploadedFileDelete,
                                attachmentUploadStates = state.draftUploadStates,
                                isFileUploadEnabled = isFileUploadEnabled,
                                showPermissionOption = chatMode != github.ponyhuang.gimi.domain.conversation.model.ChatMode.CHATBOT,
                                onRetryAttachmentUpload = onRetryAttachmentUpload,
                                onDocumentDraftImported = onDocumentDraftImported,
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
                                        instances = state.availableLLMModelSettings,
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
                                        lockedVendor = state.lockedVendor,
                                        indicateAlwaysOnSearch = chatMode == github.ponyhuang.gimi.domain.conversation.model.ChatMode.CHATBOT,
                                        modelSwitchCreatesNewSession = chatMode == github.ponyhuang.gimi.domain.conversation.model.ChatMode.CHATBOT && state.messages.isNotEmpty(),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = { if (visibleRoleChips == null) latestMessageButton() },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val listContentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
            // 推荐面板要紧贴胶囊，省掉消息流才需要的那点呼吸位。
            bottom = innerPadding.calculateBottomPadding() +
                (if (visibleRoleChips == null) 0.dp else RoleChipsRestingHeight) +
                (if (showsRecommendations) 0.dp else 8.dp),
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
            } else if (chatMode == github.ponyhuang.gimi.domain.conversation.model.ChatMode.CHATBOT &&
                listItems.isEmpty() && recommendations.isEmpty()
            ) {
                // Chatbot 空会话首屏：讲解模式定位；出现消息或推荐内容后不再展示。
                ChatbotModeIntro(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                )
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
                                                    onOpenWebSource = onOpenWebSource,
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
        if (visibleRoleChips != null) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 4.dp)) {
                        latestMessageButton()
                    }
                    key(state.sessionId) { visibleRoleChips() }
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

/** 悬浮顶栏内容区高度：48dp 按钮 + 上下各 8dp 留白；宿主层占位与此保持一致。 */
private val ChatFloatingTopBarContentHeight = 64.dp

/**
 * 悬浮顶栏：正文滚到其后时柔和淡出，状态栏保留额外遮罩。
 * 宿主可在 HorizontalPager 之上只渲染一份，模式切换时分段控件不随页面销毁，
 * 滑块动画得以连续播放；页内 Scaffold 用同高占位维持正文 contentPadding。
 */
@Composable
internal fun ChatFloatingTopBar(
    onOpenDrawer: () -> Unit,
    onNewConversation: () -> Unit,
    chatMode: github.ponyhuang.gimi.domain.conversation.model.ChatMode?,
    onChatModeChange: (github.ponyhuang.gimi.domain.conversation.model.ChatMode) -> Unit,
) {
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
            onOpenDrawer = onOpenDrawer,
            onNewConversation = onNewConversation,
            chatMode = chatMode,
            onChatModeChange = onChatModeChange,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(statusBarProtectionBrush),
        )
    }
}

/**
 * 顶栏浮动操作：左侧抽屉菜单、中间模式分段与右侧新建会话。
 * 历史会话统一从抽屉选择，文字与实时语音切换放在底部输入区。
 * 自动播报开关仍留在侧边抽屉。
 */
@Composable
internal fun ChatHeaderActions(
    onOpenDrawer: () -> Unit,
    onNewConversation: () -> Unit,
    chatMode: github.ponyhuang.gimi.domain.conversation.model.ChatMode? = null,
    onChatModeChange: (github.ponyhuang.gimi.domain.conversation.model.ChatMode) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 与输入胶囊共用容器色和描边，浅色主题下靠描边勾出边界；保留 48dp 点击区。
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.matchParentSize().padding(vertical = 2.dp),
                shape = CircleShape,
                color = chatCapsuleColor(),
                border = chatCapsuleBorder(),
            ) {}
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
        }

        if (chatMode != null) {
            // 分段控件按两档文字实际宽度自适应并居中，比撑满中段轻量、比砍半宽更稳。
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ChatModeSwitch(chatMode, onChatModeChange, Modifier.padding(horizontal = 6.dp))
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
                // 新建会话：与抽屉「新对话」行共用同一枚加号图标，两处入口视觉一致。
                IconButton(
                    onClick = onNewConversation,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_chat_add),
                        contentDescription = stringResource(R.string.chat_new_conversation),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            }
        }
    }
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
    modifier: Modifier = Modifier,
    onOpenWebSource: ((String) -> Unit)? = null,
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
            onOpenWebSource = onOpenWebSource,
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

/**
 * Chatbot 模式空会话首屏讲解：说明 Chatbot 直连厂商模型服务的定位与能力边界，
 * 并提示需要工具编排时切换 Assistant。纯展示块，出现消息或推荐内容后不再渲染。
 */
@Composable
private fun ChatbotModeIntro(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chatbot_intro_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.chatbot_intro_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Chatbot 空会话讲解 · 浅色", showBackground = true, widthDp = 390, heightDp = 700, locale = "zh-rCN")
@Preview(name = "Chatbot 空会话讲解 · 深色", showBackground = true, widthDp = 390, heightDp = 700, uiMode = 0x20, locale = "zh-rCN")
@Composable
private fun ChatbotModeIntroPreview() {
    GimiTheme {
        ChatbotModeIntro(Modifier.fillMaxSize())
    }
}

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
            onOpenDrawer = {},
            onNewConversation = {},
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
