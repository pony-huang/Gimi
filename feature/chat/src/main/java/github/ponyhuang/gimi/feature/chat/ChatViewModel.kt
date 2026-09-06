package github.ponyhuang.gimi.feature.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatTurnRepository
import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.usecase.PrepareChatTurnUseCase
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionBusyException
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.Messages
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
import github.ponyhuang.gimi.domain.speech.repository.VoiceWakeRepository
import github.ponyhuang.gimi.domain.speech.usecase.markdownToSpeechText
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
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
 * 本类负责会话编排、运行时生命周期与工具配置。
 *
 * 持久化层：`buildMessageFromParts` 改走 `EventMapper.fromEvent(event)`，保证 streaming 与历史回放共用 `Event.id → Message.id` 映射。
 * 会话管理：通过 [ConversationRepository] 完成"新建 / 切换 / 删除 / 拉取会话列表"；`reset()` 与 `switchSession()` 都走 repository。
 *
 * 取消语义：每次 `send` 取消 `currentJob`，避免 partial 流交错。
 *
 * DI：通过 Hilt 注入 [ChatAgentRepository] / [ConversationRepository]；UI 端用
 * `hiltViewModel()` 直接拿到实例，不再走原先的 `ChatViewModel.factory(context)`。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val runner: ChatAgentRepository,
    private val agentRuntimeGate: AgentRuntimeGate,
    private val repository: ConversationRepository,
    private val sessionResolver: ConversationSessionResolver,
    private val modelServices: ModelCatalogRepository,
    private val chatDisplayPreferences: ChatDisplayRepository,
    private val appearanceRepository: AppearanceRepository,
    private val toolApproval: ToolApprovalRepository,
    private val toolAuthorization: ToolAuthorizationRepository,
    private val mcpRepository: McpRepository,
    private val mcpSkipReporter: McpSkipReporter,
    private val speechRecognitionRepository: SpeechRecognitionRepository,
    private val speechPlaybackController: SpeechPlaybackRepository,
    private val speechSettings: SpeechSettingsRepository,
    private val voiceWake: VoiceWakeRepository,
    private val attachments: ChatAttachmentRepository,
    private val prepareChatTurn: PrepareChatTurnUseCase,
    private val turnRepository: ChatTurnRepository,
    private val officialFunctionCatalog: OfficialToolFunctionCatalog,
    private val memoryRuntimeStatus: MemoryRuntimeStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 8)

    /** 一次性 UI 反馈通道（Toast 等），由 Route 消费；见 [ChatEffect]。 */
    val effects = _effects.asSharedFlow()

    /** 当前聊天页处于前台时，通知语音运行时忽略唤醒词。 */
    fun setCurrentChatVisible(visible: Boolean) {
        voiceWake.setCurrentChatVisible(visible)
    }

    /**
     * 用户意图统一入口 — 所有"发后即忘"的用户操作都经这里分发，见 [ChatAction]。
     */
    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.Send -> send(action.text, action.draftAttachments)
            ChatAction.RetryFailedTurn -> retryFailedTurn()
            ChatAction.EditFailedTurn -> editFailedTurn()
            ChatAction.CancelEditFailedTurn -> cancelEditFailedTurn()
            is ChatAction.ResolveRepeatExecution -> resolveRepeatExecution(action.proceed)
            ChatAction.StopStreaming -> stopStreaming()
            is ChatAction.ToggleSpeechPlayback ->
                toggleSpeechPlayback(action.messageId, action.markdown)
            ChatAction.ToggleAutoSpeak ->
                speechSettings.setAutoSpeakEnabled(!speechSettings.autoSpeakEnabled.value)
            is ChatAction.RespondToToolConfirmation ->
                respondToToolConfirmation(action.confirmed, action.alwaysAllow)
            is ChatAction.SetFullAccess -> setFullAccess(action.enabled)
            ChatAction.RestoreOrCreateSession -> restoreOrCreateSession()
            ChatAction.NewConversation -> reset()
            is ChatAction.SwitchSession -> switchSession(action.sessionId)
            ChatAction.RefreshConversations -> refreshConversations()
            is ChatAction.DeleteConversation -> deleteConversation(action.sessionId)
            is ChatAction.SelectModel -> selectModel(action.selection)
            is ChatAction.SetToolAccessMode -> setToolAccessMode(action.mode)
            is ChatAction.SetReasoningEffort -> setReasoningEffort(action.effort)
            is ChatAction.SetMcpServerEnabled ->
                setMcpServerEnabled(action.serverId, action.enabled)
            is ChatAction.SetOfficialFunctionEnabled -> setOfficialFunctionEnabled(
                toolId = action.toolId,
                functionId = action.functionId,
                enabled = action.enabled,
                supportedFunctionIds = action.supportedFunctionIds,
            )
            is ChatAction.LoadOfficialToolFunctions -> loadOfficialToolFunctions(action.toolId)
            ChatAction.ClearToolConfigurationError -> clearToolConfigurationError()
            is ChatAction.SetDarkTheme ->
                appearanceRepository.setDarkThemeOverride(action.enabled)
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

    /** Sends the user's decision back to ADK, which then either runs or rejects the paused tool. */
    private fun respondToToolConfirmation(confirmed: Boolean, alwaysAllow: Boolean = false) {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return
        respondToToolConfirmation(sessionId, confirmed, alwaysAllow)
    }

    private fun respondToToolConfirmation(
        sessionId: String,
        confirmed: Boolean,
        alwaysAllow: Boolean = false,
    ) {
        val runtime = runtimeFor(sessionId)
        val request = runtime.pendingToolConfirmations.firstOrNull() ?: return
        if (confirmed) {
            if (alwaysAllow) toolApproval.setAlwaysAllowed(request.toolName)
            runtime.approvedToolsThisTurn += request.toolName
        } else {
            runtime.approvedToolsThisTurn.clear()
            runtime.rejectedToolNames += request.toolName
        }
        runtime.pendingToolConfirmations = runtime.pendingToolConfirmations.filterNot {
            it.confirmationCallId == request.confirmationCallId
        }
        cancelRun(runtime, releaseLease = false)
        val runToken = Any()
        runtime.runToken = runToken
        runtime.isAgentRunning = true
        runtime.turnComplete = false
        runtime.phase = AgentTaskPhase.GENERATING
        publishRuntime(runtime)
        runtime.job = viewModelScope.launch {
            try {
                cancellationAwareRunCatching {
                    ensureRunLease(runtime).updatePhase(AgentTaskPhase.GENERATING)
                    runner.respondToToolConfirmation(
                        sessionId = sessionId,
                        confirmationCallId = request.confirmationCallId,
                        confirmed = confirmed,
                    ).collect { event ->
                        Log.i("chat", "tool confirmation event: $event")
                        eventReducer.applyEvent(sessionId, event, runToken)
                    }
                }.onFailure { failure ->
                    eventReducer.applyError(sessionId, failure.message ?: failure::class.simpleName ?: "Unknown error")
                }
            } finally {
                finishRunIfOwned(sessionId, runToken)
                repository.refreshConversation(sessionId)
            }
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
                _uiState.update { it.copy(availableLLMModelSettings = services) }
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
            chatDisplayPreferences.showToolActivity.collect { show ->
                _uiState.update { it.copy(showToolActivity = show) }
            }
        }
        viewModelScope.launch {
            appearanceRepository.darkThemeOverride.collect { override ->
                _uiState.update { it.copy(darkThemeOverride = override) }
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
            repository.conversationContentUpdates.collect { sessionId ->
                handleConversationContentUpdate(sessionId)
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
            .forEach { respondToToolConfirmation(it.sessionId, confirmed = true) }
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
            scope = viewModelScope,
            repository = repository,
            toolAuthorization = toolAuthorization,
            isAutoApproved = toolApproval::isAutoApproved,
        )
    }

    private val sessionRuntimes = linkedMapOf<String, ChatSessionRuntime>()
    private var sessionLoadJob: Job? = null
    private var activeSessionLoadToken: Any? = null
    private var loadingSessionId: String? = null
    private var pendingContentRefreshSessionId: String? = null
    /** 待用户确认的“重试”意图；仅在工具已执行时置位。 */
    private var pendingRetryRun = false
    /** 待确认的“编辑后发送”内容；仅在工具已执行时置位。 */
    private var pendingEditSend: MessageData? = null
    /** 进入编辑前暂存的输入框草稿；取消编辑时恢复。 */
    private var pendingPriorComposerDraft: MessageData? = null
    private fun runtimeFor(sessionId: String): ChatSessionRuntime =
        sessionRuntimes.getOrPut(sessionId) { ChatSessionRuntime(sessionId) }

    private fun publishRuntime(runtime: ChatSessionRuntime) {
        val statuses = sessionRuntimes.values.mapNotNull { session ->
            session.drawerStatus()?.let { status -> session.sessionId to status }
        }.toMap()
        _uiState.update { state ->
            if (state.sessionId == runtime.sessionId) {
                state.copy(
                    messages = runtime.messages,
                    isAgentRunning = runtime.isAgentRunning,
                    lastSendFailed = runtime.failed,
                    failedTurn = runtime.failedRecoverableTurn(),
                    turnComplete = runtime.turnComplete,
                    currentModelSelection = runtime.modelSelection,
                    toolConfiguration = runtime.toolConfiguration,
                    officialToolDescriptors = buildOfficialToolDescriptors(
                        runtime.modelSelection,
                        state.officialToolDescriptors,
                    ),
                    pendingToolConfirmations = runtime.pendingToolConfirmations,
                    rejectedToolNames = runtime.rejectedToolNames.toSet(),
                    conversationTaskStatuses = statuses,
                )
            } else {
                state.copy(conversationTaskStatuses = statuses)
            }
        }
        scheduleMarkerExpansion()
    }

    private fun showRuntime(sessionId: String, isInitializing: Boolean = false) {
        val runtime = runtimeFor(sessionId)
        _uiState.update { state ->
            state.copy(
                sessionId = sessionId,
                messages = runtime.messages,
                isAgentRunning = runtime.isAgentRunning,
                lastSendFailed = runtime.failed,
                failedTurn = runtime.failedRecoverableTurn(),
                turnComplete = runtime.turnComplete,
                currentModelSelection = runtime.modelSelection,
                toolConfiguration = runtime.toolConfiguration,
                officialToolDescriptors = buildOfficialToolDescriptors(
                    runtime.modelSelection,
                    state.officialToolDescriptors,
                ),
                pendingToolConfirmations = runtime.pendingToolConfirmations,
                rejectedToolNames = runtime.rejectedToolNames.toSet(),
                isInitializing = isInitializing,
            )
        }
        scheduleMarkerExpansion()
        publishRuntime(runtime)
    }

    private fun cancelRun(runtime: ChatSessionRuntime, releaseLease: Boolean = true) {
        runtime.runToken = null
        runtime.job?.cancel()
        runtime.job = null
        if (releaseLease) {
            val lease = runtime.lease
            runtime.lease = null
            lease?.release()
        }
    }

    private suspend fun ensureRunLease(runtime: ChatSessionRuntime): AgentRunLease {
        runtime.lease?.let { return it }
        return agentRuntimeGate.acquire(
            source = AgentTaskSource.CHAT,
            sessionId = runtime.sessionId,
        ).also { runtime.lease = it }
    }

    private fun releaseRunLease(runtime: ChatSessionRuntime) {
        runtime.lease?.release()
        runtime.lease = null
    }

    private suspend fun finishRunIfOwned(sessionId: String, runToken: Any) {
        val runtime = runtimeFor(sessionId)
        if (runtime.runToken !== runToken) return
        runtime.job = null
        val pending = runtime.pendingToolConfirmations.firstOrNull()
        runtime.isAgentRunning = pending != null
        if (pending != null && pending.toolName in runtime.approvedToolsThisTurn) {
            runtime.lease?.updatePhase(AgentTaskPhase.WAITING_FOR_CONFIRMATION)
            publishRuntime(runtime)
            respondToToolConfirmation(sessionId, confirmed = true)
        } else if (pending != null) {
            runtime.lease?.updatePhase(AgentTaskPhase.WAITING_FOR_CONFIRMATION)
            publishRuntime(runtime)
        } else {
            runtime.approvedToolsThisTurn.clear()
            releaseRunLease(runtime)
            if (_uiState.value.sessionId != sessionId) {
                runtime.attention = if (runtime.failed) {
                    SessionResultAttention.FAILED
                } else {
                    SessionResultAttention.COMPLETED
                }
            } else {
                autoSpeakCompletedReply(sessionId, runtime)
            }
            publishRuntime(runtime)
        }
        viewModelScope.launch { flushPendingConversationContentUpdate() }
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

    private fun clearToolConfirmationState(runtime: ChatSessionRuntime) {
        runtime.approvedToolsThisTurn.clear()
        runtime.pendingToolConfirmations = emptyList()
        publishRuntime(runtime)
    }

    override fun onCleared() {
        sessionRuntimes.values.forEach { runtime ->
            runtime.job?.cancel()
            runtime.lease?.release()
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

    /**
     * Reloads a session changed by the background voice runner when that session is visible.
     *
     * A foreground turn owns the in-memory partial reducer, while a session switch owns the
     * initial history snapshot. In either case replacing [ChatUiState.messages] immediately would
     * lose newer UI state, so the invalidation is retained and flushed by the owner on completion.
     */
    private suspend fun handleConversationContentUpdate(sessionId: String) {
        val loadingId = loadingSessionId
        if (loadingId != null) {
            if (loadingId == sessionId) pendingContentRefreshSessionId = sessionId
            return
        }

        val state = _uiState.value
        if (state.sessionId != sessionId) return
        if (state.isAgentRunning || state.isInitializing) {
            pendingContentRefreshSessionId = sessionId
            return
        }
        reloadConversationMessages(sessionId)
    }

    private suspend fun reloadConversationMessages(sessionId: String) {
        val beforeLoad = _uiState.value
        if (beforeLoad.sessionId != sessionId || beforeLoad.isAgentRunning ||
            beforeLoad.isInitializing || loadingSessionId != null
        ) {
            if (beforeLoad.sessionId == sessionId || loadingSessionId == sessionId) {
                pendingContentRefreshSessionId = sessionId
            }
            return
        }

        val messages = repository.loadMessages(sessionId) ?: return
        val afterLoad = _uiState.value
        if (afterLoad.sessionId != sessionId || afterLoad.isAgentRunning ||
            afterLoad.isInitializing || loadingSessionId != null
        ) {
            if (afterLoad.sessionId == sessionId || loadingSessionId == sessionId) {
                pendingContentRefreshSessionId = sessionId
            }
            return
        }

        val runtime = runtimeFor(sessionId)
        runtime.closePartChannels()
        val current = _uiState.value
        if (current.sessionId == sessionId && !current.isAgentRunning &&
            !current.isInitializing && loadingSessionId == null
        ) {
            runtime.messages = messages
            runtime.isLoaded = true
            runtime.turnComplete = false
            publishRuntime(runtime)
        }
    }

    private suspend fun flushPendingConversationContentUpdate(expectedSessionId: String? = null) {
        val pendingId = pendingContentRefreshSessionId ?: return
        if (expectedSessionId != null && pendingId != expectedSessionId) return

        val state = _uiState.value
        if (loadingSessionId != null || state.isAgentRunning || state.isInitializing) return
        if (state.sessionId != pendingId) {
            pendingContentRefreshSessionId = null
            return
        }

        pendingContentRefreshSessionId = null
        reloadConversationMessages(pendingId)
    }

    /**
     * 发送用户消息。
     *
     * 行为：
     * - 取消当前进行中的 send。
     * - 立刻在消息列表尾部追加一条 `User` 消息（乐观 UI）。
     * - 兜底确保 [sessionId] 有值 — 若为空（首次安装还没建过会话），先调
     *   [ConversationRepository.createConversation] 建一个再走 [ChatAgentRepository.send]，
     *   避免 ADK `createSession(SessionKey(id = ""))` 抛 "SessionKey.id must not be blank"。
     * - 启动协程调用 [ChatAgentRepository.send]，把每个 `Event` 送入 [applyEvent]。
     */
    fun send(text: String, draftAttachments: List<DraftAttachment> = emptyList()): Boolean {
        if (text.isBlank() && draftAttachments.isEmpty()) return false
        if (_uiState.value.pendingToolConfirmation != null) return false
        val stateAtSend = _uiState.value
        val editing = stateAtSend.editingFailedTurn
        val failedTurn = stateAtSend.failedTurn
        // 编辑失败消息且工具已执行时，先弹出重复执行确认，不直接发送。
        if (editing && failedTurn?.hasToolCalls == true && !stateAtSend.toolReexecutionPending) {
            pendingEditSend = MessageData(text = text, attachments = draftAttachments)
            pendingPriorComposerDraft = stateAtSend.composerSeed
            _uiState.update { it.copy(toolReexecutionPending = true) }
            return false
        }
        val retry = if (editing) failedTurn else null
        val sessionId = _uiState.value.sessionId
        val usableSelection = _uiState.value.currentModelSelection
            ?.takeIf(::isUsableChatSelection)
        if (sessionId.isBlank() || usableSelection == null) {
            viewModelScope.launch {
                val snapshot = runCatching {
                    if (sessionId.isBlank()) {
                        sessionResolver.resolveCurrentOrCreate()
                    } else {
                        sessionResolver.activate(sessionId)
                            ?: sessionResolver.resolveCurrentOrCreate()
                    }
                }.getOrElse {
                    emitNotice(ChatNotice.ConfigureChatModel)
                    return@launch
                }
                validateAttachments(snapshot.modelSelection, draftAttachments)?.let { notice ->
                    emitNotice(notice)
                    return@launch
                }
                val runtime = runtimeFor(snapshot.sessionId)
                runtime.modelSelection = snapshot.modelSelection
                runtime.toolConfiguration = snapshot.toolConfiguration
                if (!runtime.isLoaded) {
                    runtime.messages = repository.loadMessages(snapshot.sessionId).orEmpty()
                    runtime.isLoaded = true
                }
                showRuntime(snapshot.sessionId)
                startSend(
                    snapshot.sessionId,
                    snapshot.modelSelection,
                    text,
                    draftAttachments,
                    retry = retry,
                )
            }
            return true
        }
        validateAttachments(usableSelection, draftAttachments)?.let { notice ->
            emitNotice(notice)
            return false
        }
        val accepted = startSend(sessionId, usableSelection, text, draftAttachments, retry = retry)
        if (accepted && editing) {
            // 编辑提交成功：退出编辑态并清空输入框。
            pendingPriorComposerDraft = null
            _uiState.update {
                it.copy(
                    editingFailedTurn = false,
                    composerSeed = MessageData(),
                )
            }
        }
        return accepted
    }

    private fun startSend(
        sessionId: String,
        selection: ModelSelection,
        text: String,
        draftAttachments: List<DraftAttachment>,
        retry: ChatTurn? = null,
        reuseOriginal: Boolean = false,
    ): Boolean {
        val runtime = runtimeFor(sessionId)
        if (runtime.isActive) return false
        if (sessionRuntimes.values.count { it.isActive } >= MAX_PARALLEL_TASKS) {
            emitNotice(ChatNotice.ParallelTaskLimitReached)
            return false
        }
        clearToolConfirmationState(runtime)
        val runToken = Any()
        runtime.runToken = runToken
        runtime.modelSelection = selection
        runtime.isAgentRunning = true
        runtime.turnComplete = false
        runtime.phase = AgentTaskPhase.GENERATING
        runtime.failed = false
        runtime.attention = SessionResultAttention.NONE
        publishRuntime(runtime)

        runtime.job = viewModelScope.launch {
            try {
                ensureRunLease(runtime).updatePhase(AgentTaskPhase.GENERATING)
            } catch (_: AgentSessionBusyException) {
                emitNotice(ChatNotice.CurrentConversationBusy)
                finishRunIfOwned(sessionId, runToken)
                return@launch
            }
            // Agent 工具可能在上一轮直接更新了当前会话的 MCP 选择；发送前以持久化配置
            // 为准，避免把旧的内存快照继续传给下一轮 Runner。
            loadOrInitializeToolConfiguration(sessionId, selection)
            publishRuntime(runtime)
            val turn = try {
                prepareChatTurn(
                    sessionId = sessionId,
                    text = text,
                    drafts = draftAttachments,
                    history = runtime.messages,
                    retry = retry,
                    reuseOriginal = reuseOriginal,
                )
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                eventReducer.applyError(
                    sessionId,
                    failure.message ?: failure::class.simpleName ?: "Cannot prepare message",
                )
                finishRunIfOwned(sessionId, runToken)
                return@launch
            }
            runtime.lastTurn = turn
            runtime.messages = turn.messages
            runtime.isLoaded = true
            runtime.failed = false
            publishRuntime(runtime)

            val sendText = turn.userMessage.textParts.joinToString("") { it.text }
            try {
                cancellationAwareRunCatching {
                    runner.send(
                        sessionId = sessionId,
                        selection = selection,
                        text = sendText,
                        fileAttachments = turn.userMessage.fileAttachments,
                        toolConfiguration = runtime.toolConfiguration,
                    ).collect { event ->
                        eventReducer.applyEvent(sessionId, event, runToken)
                    }
                }.onSuccess {
                    if (!runtime.failed) {
                        attachments.deleteDrafts(draftAttachments)
                        turnRepository.finish(sessionId, turn.attemptId)
                        runtime.lastTurn = null
                    }
                }.onFailure { failure ->
                    eventReducer.applyError(sessionId, failure.message ?: failure::class.simpleName ?: "Unknown error")
                    saveFailedTurn(sessionId, turn)
                }
            } finally {
                finishRunIfOwned(sessionId, runToken)
                repository.refreshConversation(sessionId)
            }
        }
        return true
    }

    /**
     * 把发送轮落盘为可恢复的 FAILED 轮次（保留部分输出与附件），供错误区的“编辑/重试”恢复。
     * 流式失败与用户主动停止都走这里；重试/编辑时按检查点回退到本轮之前。
     */
    private fun saveFailedTurn(sessionId: String, turn: ChatTurn) {
        val runtime = runtimeFor(sessionId)
        val executedTool = runtime.messages.hasToolCallsAfter(turn.userMessage.id)
        val failed = turn.copy(
            status = ChatTurnStatus.FAILED,
            hasToolCalls = executedTool,
            messages = runtime.messages,
        )
        runtime.lastTurn = failed
        publishRuntime(runtime)
        viewModelScope.launch {
            runCatching { turnRepository.save(failed) }
                .onFailure { Log.w(TAG, "Failed to persist turn record for retry", it) }
        }
    }

    /**
     * 会话加载时恢复失败的发送轮：把中断/失败轮回填为 runtime 的当前展示与可恢复记录，
     * 重启后用户仍能“编辑/重试”。恢复失败不阻断对话加载。
     */
    private suspend fun applyRecoverableTurn(sessionId: String, runtime: ChatSessionRuntime) {
        runCatching { turnRepository.recover(sessionId) }
            .onSuccess { recovered ->
                if (recovered != null) {
                    runtime.lastTurn = recovered
                    runtime.messages = recovered.messages
                }
            }
            .onFailure { Log.w(TAG, "Failed to recover turn record for session $sessionId", it) }
    }

    /** 重新发送最近失败轮次；若工具已执行则先请求用户确认。 */
    private fun retryFailedTurn() {
        val state = _uiState.value
        val failedTurn = state.failedTurn ?: return
        if (state.isAgentRunning || state.editingFailedTurn) return
        val sessionId = state.sessionId
        val selection = state.currentModelSelection?.takeIf(::isUsableChatSelection) ?: run {
            emitNotice(ChatNotice.ConfigureChatModel)
            return
        }
        if (failedTurn.hasToolCalls) {
            pendingRetryRun = true
            _uiState.update { it.copy(toolReexecutionPending = true) }
            return
        }
        startSend(
            sessionId = sessionId,
            selection = selection,
            text = failedTurn.userMessage.textParts.joinToString("") { it.text },
            draftAttachments = emptyList(),
            retry = failedTurn,
            reuseOriginal = true,
        )
    }

    /** 编辑失败消息：把原始文字与附件回填输入框，进入编辑态。 */
    private fun editFailedTurn() {
        val state = _uiState.value
        val failedTurn = state.failedTurn ?: return
        if (state.isAgentRunning || state.editingFailedTurn) return
        val text = failedTurn.userMessage.textParts.joinToString("") { it.text }
        pendingPriorComposerDraft = state.composerSeed
        _uiState.update { it.copy(editingFailedTurn = true) }
        viewModelScope.launch {
            val draftAttachments = runCatching {
                attachments.createDrafts(failedTurn.userMessage.fileAttachments)
            }.getOrElse { failure ->
                emitNotice(ChatNotice.EditDraftsRestoreFailed)
                Log.w(TAG, "Failed to create edit drafts", failure)
                emptyList()
            }
            _uiState.update {
                it.copy(
                    composerSeed = MessageData(text = text, attachments = draftAttachments),
                )
            }
        }
    }

    /** 取消编辑：恢复进入编辑前的草稿，不改动历史；编辑专用草稿副本一并清理。 */
    private fun cancelEditFailedTurn() {
        pendingEditSend = null
        val editDrafts = _uiState.value.composerSeed.attachments
        _uiState.update {
            it.copy(
                editingFailedTurn = false,
                toolReexecutionPending = false,
                composerSeed = pendingPriorComposerDraft ?: MessageData(),
            )
        }
        pendingPriorComposerDraft = null
        if (editDrafts.isNotEmpty()) {
            viewModelScope.launch {
                // deleteDrafts 只删除草稿目录内的临时副本，不会误删已归档的历史附件。
                runCatching { attachments.deleteDrafts(editDrafts) }
                    .onFailure { Log.w(TAG, "Failed to clean up edit drafts", it) }
            }
        }
    }

    /** 处理“重试可能重复执行工具”确认对话框。 */
    private fun resolveRepeatExecution(proceed: Boolean) {
        val state = _uiState.value
        if (!proceed) {
            pendingRetryRun = false
            pendingEditSend = null
            _uiState.update { it.copy(toolReexecutionPending = false) }
            return
        }
        val edit = pendingEditSend
        if (edit != null) {
            pendingEditSend = null
            pendingRetryRun = false
            _uiState.update { it.copy(toolReexecutionPending = false) }
            val failedTurn = state.failedTurn
            val selection = state.currentModelSelection?.takeIf(::isUsableChatSelection)
            val sessionId = state.sessionId
            if (failedTurn == null || selection == null || sessionId.isBlank()) return
            val accepted = startSend(
                sessionId = sessionId,
                selection = selection,
                text = edit.text,
                draftAttachments = edit.attachments,
                retry = failedTurn,
                reuseOriginal = false,
            )
            if (accepted) {
                pendingPriorComposerDraft = null
                _uiState.update {
                    it.copy(
                        editingFailedTurn = false,
                        composerSeed = MessageData(),
                    )
                }
            }
            return
        }
        if (pendingRetryRun) {
            pendingRetryRun = false
            _uiState.update { it.copy(toolReexecutionPending = false) }
            val failedTurn = state.failedTurn ?: return
            val selection = state.currentModelSelection?.takeIf(::isUsableChatSelection) ?: return
            startSend(
                sessionId = state.sessionId,
                selection = selection,
                text = failedTurn.userMessage.textParts.joinToString("") { it.text },
                draftAttachments = emptyList(),
                retry = failedTurn,
                reuseOriginal = true,
            )
        }
    }

    private fun validateAttachments(
        selection: ModelSelection,
        draftAttachments: List<DraftAttachment>,
    ): ChatNotice? {
        if (draftAttachments.isEmpty()) return null
        if (draftAttachments.mapTo(hashSetOf()) { it.category }.size != 1) {
            return ChatNotice.MixedAttachmentCategories
        }
        val model = _uiState.value.availableLLMModelSettings
            .firstOrNull { it.id == selection.serviceId }
            ?.groups?.firstOrNull { it.id == selection.groupId }
            ?.models?.firstOrNull { it.id == selection.modelId }
            ?: return ChatNotice.ChatModelUnavailable
        val (supportedMimeTypes, maxInlineBytes) = when (draftAttachments.first().category) {
            AttachmentCategory.IMAGE -> model.capabilities.vision?.let {
                it.supportedMimeTypes to it.maxInlineBytes
            }
            AttachmentCategory.AUDIO -> model.capabilities.audioInput?.let {
                it.supportedMimeTypes to it.maxInlineBytes
            }
            AttachmentCategory.DOCUMENT -> model.capabilities.documentInput?.let {
                it.supportedMimeTypes to it.maxInlineBytes
            }
        } ?: return ChatNotice.AttachmentCategoryUnsupported
        val unsupported = draftAttachments.firstOrNull {
            it.mimeType !in supportedMimeTypes ||
                maxInlineBytes?.let { limit -> it.sizeBytes > limit } == true
        }
        if (unsupported != null) return ChatNotice.AttachmentUnsupportedOrTooLarge(unsupported.displayName)
        if (
            draftAttachments.first().category == AttachmentCategory.DOCUMENT &&
            draftAttachments.sumOf(DraftAttachment::sizeBytes) > MAX_DOCUMENT_REQUEST_BYTES
        ) {
            return ChatNotice.DocumentTotalSizeLimitExceeded
        }
        return null
    }

    /**
     * 启动期会话恢复：依次尝试
     * 1. 元数据 RoomDatabase 中 `isLast=true` 的 id（仍在 ADK Room 中）；
     * 2. Room 中 `lastUpdateTime` 最大的会话（即最近活跃的）；
     * 3. 创建一个新的空会话（首次安装 / 全部被删的兜底）。
     *
     * 供 [ChatRoute] 在 `LaunchedEffect(Unit)` 内调用，让首屏打字前已经有可用 sessionId，
     * 避免依赖 `send()` 的兜底分支。仅在进程级（`_uiState.value.sessionId` 为空）执行一次；同一 ViewModel 实例内多次调用安全。
     */
    private fun restoreOrCreateSession() {
        if (_uiState.value.sessionId.isNotBlank()) return
        // 同步先把 isInitializing 置 true：让 MainScreen 在第一次 collect 时就拿到
        // 加载态，避免 history commit 之前出现"旧 messages 残留 → 新 history"的
        // 单帧闪烁。三个分支都在 commit / 兜底结束时各自把它置回 false。
        _uiState.update { it.copy(isInitializing = true) }
        viewModelScope.launch {
            modelServices.awaitReady()
            runCatching { sessionResolver.resolveCurrentOrCreate() }
                .onSuccess { snapshot ->
                    val history = repository.loadMessages(snapshot.sessionId).orEmpty()
                    val runtime = runtimeFor(snapshot.sessionId)
                    runtime.messages = history
                    applyRecoverableTurn(snapshot.sessionId, runtime)
                    runtime.modelSelection = snapshot.modelSelection
                    runtime.toolConfiguration = snapshot.toolConfiguration
                    runtime.isLoaded = true
                    showRuntime(snapshot.sessionId)
                    Log.i(
                        TAG,
                        "restoreOrCreateSession: resolved current id=${snapshot.sessionId} " +
                            "(events=${history.size}).",
                    )
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isInitializing = false) }
                    Log.w(TAG, "restoreOrCreateSession: unable to resolve current conversation.", error)
                }
        }
    }

    /**
     * 开始一个全新的会话 — 调 [ConversationRepository.createConversation] 创建并切到新会话。
     *
     * 即使 [ConversationRepository.createConversation] 失败（例如 Room 暂时不可用），也先清掉
     * 上一会话遗留的 [partChannels]，避免 channel 跨"空 session"残留。
     */
    private fun reset() {
        viewModelScope.launch {
            modelServices.awaitReady()
            val newId = createConversationWithDefaults()
            if (newId.isNotBlank()) {
                switchSessionUnchecked(newId)
            } else {
                Log.w(TAG, "reset() failed to create a new conversation; UI state unchanged.")
            }
        }
    }

    /**
     * 切换到指定 session
     */
    private fun switchSession(sessionId: String) {
        if (sessionId.isBlank()) return
        switchSessionUnchecked(sessionId)
    }

    private fun switchSessionUnchecked(sessionId: String) {
        if (sessionId.isBlank()) return
        if (sessionId == _uiState.value.sessionId && !_uiState.value.isInitializing) return
        sessionRuntimes[_uiState.value.sessionId]?.closePartChannels()
        sessionLoadJob?.cancel()
        speechPlaybackController.clearSession()
        val loadToken = Any()
        activeSessionLoadToken = loadToken
        loadingSessionId = sessionId
        if (pendingContentRefreshSessionId != sessionId) pendingContentRefreshSessionId = null
        // isAgentRunning=false 解锁输入；turnComplete=false 重置上一轮的徽章；isInitializing=true
        // 让 MainScreen 中央 spinner 立刻接管，避免 history commit 之前旧 messages 残留闪烁。
        val targetRuntime = runtimeFor(sessionId)
        showRuntime(sessionId, isInitializing = !targetRuntime.isLoaded)
        sessionLoadJob = viewModelScope.launch {
            try {
                modelServices.awaitReady()
                targetRuntime.modelSelection = activateConversationSettings(sessionId)
                if (targetRuntime.isLoaded) {
                    targetRuntime.attention = SessionResultAttention.NONE
                    targetRuntime.reseedPartialChannels()
                    showRuntime(sessionId)
                    return@launch
                }
                val messages = repository.loadMessages(sessionId)
                if (activeSessionLoadToken !== loadToken) return@launch
                when {
                    messages == null -> {
                        Log.i(TAG, "switchSession($sessionId): session missing; creating a fresh one.")
                        repository.discardConversationMetadata(sessionId)
                        val newId = createConversationWithDefaults()
                        if (activeSessionLoadToken !== loadToken) return@launch
                        if (newId.isNotBlank()) {
                            val newRuntime = runtimeFor(newId)
                            newRuntime.modelSelection = activateConversationSettings(newId)
                            newRuntime.isLoaded = true
                            if (activeSessionLoadToken !== loadToken) return@launch
                            showRuntime(newId)
                        } else {
                            // create 失败也别把 spinner 永久卡住 — 解锁 UI 让用户能重试。
                            _uiState.update { it.copy(isInitializing = false) }
                            Log.w(TAG, "switchSession($sessionId): createConversation failed; isInitializing cleared anyway.")
                        }
                    }

                    else -> {
                        // 命中：history 非空 = 旧 session；history 空 = 刚建的空 session。
                        // 一次性 commit sessionId + messages + isInitializing=false，
                        // 避免两次 messages 写导致两帧渲染。
                        if (activeSessionLoadToken !== loadToken) return@launch
                        targetRuntime.messages = messages
                        applyRecoverableTurn(sessionId, targetRuntime)
                        targetRuntime.isLoaded = true
                        targetRuntime.turnComplete = false
                        targetRuntime.attention = SessionResultAttention.NONE
                        targetRuntime.reseedPartialChannels()
                        showRuntime(sessionId)
                    }
                }
            } finally {
                if (activeSessionLoadToken === loadToken) {
                    activeSessionLoadToken = null
                    loadingSessionId = null
                    sessionLoadJob = null
                    flushPendingConversationContentUpdate(expectedSessionId = sessionId)
                }
            }
        }
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
            runner.releaseSession(sessionId)
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
            initializeOfficialFunctionsForSelection(sessionId, runtime, selection)
            runner.releaseSession(sessionId)
            publishRuntime(runtime)
        }
    }

    /**
     * 把指定会话持久化的配置装载为当前运行时配置，并重建后续消息使用的 agent。
     *
     * 已保存且仍可用的模型优先；新会话或模型已失效时使用当前默认模型。若没有任何
     * 可用模型，则清空运行时选择和旧 runner，保留空会话等待用户完成模型配置。
     */
    private suspend fun activateConversationSettings(sessionId: String): ModelSelection? {
        val snapshot = sessionResolver.activate(sessionId)
        if (snapshot == null) {
            runner.releaseSession(sessionId)
            Log.w(TAG, "activateConversationSettings($sessionId): conversation is unavailable.")
            return null
        }
        runtimeFor(sessionId).toolConfiguration = snapshot.toolConfiguration
        return snapshot.modelSelection
    }

    private suspend fun createConversationWithDefaults(): String {
        val snapshot = sessionResolver.createAndActivate()
        runtimeFor(snapshot.sessionId).apply {
            modelSelection = snapshot.modelSelection
            toolConfiguration = snapshot.toolConfiguration
        }
        return snapshot.sessionId
    }

    private suspend fun loadOrInitializeToolConfiguration(
        sessionId: String,
        selection: ModelSelection?,
    ) {
        val runtime = runtimeFor(sessionId)
        if (selection == null) {
            runtime.toolConfiguration = ConversationToolConfiguration()
            return
        }
        runCatching {
            sessionResolver.resolveToolConfiguration(sessionId, selection)
        }.onSuccess { configuration ->
            runtime.toolConfiguration = configuration
            _uiState.update { it.copy(hasToolConfigurationError = false) }
        }.onFailure {
            _uiState.update { it.copy(hasToolConfigurationError = true) }
        }
    }

    private suspend fun initializeOfficialFunctionsForSelection(
        sessionId: String,
        runtime: ChatSessionRuntime,
        selection: ModelSelection,
    ) {
        val current = runtime.toolConfiguration
            ?: sessionResolver.resolveToolConfiguration(sessionId, selection)
        val initialized = current.initializeOfficialFunctions(
            supportedOfficialToolIds(selection),
        )
        if (initialized != current) {
            if (repository.setConversationToolConfiguration(sessionId, initialized)) {
                runtime.toolConfiguration = initialized
            } else {
                _uiState.update {
                    it.copy(hasToolConfigurationError = true)
                }
            }
        } else {
            runtime.toolConfiguration = current
        }
    }

    private fun setToolAccessMode(mode: ToolAccessMode) {
        updateToolConfiguration { configuration ->
            configuration.copy(toolAccessMode = mode)
        }
    }

    private fun setReasoningEffort(effort: ReasoningEffort) {
        updateToolConfiguration { configuration ->
            configuration.copy(reasoningEffort = effort)
        }
    }

    private fun setMcpServerEnabled(serverId: String, enabled: Boolean) {
        updateToolConfiguration { configuration ->
            configuration.copy(
                enabledMcpServerIds = if (enabled) {
                    configuration.enabledMcpServerIds + serverId
                } else {
                    configuration.enabledMcpServerIds - serverId
                },
            )
        }
    }

    /**
     * Toggle a single function of an official tool. The caller passes the
     * current catalog of ids so the marker can be expanded before the write.
     */
    private fun setOfficialFunctionEnabled(
        toolId: String,
        functionId: String,
        enabled: Boolean,
        supportedFunctionIds: Set<String>,
    ) {
        val selection = _uiState.value.currentModelSelection ?: return
        updateToolConfiguration { configuration ->
            configuration.setOfficialFunctionEnabled(
                toolId = toolId,
                functionId = functionId,
                supportedFunctionIds = supportedFunctionIds,
                enabled = enabled,
            )
        }
    }

    /**
     * Trigger an async load of the function list for [toolId]. Already loaded
     * tools only re-run their marker expansion (no network call). On success,
     * the configuration's marker entry for the tool is replaced with the real
     * function ids so persistence stays concrete.
     */
    private fun loadOfficialToolFunctions(toolId: String) {
        val descriptors = _uiState.value.officialToolDescriptors
        val target = descriptors.firstOrNull { it.id == toolId } ?: return
        if (target.isLoadingFunctions) return
        if (target.functions.isNotEmpty() && target.loadError == null) {
            expandMarkerAfterLoad(target)
            return
        }
        fetchAndCacheOfficialToolFunctions(toolId)
    }

    /**
     * Walk every descriptor and, for tools whose configuration still uses the
     * [ConversationToolConfiguration.ALL_FUNCTIONS_MARKER] sentinel, fetch the
     * function list in the background so the marker is expanded to concrete
     * ids without forcing the user to open each sub-page first.
     */
    private fun scheduleMarkerExpansion() {
        val configuration = _uiState.value.toolConfiguration ?: return
        val selection = _uiState.value.currentModelSelection ?: return
        val descriptors = _uiState.value.officialToolDescriptors
        descriptors.forEach { descriptor ->
            val raw = configuration.enabledOfficialFunctionIds(descriptor.id)
            val needsExpansion = ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in raw
            val alreadyLoaded = descriptor.functions.isNotEmpty()
            if (!needsExpansion) return@forEach
            if (alreadyLoaded) {
                expandMarkerAfterLoad(descriptor)
                return@forEach
            }
            if (descriptor.isLoadingFunctions || descriptor.loadError != null) return@forEach
            fetchAndCacheOfficialToolFunctions(descriptor.id)
        }
    }

    private fun fetchAndCacheOfficialToolFunctions(toolId: String) {
        _uiState.update { state ->
            state.copy(
                officialToolDescriptors = state.officialToolDescriptors.map { existing ->
                    if (existing.id == toolId) {
                        existing.copy(isLoadingFunctions = true, loadError = null)
                    } else {
                        existing
                    }
                },
            )
        }
        viewModelScope.launch {
            val outcome = cancellationAwareRunCatching { officialFunctionCatalog.listFunctions(toolId) }
            val functions = outcome.getOrDefault(emptyList())
            val loadError = outcome.exceptionOrNull()?.message
            _uiState.update { state ->
                state.copy(
                    officialToolDescriptors = state.officialToolDescriptors.map { existing ->
                        if (existing.id == toolId) {
                            existing.copy(
                                functions = functions,
                                isLoadingFunctions = false,
                                loadError = loadError,
                            )
                        } else {
                            existing
                        }
                    },
                )
            }
            if (functions.isNotEmpty()) {
                expandMarkerAfterLoad(
                    OfficialToolDescriptor(id = toolId, functions = functions),
                )
            }
        }
    }

    private fun expandMarkerAfterLoad(tool: OfficialToolDescriptor) {
        val selection = _uiState.value.currentModelSelection ?: return
        val configuration = _uiState.value.toolConfiguration ?: return
        val ids = configuration.enabledOfficialFunctionIds(tool.id)
        if (ConversationToolConfiguration.ALL_FUNCTIONS_MARKER !in ids) return
        if (tool.functions.isEmpty()) return
        updateToolConfiguration { configuration ->
            configuration.expandOfficialFunctionsMarker(
                tool.id,
                tool.functions.mapTo(hashSetOf()) { it.id },
            )
        }
    }

    private fun buildOfficialToolDescriptors(
        selection: ModelSelection?,
        existing: List<OfficialToolDescriptor>,
    ): List<OfficialToolDescriptor> {
        val ids = supportedOfficialToolIds(selection)
        if (ids.isEmpty()) return emptyList()
        val existingById = existing.associateBy { it.id }
        return ids.map { id ->
            existingById[id] ?: OfficialToolDescriptor(id = id)
        }
    }

    private fun clearToolConfigurationError() {
        _uiState.update { it.copy(hasToolConfigurationError = false) }
    }

    private fun updateToolConfiguration(
        transform: (ConversationToolConfiguration) -> ConversationToolConfiguration,
    ) {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return
        val runtime = runtimeFor(sessionId)
        if (runtime.isActive) return
        val current = runtime.toolConfiguration ?: return
        val updated = transform(current)
        if (updated == current) return
        viewModelScope.launch {
            if (repository.setConversationToolConfiguration(sessionId, updated)) {
                runtime.toolConfiguration = updated
                runner.releaseSession(sessionId)
                _uiState.update { it.copy(hasToolConfigurationError = false) }
                publishRuntime(runtime)
            } else {
                _uiState.update {
                    it.copy(hasToolConfigurationError = true)
                }
            }
        }
    }

    /**
     * 当前选择支持的官方工具 ID(厂商唯一)。官方工具支持矩阵由 agent 层的
     * [OfficialToolFunctionCatalog] 实现维护,按服务 + 当前协议查询。
     */
    private fun supportedOfficialToolIds(selection: ModelSelection?): Set<String> {
        val current = selection ?: return emptySet()
        val service = modelServices.currentServices()
            .firstOrNull { it.id == current.serviceId }
            ?: return emptySet()
        return officialFunctionCatalog.supportedToolIds(current.serviceId, service.apiProtocol)
    }

    private fun isUsableChatSelection(selection: ModelSelection): Boolean =
        modelServices.currentServices().isUsableChatSelection(selection)

    /**
     * 用户主动中断当前 turn（点击 composer 上的停止按钮）。
     *
     * 与 [send] 的 `finally` 块相比，这里**同步**把 `isAgentRunning` 置 false —
     * `finally` 是协程挂起后才跑，UI 会延迟一帧才解锁输入框，用户感知明显；
     * 提前在取消的同一帧更新 state 让 stop 按钮 → 输入框 enable 的过渡即时可见。
     *
     * `partial = false` 让未完成的 assistant message 在 UI 上结束流式渲染。
     * 用户取消不是正常完成，所以 `turnComplete` 保持 false。
     *
     * 同步把仍在 `partial` 状态的 assistant message 翻成 `partial = false`：因为是用户主动
     * 中断,不会有 final non-partial event 到达来触发 [appendCompleteEvent] 的就地翻标志位;
     * 如果不在这里手动翻,那条 message 会一直停留在 `partial = true`,用户后续滚动离开再
     * 滚回时,LazyColumn 重新 Composition 后 [ChatTextContent] 会用 `partial = true && chunkChannel != null`
     * 落到 streaming 路径,但本地 `streamingState` 已被重置为空 → 气泡内容丢失。和流式自然
     * 完成的滚动回看场景是同一个 root cause family,这里一并兜底。
     *
     * 没有进行中的 job 时直接 no-op，避免在非 streaming 状态误触。
     */
    private fun stopStreaming() {
        val sessionId = _uiState.value.sessionId
        val runtime = sessionRuntimes[sessionId] ?: return
        if (runtime.job?.isActive != true && runtime.pendingToolConfirmations.isEmpty()) return
        if (runtime.pendingToolConfirmations.isNotEmpty()) {
            runtime.approvedToolsThisTurn.clear()
            respondToToolConfirmation(sessionId, confirmed = false)
            return
        }
        cancelRun(runtime)
        runtime.messages = runtime.messages.map { msg ->
            if (msg.partial && msg.role == MessageRole.Assistant) {
                msg.copy(partial = false, turnComplete = false)
            } else {
                msg
            }
        }
        runtime.isAgentRunning = false
        runtime.turnComplete = false
        runtime.attention = SessionResultAttention.NONE
        // 用户主动停止的轮次同样保留“编辑/重试”：把已生成的部分回答一并落盘为可恢复轮，
        // 重试/编辑时按检查点回退到本轮之前（与失败轮语义一致）。
        runtime.lastTurn?.takeIf { it.status == ChatTurnStatus.RUNNING }?.let { stopped ->
            saveFailedTurn(sessionId, stopped)
        }
        publishRuntime(runtime)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    companion object {
        private const val TAG: String = "ChatViewModel"
        private const val MAX_PARALLEL_TASKS: Int = 3
        private const val MAX_DOCUMENT_REQUEST_BYTES: Long = 50L * 1024 * 1024
    }
}

private fun List<LLMModelSetting>.isUsableChatSelection(selection: ModelSelection): Boolean {
    val service = firstOrNull { it.id == selection.serviceId } ?: return false
    if (!service.isEnabled || service.apiKey.isBlank()) return false
    val group = service.groups.firstOrNull { it.id == selection.groupId } ?: return false
    val model = group.models.firstOrNull { it.id == selection.modelId } ?: return false
    return !model.isStt && !model.isTts
}

/** 最近一轮若处于失败/中断态，则可作为“编辑/重试”的可恢复轮。 */
private fun ChatSessionRuntime.failedRecoverableTurn(): ChatTurn? =
    lastTurn?.takeIf {
        it.status == ChatTurnStatus.FAILED || it.status == ChatTurnStatus.INTERRUPTED
    }

/**
 * 判断本轮（指定用户消息之后的消息）是否发起过实际工具调用；工具调用只挂在
 * assistant 消息上，历史轮的调用不计入，否则重试会误报“重复执行”确认框。
 */
internal fun List<Message>.hasToolCallsAfter(userMessageId: String): Boolean {
    val userIndex = indexOfFirst { it.id == userMessageId }
    if (userIndex < 0) return false
    return subList(userIndex + 1, size).any { message ->
        message.functionCalls.isNotEmpty() || message.functionResponses.isNotEmpty()
    }
}
