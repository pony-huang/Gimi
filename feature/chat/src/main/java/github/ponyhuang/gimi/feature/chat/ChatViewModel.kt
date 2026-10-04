package github.ponyhuang.gimi.feature.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.usecase.PrepareChatSendUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.ChatHistorySnapshot
import github.ponyhuang.gimi.domain.conversation.usecase.ValidateChatAttachmentsUseCase
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.NoAvailableAssistantModelException
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.core.notifications.AppNotificationManager
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelectionCodec
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.mcp.model.McpSkippedServer
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.mcp.repository.McpSkipReporter
import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import github.ponyhuang.gimi.domain.memory.repository.MemoryRuntimeStatus
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechSettingsRepository
import github.ponyhuang.gimi.domain.speech.usecase.markdownToSpeechText
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 聊天页 ViewModel — 维护消息列表并把 ADK `Event` 流合并到 UI 友好的 `Message` 模型。
 *
 * 核心算法（参考 `~/.claude/projects/E--workplace-adk-web/memory/chat-streaming-and-thought.md`）：
 * 事件归约算法（partial 合并 / 完整事件构造 / 工具确认捕获）已拆到 [AgentEventReducer]，
 * 本类负责会话编排；运行生命周期由 [ChatRunLifecycleCoordinator] 协调，工具配置由 [ChatToolConfigurationCoordinator]
 * 协调，失败轮恢复由 [ChatTurnRecoveryCoordinator] 协调。
 *
 * 持久化层：`buildMessageFromParts` 改走 `EventMapper.fromEvent(event)`，保证 streaming 与历史回放共用 `Event.id → Message.id` 映射。
 * 会话管理：通过 [ConversationRepository] 完成"新建 / 切换 / 删除 / 拉取会话列表"；导航和历史刷新由 [ChatSessionNavigationCoordinator] 协调。
 *
 * 取消语义：每个会话以 runToken 隔离事件，已接管的任务可在切换会话后继续。
 *
 * DI：通过 Hilt 注入 [PrepareChatSendUseCase] / [ConversationRepository]；UI 端用
 * `hiltViewModel()` 直接拿到实例，不再走原先的 `ChatViewModel.factory(context)`。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    agentRuntimeGate: AgentRuntimeGate,
    private val repository: ConversationRepository,
    private val sessionResolver: ConversationSessionResolver,
    private val modelServices: ModelCatalogRepository,
    private val appearanceRepository: AppearanceRepository,
    private val toolApproval: ToolApprovalRepository,
    private val toolAuthorization: ToolAuthorizationRepository,
    private val mcpRepository: McpRepository,
    private val mcpSkipReporter: McpSkipReporter,
    private val speechRecognitionRepository: SpeechRecognitionRepository,
    private val speechPlaybackController: SpeechPlaybackRepository,
    private val speechSettings: SpeechSettingsRepository,
    private val attachments: ChatAttachmentRepository,
    private val prepareChatSend: PrepareChatSendUseCase,
    private val validateChatAttachments: ValidateChatAttachmentsUseCase,
    officialFunctionCatalog: OfficialToolFunctionCatalog,
    private val memoryRuntimeStatus: MemoryRuntimeStatus,
    private val appNotificationManager: AppNotificationManager,
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val sessionRuntimes = linkedMapOf<String, ChatSessionRuntime>()

    private val _effects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 8)

    private val toolConfigurationCoordinator = ChatToolConfigurationCoordinator(
        uiState = _uiState,
        scope = viewModelScope,
        repository = repository,
        sessionResolver = sessionResolver,
        modelServices = modelServices,
        officialFunctionCatalog = officialFunctionCatalog,
        runtimeFor = ::runtimeFor,
        publishRuntime = ::publishRuntime,
    )

    private val turnRecoveryCoordinator = ChatTurnRecoveryCoordinator(
        uiState = _uiState,
        runtimeFor = ::runtimeFor,
        publishRuntime = ::publishRuntime,
        resend = ::executeFailedTurnResend,
    )

    private val runLifecycleCoordinator = ChatRunLifecycleCoordinator(
        uiState = _uiState,
        scope = viewModelScope,
        agentRuntimeGate = agentRuntimeGate,
        repository = repository,
        toolApproval = toolApproval,
        appNotificationManager = appNotificationManager,
        runtimeFor = ::runtimeFor,
        runtimeOrNull = { sessionRuntimes[it] },
        publishRuntime = ::publishRuntime,
        emitNotice = ::emitNotice,
        eventReducer = { eventReducer },
        recordFailure = turnRecoveryCoordinator::recordFailure,
        onForegroundCompleted = ::autoSpeakCompletedReply,
        refreshVisibleHistoryIfStale = { sessionNavigationCoordinator.refreshVisibleHistoryIfStale() },
    )

    private val sessionNavigationCoordinator = ChatSessionNavigationCoordinator(
        uiState = _uiState,
        scope = viewModelScope,
        repository = repository,
        sessionResolver = sessionResolver,
        runtimeFor = ::runtimeFor,
        runtimeOrNull = { sessionRuntimes[it] },
        showRuntime = ::showRuntime,
        publishRuntime = ::publishRuntime,
        clearSpeechSession = speechPlaybackController::clearSession,
    )

    /** 一次性 UI 反馈通道（Toast 等），由 Route 消费；见 [ChatEffect]。 */
    val effects = _effects.asSharedFlow()

    /**
     * 用户意图统一入口 — 所有"发后即忘"的用户操作都经这里分发，见 [ChatAction]。
     */
    fun onAction(action: ChatAction) {
        when (action) {
            ChatAction.RetryFailedTurn -> turnRecoveryCoordinator.retry()
            ChatAction.ResumeChat -> resumeChat()
            ChatAction.StopStreaming -> runLifecycleCoordinator.stopStreaming()
            is ChatAction.ToggleSpeechPlayback ->
                toggleSpeechPlayback(action.messageId, action.markdown)
            ChatAction.ToggleAutoSpeak ->
                speechSettings.setAutoSpeakEnabled(!speechSettings.autoSpeakEnabled.value)
            is ChatAction.ToggleTimeline -> toggleTimeline(action.groupId)
            is ChatAction.RespondToToolConfirmation ->
                runLifecycleCoordinator.respondToToolConfirmation(action.confirmed, action.alwaysAllow)
            is ChatAction.RespondToInputRequest ->
                runLifecycleCoordinator.respondToInputRequest(action.callId, action.value)
            is ChatAction.SetFullAccess -> setFullAccess(action.enabled)
            ChatAction.RestoreOrCreateSession -> sessionNavigationCoordinator.restoreOrCreateSession()
            ChatAction.NewConversation -> sessionNavigationCoordinator.newConversation()
            is ChatAction.SwitchSession -> sessionNavigationCoordinator.switchSession(action.sessionId)
            ChatAction.RefreshConversations -> refreshConversations()
            is ChatAction.DeleteConversation -> deleteConversation(action.sessionId)
            is ChatAction.SelectModel -> selectModel(action.selection)
            is ChatAction.SetReasoningEffort -> toolConfigurationCoordinator.setReasoningEffort(action.effort)
            is ChatAction.SetMcpServerEnabled ->
                toolConfigurationCoordinator.setMcpServerEnabled(action.serverId, action.enabled)
            is ChatAction.SetOfficialFunctionEnabled -> toolConfigurationCoordinator.setOfficialFunctionEnabled(
                toolId = action.toolId,
                functionId = action.functionId,
                enabled = action.enabled,
                supportedFunctionIds = action.supportedFunctionIds,
            )
            is ChatAction.LoadOfficialToolFunctions -> toolConfigurationCoordinator.loadFunctions(action.toolId)
            ChatAction.ClearToolConfigurationError -> toolConfigurationCoordinator.clearError()
            is ChatAction.SetThemeMode ->
                appearanceRepository.setThemeMode(action.mode)
        }
    }

    /** 向 [effects] 通道发射一条一次性提示，由 Route 消费（Toast 等）。 */
    private fun emitNotice(notice: ChatNotice) {
        _effects.tryEmit(ChatEffect.ShowNotice(notice))
    }

    suspend fun transcribeVoice(pcm16: ByteArray): String =
        speechRecognitionRepository.transcribe(pcm16)

    private fun toggleSpeechPlayback(messageId: String, markdown: String) {
        speechPlaybackController.toggle(messageId, markdownToSpeechText(markdown))
    }

    private fun toggleTimeline(groupId: String) {
        _uiState.update { state ->
            val expanded = state.expandedActivityGroupIds.toMutableSet()
            if (!expanded.add(groupId)) expanded.remove(groupId)
            state.copy(expandedActivityGroupIds = expanded)
        }
    }

    /**
     * 会话列表由 [repository.conversations] 转发到 [uiState] 的 [ChatUiState.conversations]
     * 字段；UI 只订阅 `uiState` 一条流即可同时拿到消息、streaming 标志、会话 id 与会话列表。
     */
    init {
        viewModelScope.launch {
            repository.conversations.collect { convs ->
                _uiState.update { it.copy(conversations = convs) }
            }
        }
        viewModelScope.launch {
            modelServices.observeServices().collect { services ->
                _uiState.update { state ->
                    state.copy(
                        availableLLMModelSettings = services,
                        officialToolDescriptors = toolConfigurationCoordinator.buildDescriptors(
                            selection = state.currentModelSelection,
                            existing = state.officialToolDescriptors,
                        ),
                    )
                }
                val sessionId = _uiState.value.sessionId
                val runtime = sessionRuntimes[sessionId] ?: return@collect
                val selection = runtime.modelSelection ?: return@collect
                toolConfigurationCoordinator.initializeForSelection(sessionId, runtime, selection)
                publishRuntime(runtime)
            }
        }
        viewModelScope.launch {
            mcpRepository.observeServers().collect { servers ->
                _uiState.update { it.copy(availableMcpServers = servers) }
            }
        }
        viewModelScope.launch {
            modelServices.observeLoadState().collect { state ->
                _uiState.update { it.copy(modelCatalogLoadState = state) }
            }
        }
        viewModelScope.launch {
            appearanceRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            toolApproval.fullAccess.collect { enabled ->
                _uiState.update { it.copy(fullAccess = enabled) }
            }
        }
        viewModelScope.launch {
            speechRecognitionRepository.availability.collect { available ->
                _uiState.update { it.copy(isSpeechRecognitionAvailable = available) }
            }
        }
        viewModelScope.launch {
            speechPlaybackController.state.collect { playback ->
                _uiState.update { it.copy(speechPlaybackState = playback) }
            }
        }
        viewModelScope.launch {
            speechSettings.autoSpeakEnabled.collect { enabled ->
                _uiState.update { it.copy(autoSpeakEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appUpdateRepository.hasUnseenUpdate.collect { unseen ->
                _uiState.update { it.copy(hasUpdateBadge = unseen) }
            }
        }
        viewModelScope.launch {
            repository.conversationContentRevisions.collect {
                sessionNavigationCoordinator.refreshVisibleHistoryIfStale()
            }
        }
        viewModelScope.launch {
            speechPlaybackController.errors.collect { message ->
                emitNotice(ChatNotice.Message(message))
            }
        }
        viewModelScope.launch {
            mcpSkipReporter.skipped.collect { skipped ->
                notifySkippedMcpServers(skipped)
            }
        }
        viewModelScope.launch {
            memoryRuntimeStatus.failures.collect { failure ->
                emitNotice(
                    when (failure.operation) {
                        MemoryOperation.SEARCH -> ChatNotice.MemorySearchFailed
                        MemoryOperation.WRITE -> ChatNotice.MemoryWriteFailed
                    },
                )
            }
        }
    }

    /** 已提示过的 (sessionId, serverId)，保证每个服务器每个会话只提示一次。 */
    private val notifiedSkippedMcpServers = mutableSetOf<String>()

    /**
     * 切换 Full access 全局开关。开启瞬间把所有已挂起的确认卡片立即放行，
     * 避免开关"看起来没生效"。
     */
    private fun setFullAccess(enabled: Boolean) {
        toolApproval.setFullAccess(enabled)
        if (!enabled) return
        sessionRuntimes.values
            .filter { it.pendingToolConfirmations.isNotEmpty() }
            .forEach { runLifecycleCoordinator.respondToToolConfirmation(it.sessionId, confirmed = true) }
    }

    private fun notifySkippedMcpServers(skipped: List<McpSkippedServer>) {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank() || skipped.isEmpty()) return
        skipped
            .filter { notifiedSkippedMcpServers.add("$sessionId:${it.serverId}") }
            .forEach { emitNotice(ChatNotice.McpServerSkipped(it.displayName)) }
    }

    /**
     * ADK 事件归约器 — 把 partial/complete `ChatRunEvent` 合并进会话运行时。
     * 依赖以引用注入，逻辑见 [AgentEventReducer]。
     */
    private val eventReducer: AgentEventReducer by lazy {
        AgentEventReducer(
            runtimeOrNull = { sessionRuntimes[it] },
            runtimeFor = ::runtimeFor,
            publishRuntime = ::publishRuntime,
            emitPartDelta = ::emitPartDelta,
            toolAuthorization = toolAuthorization,
            isAutoApproved = toolApproval::isAutoApproved,
        )
    }

    internal fun runtimeFor(sessionId: String): ChatSessionRuntime =
        sessionRuntimes.getOrPut(sessionId) { ChatSessionRuntime(sessionId) }

    private fun publishRuntime(runtime: ChatSessionRuntime) {
        val statuses = sessionRuntimes.values.mapNotNull { session ->
            session.drawerStatus()?.let { status -> session.sessionId to status }
        }.toMap()
        _uiState.update { state ->
            if (state.sessionId == runtime.sessionId) {
                state.copy(
                    messages = runtime.messages,
                    listItems = runtime.toChatListItems(),
                    isAgentRunning = runtime.isAgentRunning,
                    failedTurn = runtime.failedRecoverableTurn(),
                    currentModelSelection = runtime.modelSelection,
                    toolConfiguration = runtime.toolConfiguration,
                    officialToolDescriptors = toolConfigurationCoordinator.buildDescriptors(
                        runtime.modelSelection,
                        state.officialToolDescriptors,
                    ),
                    pendingToolConfirmations = runtime.pendingToolConfirmations,
                    pendingInputRequests = runtime.pendingInputRequests,
                    conversationTaskStatuses = statuses,
                )
            } else {
                state.copy(conversationTaskStatuses = statuses)
            }
        }
        toolConfigurationCoordinator.expandPendingMarkers()
    }


    private fun showRuntime(sessionId: String, isInitializing: Boolean = false) {
        val runtime = runtimeFor(sessionId)
        _uiState.update { state ->
            state.copy(
                sessionId = sessionId,
                messages = runtime.messages,
                listItems = runtime.toChatListItems(),
                isAgentRunning = runtime.isAgentRunning,
                failedTurn = runtime.failedRecoverableTurn(),
                currentModelSelection = runtime.modelSelection,
                toolConfiguration = runtime.toolConfiguration,
                officialToolDescriptors = toolConfigurationCoordinator.buildDescriptors(
                    runtime.modelSelection,
                    state.officialToolDescriptors,
                ),
                pendingToolConfirmations = runtime.pendingToolConfirmations,
                pendingInputRequests = runtime.pendingInputRequests,
                isInitializing = isInitializing,
            )
        }
        toolConfigurationCoordinator.expandPendingMarkers()
        publishRuntime(runtime)
    }

    /**
     * 自动语音播报刚完成的回复。仅对当前前台会话生效（后台完成的会话不打扰用户），
     * 失败收尾与空文本（纯工具调用轮）跳过；文本与手动播报共用同一条清洗链路。
     */
    private fun autoSpeakCompletedReply(sessionId: String, runtime: ChatSessionRuntime) {
        if (!speechSettings.autoSpeakEnabled.value) return
        if (runtime.failed) return
        val state = _uiState.value
        if (state.sessionId != sessionId) return
        val reply = state.messages.lastOrNull { message ->
            message.role == MessageRole.Assistant && !message.partial
        } ?: return
        val text = reply.textParts
            .filterNot { it.thought }
            .joinToString(separator = "") { it.text }
            .takeIf(String::isNotBlank)
            ?: return
        speechPlaybackController.play(reply.id, markdownToSpeechText(text))
    }

    override fun onCleared() {
        sessionRuntimes.values.forEach { runtime ->
            runLifecycleCoordinator.cancelRun(runtime)
            runtime.closePartChannels()
        }
        speechPlaybackController.clearSession()
        super.onCleared()
    }

    /**
     * 每个 [TextPart] 的文本增量流 — 渲染端用 `rememberStreamingMarkdownState` + `append()`
     * 做增量解析，避免每次 partial 都重解析整段 markdown。
     */
    /**
     * 返回指定 [TextPart.id] 的文本增量订阅 channel。如果该 part 还没有任何增量发出，返回 `null`。
     */
    fun partChannelFor(partId: String): ReceiveChannel<String>? =
        sessionRuntimes[_uiState.value.sessionId]?.partChannel(partId)

    private fun emitPartDelta(sessionId: String, partId: String, delta: String) {
        runtimeFor(sessionId).emitPartDelta(partId, delta)
    }

    /**
     * 关闭并清空所有 [partChannels]。
     *
     * 切换 / 重置会话时调用，避免 channel 跨会话累积（`Channel(UNLIMITED)` 持有挂起的消费者协程，
     * 仅当 `partChannels` 不再引用时才会被 GC）。
     */
    private fun clearPartChannels(sessionId: String = _uiState.value.sessionId) {
        sessionRuntimes[sessionId]?.closePartChannels()
    }

    private var resolvingSubmission = false

    /** 发送只回报真实接管结果；准备过程属于 ViewModel，不依赖输入框协程的生命周期。 */
    fun send(
        text: String,
        draftAttachments: List<DraftAttachment> = emptyList(),
        onResult: (ChatSubmissionResult) -> Unit = {},
    ) {
        var ownsResolution = false
        val submission = ChatSubmission { result ->
            if (ownsResolution) resolvingSubmission = false
            onResult(result)
        }
        val state = _uiState.value
        if (text.isBlank() && draftAttachments.isEmpty() ||
            state.pendingToolConfirmation != null || state.pendingInputRequest != null ||
            state.isInitializing || sessionNavigationCoordinator.loadingSessionId != null
        ) {
            submission.complete(ChatSubmissionResult.REJECTED)
            return
        }
        val selection = state.currentModelSelection?.takeIf(::isUsableChatSelection)
        if (state.sessionId.isNotBlank() && selection != null) {
            startSend(state.sessionId, selection, text, draftAttachments, submission = submission)
            return
        }
        if (resolvingSubmission) {
            submission.complete(ChatSubmissionResult.REJECTED)
            return
        }
        resolvingSubmission = true
        ownsResolution = true
        val navigationAtSend = sessionNavigationCoordinator.navigationVersion
        var handedOff = false
        val job = viewModelScope.launch {
            try {
                val snapshot = if (state.sessionId.isBlank()) {
                    sessionResolver.resolveCurrentOrCreate()
                } else {
                    sessionResolver.activate(state.sessionId) ?: sessionResolver.resolveCurrentOrCreate()
                }
                currentCoroutineContext().ensureActive()
                // 会话解析期间用户已导航，不把旧发送强行切回当前页面。
                if (sessionNavigationCoordinator.navigationVersion != navigationAtSend || sessionNavigationCoordinator.loadingSessionId != null) return@launch
                handedOff = true
                startSend(
                    snapshot.sessionId, snapshot.modelSelection, text, draftAttachments,
                    submission = submission,
                    showAfterAcceptance = true,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: NoAvailableAssistantModelException) {
                emitNotice(ChatNotice.ConfigureChatModel)
            } catch (failure: Exception) {
                // 不能把所有解析失败都归因于"未配置模型"；Room/ADK/附件归档故障
                // 需要真实的堆栈与原因，否则用户会去做完全无关的修复。
                Log.w(TAG, "Chat send session resolution failed", failure)
                emitNotice(ChatNotice.Message(failure.message ?: "Unknown error"))
            } finally {
                if (!handedOff) submission.complete(ChatSubmissionResult.REJECTED)
            }
        }
        job.invokeOnCompletion {
            if (!handedOff) submission.complete(ChatSubmissionResult.REJECTED)
        }
    }

    private fun startSend(
        sessionId: String,
        selection: ModelSelection,
        text: String,
        draftAttachments: List<DraftAttachment>,
        retry: ChatTurn? = null,
        submission: ChatSubmission = ChatSubmission {},
        showAfterAcceptance: Boolean = false,
    ) {
        val runtime = runtimeFor(sessionId)
        if (runtime.isActive || sessionNavigationCoordinator.loadingSessionId != null ||
            sessionRuntimes.values.count { it.isActive } >= MAX_PARALLEL_TASKS
        ) {
            if (!runtime.isActive && sessionNavigationCoordinator.loadingSessionId == null) {
                emitNotice(ChatNotice.ParallelTaskLimitReached)
            }
            submission.complete(ChatSubmissionResult.REJECTED)
            return
        }
        validateChatAttachments(selection, _uiState.value.availableLLMModelSettings, draftAttachments)?.let {
            emitNotice(it.toChatNotice())
            submission.complete(ChatSubmissionResult.REJECTED)
            return
        }
        runLifecycleCoordinator.clearToolConfirmationState(runtime)
        runtime.modelSelection = selection
        runtime.failed = false
        runtime.attention = SessionResultAttention.NONE
        val navigationAtSend = sessionNavigationCoordinator.navigationVersion
        var preparedTurn: ChatTurn? = null
        val job = runLifecycleCoordinator.launchRun(runtime, onFailure = { failure ->
            preparedTurn?.let { turnRecoveryCoordinator.recordFailure(sessionId, it) }
        }) { runToken ->
            try {
                // 准备流程不持有运行所有权；接管前仍由本类核对 token 与导航版本。
                val prepared = prepareChatSend(
                    sessionId = sessionId,
                    selection = selection,
                    text = text,
                    drafts = draftAttachments,
                    cachedHistory = {
                        runtime.takeIf { it.isLoaded }?.let {
                            ChatHistorySnapshot(it.messages, it.loadedContentRevision)
                        }
                    },
                    retry = retry,
                )
                val turn = prepared.turn
                val execution = prepared.execution
                currentCoroutineContext().ensureActive()
                if (runtime.runToken !== runToken) throw CancellationException("Superseded preparation")
                // 未接管前的导航取消发送；已接管的后台任务则继续执行。
                if (sessionNavigationCoordinator.navigationVersion != navigationAtSend) throw CancellationException("Navigation changed")
                runtime.execution = execution
                runtime.toolConfiguration = prepared.toolConfiguration
                preparedTurn = turn
                runtime.lastTurn = turn
                runtime.messages = turn.messages
                runtime.retryingInvocation = retry != null
                runtime.loadedContentRevision = prepared.contentRevision
                runtime.isLoaded = true
                runtime.failed = false
                submission.complete(ChatSubmissionResult.ACCEPTED)
                if (showAfterAcceptance) showRuntime(sessionId) else publishRuntime(runtime)
                execution.send(
                    text = turn.userMessage.textParts.joinToString("") { it.text },
                    fileAttachments = turn.userMessage.fileAttachments,
                    retry = retry != null,
                ).collect { event ->
                    event.functionCalls
                        .firstOrNull { it.confirmationRequest == null }
                        ?.let {
                            appNotificationManager.notifyToolExecution(
                                toolName = it.name,
                                taskId = sessionId,
                            )
                        }
                    eventReducer.applyEvent(sessionId, event, runToken)
                }
            } finally {
                submission.complete(ChatSubmissionResult.REJECTED)
                if (submission.result == ChatSubmissionResult.ACCEPTED) {
                    // 输入已归档，网络失败和取消也可清理原草稿；重试使用归档副本。
                    withContext(NonCancellable) { attachments.deleteDrafts(draftAttachments) }
                }
            }
        }
        // 覆盖协程尚未开始就被取消的情况。
        job.invokeOnCompletion { submission.complete(ChatSubmissionResult.REJECTED) }
    }

    /**
     * 返回/恢复聊天界面。
     *
     * 活跃任务只重设流式 channel，避免重新读取历史打断正在生成的会话；后台已完成的
     * 任务则重新读取一次完整历史。Activity 在后台期间可能没有持续消费 Compose 的
     * streaming state，直接沿用旧 channel 会留下截断的 Markdown 文本，重新切换会话之所以
     * 能修复正是因为它走了这条历史读取路径。
     */
    private fun resumeChat() {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return
        val runtime = sessionRuntimes[sessionId] ?: return
        if (runtime.isActive) {
            runtime.reseedPartialChannels()
            publishRuntime(runtime)
            return
        }

        viewModelScope.launch {
            val messages = repository.loadMessages(sessionId)
                ?.takeIf { it.isNotEmpty() || runtime.messages.isEmpty() }
                ?: return@launch
            if (_uiState.value.sessionId != sessionId || runtime.isActive) return@launch
            runtime.closePartChannels()
            runtime.messages = messages
            runtime.isLoaded = true
            publishRuntime(runtime)
            _uiState.update { state ->
                if (state.sessionId == sessionId) {
                    state.copy(scrollToLatestRequest = state.scrollToLatestRequest + 1L)
                } else {
                    state
                }
            }
        }
    }

    /** 原样重试和编辑提交的唯一执行入口。 */
    private fun executeFailedTurnResend(failedTurn: ChatTurn) {
        val state = _uiState.value
        val selection = state.currentModelSelection?.takeIf(::isUsableChatSelection) ?: run {
            emitNotice(ChatNotice.ConfigureChatModel)
            return
        }
        if (state.sessionId.isBlank()) return
        startSend(
            sessionId = state.sessionId,
            selection = selection,
            text = failedTurn.userMessage.textParts.joinToString("") { it.text },
            draftAttachments = emptyList(),
            retry = failedTurn,
        )
    }

    /**
     * 触发一次 [ConversationRepository.refresh] 拉取最新会话列表（写到 [conversations]）。
     */
    private fun refreshConversations() {
        viewModelScope.launch { repository.refresh() }
    }

    /**
     * 删除指定 session，并刷新会话列表。
     *
     * 守卫：若 [sessionId] 等于当前 `_uiState.value.sessionId`（即用户正在用的会话），直接 no-op —
     * 删掉当前会话会把 `sessionId` 留在一个已删除的 id 上，后续 `send()` 会因找不到 session 而失败。
     *
     * 副作用：删除成功后同步移除 RoomDatabase 中对应的会话元数据。
     */
    private fun deleteConversation(sessionId: String) {
        if (sessionId.isBlank()) return
        if (sessionRuntimes[sessionId]?.isActive == true) {
            emitNotice(ChatNotice.ActiveConversationDeleteBlocked)
            return
        }
        if (sessionId == _uiState.value.sessionId) {
            Log.w(TAG, "deleteConversation($sessionId) refused: this is the active session.")
            return
        }
        viewModelScope.launch {
            repository.deleteConversation(sessionId)
            attachments.deleteSession(sessionId)
            sessionRuntimes.remove(sessionId)?.closePartChannels()
        }
    }

    private fun selectModel(selection: ModelSelection) {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return
        val runtime = runtimeFor(sessionId)
        if (runtime.isActive) {
            emitNotice(ChatNotice.ModelSwitchBlocked)
            return
        }
        viewModelScope.launch {
            repository.setConversationModel(sessionId, ModelSelectionCodec.encode(selection))
            runtime.modelSelection = selection
            toolConfigurationCoordinator.initializeForSelection(sessionId, runtime, selection)
            publishRuntime(runtime)
        }
    }

    private fun isUsableChatSelection(selection: ModelSelection): Boolean =
        modelServices.currentServices().isUsableChatSelection(selection)

    // ── Helpers ────────────────────────────────────────────────────────────

    companion object {
        private const val TAG: String = "ChatViewModel"
        private const val MAX_PARALLEL_TASKS: Int = 3
    }
}

/** 为 UI 发布当前会话的可展示轮次，同时保留 [ChatSessionRuntime.messages] 原始真相。 */
private fun ChatSessionRuntime.toChatListItems(): List<ChatListItem> =
    messages.toChatListItems(
        TimelineActivityState(
            isAgentRunning = isAgentRunning,
            toolStatuses = toolStatuses,
        ),
    )

private fun List<LLMModelSetting>.isUsableChatSelection(selection: ModelSelection): Boolean {
    val service = firstOrNull { it.id == selection.serviceId } ?: return false
    if (!service.isEnabled || service.apiKey.isBlank()) return false
    val group = service.groups.firstOrNull { it.id == selection.groupId } ?: return false
    val model = group.models.firstOrNull { it.id == selection.modelId } ?: return false
    return !model.isStt && !model.isTts
}

/** 最近一轮若处于失败态，则可作为当前进程内“重试”的可恢复轮。 */
private fun ChatSessionRuntime.failedRecoverableTurn(): ChatTurn? =
    lastTurn?.takeIf { it.status == ChatTurnStatus.FAILED }
