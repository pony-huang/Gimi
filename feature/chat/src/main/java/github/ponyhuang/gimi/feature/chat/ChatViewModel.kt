package github.ponyhuang.gimi.feature.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.domain.conversation.usecase.ChatRunEventMapper
import github.ponyhuang.gimi.domain.conversation.usecase.summarizeValue
import github.ponyhuang.gimi.domain.conversation.usecase.toView
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
import github.ponyhuang.gimi.domain.speech.usecase.markdownToSpeechText
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * 聊天页 ViewModel — 维护消息列表并把 ADK `Event` 流合并到 UI 友好的 `Message` 模型。
 *
 * 核心算法（参考 `~/.claude/projects/E--workplace-adk-web/memory/chat-streaming-and-thought.md`）：
 * 1. `applyEvent(event)` — 顶层分发：partial 事件合并到上一条；否则当作完整事件。
 * 2. `mergePartialEvent(last, event)` — 把当前 partial 的所有 part 累积进 last。
 * 3. `addTextToParts(message, text, thought)` — 同 thought 标志合并到末段；异则新建段。
 *
 * 持久化层：`buildMessageFromParts` 改走 `EventMapper.fromEvent(event)`，保证 streaming 与历史回放共用 `Event.id → Message.id` 映射。
 * 会话管理：通过 [ConversationRepository] 完成"新建 / 切换 / 删除 / 拉取会话列表"；`reset()` 与 `switchSession()` 都走 repository。
 *
 * 取消语义：每次 `send` 取消 `currentJob`，避免 partial 流交错。
 *
 * DI：通过 Hilt 注入 [AgentChatRunner] / [ConversationRepository]；UI 端用
 * `hiltViewModel()` 直接拿到实例，不再走原先的 `ChatViewModel.factory(context)`。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val runner: ChatAgentRepository,
    private val agentRuntimeGate: AgentRuntimeGate,
    private val repository: ConversationRepository,
    private val modelServices: ModelCatalogRepository,
    private val chatDisplayPreferences: ChatDisplayRepository,
    private val toolApproval: ToolApprovalRepository,
    private val toolAuthorization: ToolAuthorizationRepository,
    private val mcpRepository: McpRepository,
    private val mcpSkipReporter: McpSkipReporter,
    private val speechRecognitionRepository: SpeechRecognitionRepository,
    private val speechPlaybackController: SpeechPlaybackRepository,
    private val attachments: ChatAttachmentRepository,
    private val officialFunctionCatalog: OfficialToolFunctionCatalog,
    private val memoryRuntimeStatus: MemoryRuntimeStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    private val sessionCreationMutex = Mutex()
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 8)

    /** 一次性 UI 反馈通道（Toast 等），由 Route 消费；见 [ChatEffect]。 */
    val effects = _effects.asSharedFlow()

    /**
     * 用户意图统一入口 — 所有"发后即忘"的用户操作都经这里分发，见 [ChatAction]。
     */
    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.Send -> send(action.text, action.draftAttachments)
            ChatAction.StopStreaming -> stopStreaming()
            is ChatAction.ToggleSpeechPlayback ->
                toggleSpeechPlayback(action.messageId, action.markdown)
            is ChatAction.RespondToToolConfirmation ->
                respondToToolConfirmation(action.confirmed, action.alwaysAllow)
            is ChatAction.SetFullAccess -> setFullAccess(action.enabled)
            ChatAction.RestoreOrCreateSession -> restoreOrCreateSession()
            ChatAction.NewConversation -> reset()
            is ChatAction.SwitchSession -> switchSession(action.sessionId)
            ChatAction.RefreshConversations -> refreshConversations()
            is ChatAction.DeleteConversation -> deleteConversation(action.sessionId)
            is ChatAction.SelectModel -> selectModel(action.selection)
            is ChatAction.SetLocalToolEnabled ->
                setLocalToolEnabled(action.toolId, action.enabled)
            is ChatAction.SetToolAccessMode -> setToolAccessMode(action.mode)
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
                chatDisplayPreferences.setDarkThemeOverride(action.enabled)
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
                        applyEvent(sessionId, event, runToken)
                    }
                }.onFailure { failure ->
                    applyError(sessionId, failure.message ?: failure::class.simpleName ?: "Unknown error")
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
            toolAuthorization.tools.collect { tools ->
                _uiState.update { it.copy(availableLocalTools = tools) }
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
            chatDisplayPreferences.darkThemeOverride.collect { override ->
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

    private val sessionRuntimes = linkedMapOf<String, ChatSessionRuntime>()
    private var sessionLoadJob: Job? = null
    private var activeSessionLoadToken: Any? = null
    private var loadingSessionId: String? = null
    private var pendingContentRefreshSessionId: String? = null
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
            }
            publishRuntime(runtime)
        }
        viewModelScope.launch { flushPendingConversationContentUpdate() }
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
     *   [ConversationRepository.createConversation] 建一个再走 [AgentChatRunner.send]，
     *   避免 ADK `createSession(SessionKey(id = ""))` 抛 "SessionKey.id must not be blank"。
     * - 启动协程调用 [AgentChatRunner.send]，把每个 `Event` 送入 [applyEvent]。
     */
    fun send(text: String, draftAttachments: List<DraftAttachment> = emptyList()): Boolean {
        if (text.isBlank() && draftAttachments.isEmpty()) return false
        if (_uiState.value.pendingToolConfirmation != null) return false
        val usableSelection = _uiState.value.currentModelSelection
            ?.takeIf(::isUsableChatSelection)
            ?: defaultSelection()
        if (usableSelection == null) {
            emitNotice(ChatNotice.ConfigureChatModel)
            return false
        }
        validateAttachments(usableSelection, draftAttachments)?.let { notice ->
            emitNotice(notice)
            return false
        }
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) {
            viewModelScope.launch {
                val ensured = ensureSessionId()
                if (ensured.isNotBlank()) {
                    startSend(ensured, usableSelection, text, draftAttachments)
                }
            }
            return true
        }
        return startSend(sessionId, usableSelection, text, draftAttachments)
    }

    private fun startSend(
        sessionId: String,
        selection: ModelSelection,
        text: String,
        draftAttachments: List<DraftAttachment>,
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
            ensureRunLease(runtime).updatePhase(AgentTaskPhase.GENERATING)
            // Agent 工具可能在上一轮直接更新了当前会话的 MCP 选择；发送前以持久化配置
            // 为准，避免把旧的内存快照继续传给下一轮 Runner。
            loadOrInitializeToolConfiguration(sessionId, selection)
            publishRuntime(runtime)
            val preparedAttachments = cancellationAwareRunCatching {
                attachments.read(sessionId, draftAttachments)
            }.getOrElse { failure ->
                applyError(sessionId, "Cannot read selected attachment: ${failure.message ?: "unknown error"}",)
                finishRunIfOwned(sessionId, runToken)
                return@launch
            }
            val userMessage = Messages.fromUser(text = text, fileAttachments = preparedAttachments,)
            runtime.messages += userMessage
            runtime.isLoaded = true
            publishRuntime(runtime)
            try {
                cancellationAwareRunCatching {
                    runner.send(
                        sessionId = sessionId,
                        selection = selection,
                        text = text,
                        fileAttachments = preparedAttachments,
                        toolConfiguration = runtime.toolConfiguration,
                    ).collect { event ->
                        applyEvent(sessionId, event, runToken)
                    }
                }.onSuccess {
                    if (!runtime.failed) attachments.deleteDrafts(draftAttachments)
                }.onFailure { failure ->
                    applyError(sessionId, failure.message ?: failure::class.simpleName ?: "Unknown error")
                }
            } finally {
                finishRunIfOwned(sessionId, runToken)
                repository.refreshConversation(sessionId)
            }
        }
        return true
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
     * 兜底：保证 [sessionId] 非空。当前值为空时调 [ConversationRepository.createConversation] 建一个。
     *
     * 注：是 `suspend` 内部方法，调用方必须在协程里调用以确保 `_uiState.value.sessionId` 已更新后再继续。
     */
    private suspend fun ensureSessionId(): String = sessionCreationMutex.withLock {
        modelServices.awaitReady()
        _uiState.value.sessionId.takeIf { it.isNotBlank() }?.let { return@withLock it }
        val newId = createConversationWithDefaults()
        if (newId.isNotBlank()) {
            val runtime = runtimeFor(newId)
            runtime.modelSelection = activateConversationSettings(newId)
            runtime.isLoaded = true
            showRuntime(newId)
        }
        _uiState.value.sessionId
    }

    /**
     * 启动期会话恢复：依次尝试
     * 1. 元数据 RoomDatabase 中 `isLast=true` 的 id（仍在 ADK Room 中）；
     * 2. Room 中 `lastUpdateTime` 最大的会话（即最近活跃的）；
     * 3. 创建一个新的空会话（首次安装 / 全部被删的兜底）。
     *
     * 供 [MainScreen] 在 `LaunchedEffect(Unit)` 内调用，让首屏打字前已经有可用 sessionId，
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
            // 1. 元数据中的当前会话优先 — 仅当 ADK Room 里仍存在该 session 才算命中。
            val remembered = repository.lastConversationId()
            if (remembered != null) {
                val history = repository.loadMessages(remembered)
                if (history != null) {
                    val runtime = runtimeFor(remembered)
                    runtime.messages = history
                    runtime.modelSelection = activateConversationSettings(remembered)
                    runtime.isLoaded = true
                    showRuntime(remembered)
                    Log.i(TAG, "restoreOrCreateSession: restored from prefs id=$remembered (events=${history.size}).")
                    return@launch
                } else {
                    repository.discardConversationMetadata(remembered)
                    Log.i(TAG, "restoreOrCreateSession: remembered id=$remembered missing; cleared metadata.")
                }
            }

            // 2. fallback：取 Room 里最近活跃的会话。
            val mostRecent = repository.listConversations().firstOrNull()
            if (mostRecent != null) {
                // switchSession 内部会自己维护 isInitializing（置 true → false）。
                switchSession(mostRecent.id)
                Log.i(TAG, "restoreOrCreateSession: fell back to most recent id=${mostRecent.id}.")
                return@launch
            }

            // 3. 兜底：数据库里一个 session 都没有，建一个空会话（首次安装 / 全删场景）。
            val newId = createConversationWithDefaults()
            if (newId.isNotBlank()) {
                val runtime = runtimeFor(newId)
                runtime.modelSelection = activateConversationSettings(newId)
                runtime.isLoaded = true
                showRuntime(newId)
                Log.i(TAG, "restoreOrCreateSession: created fresh session id=$newId.")
            } else {
                // create 失败也别把 spinner 永久卡住。
                _uiState.update { it.copy(isInitializing = false) }
                Log.w(TAG, "restoreOrCreateSession: failed to create a fresh session; isInitializing cleared anyway.")
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

    private fun refreshCurrentConversation() {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return
        viewModelScope.launch { repository.refreshConversation(sessionId) }
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
        modelServices.awaitReady()
        val storedModel = repository.activateConversation(sessionId, defaultModelPayload())
        val savedSelection = ModelSelectionCodec.decode(storedModel)
        val validSavedSelection = savedSelection?.takeIf {
            isUsableChatSelection(it)
        }
        val selection = validSavedSelection ?: defaultSelection()
        if (savedSelection != selection) {
            repository.setConversationModel(
                sessionId = sessionId,
                model = selection?.let(ModelSelectionCodec::encode).orEmpty(),
            )
        }
        if (selection == null) {
            runner.releaseSession(sessionId)
            Log.w(TAG, "activateConversationSettings($sessionId): no available model.")
        }
        loadOrInitializeToolConfiguration(sessionId, selection)
        return selection
    }

    private suspend fun createConversationWithDefaults(): String {
        val selection = defaultSelection()
        return repository.createConversation(
            initialModel = selection?.let(ModelSelectionCodec::encode).orEmpty(),
            initialToolConfiguration = defaultToolConfiguration(selection),
        )
    }

    private fun defaultToolConfiguration(
        selection: ModelSelection?,
    ): ConversationToolConfiguration {
        val officialSelections = selection?.let { current ->
            mapOf(
                current.serviceId to supportedOfficialToolIds(current)
                    .associateWith { setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER) },
            )
        }.orEmpty()
        return ConversationToolConfiguration(
            enabledLocalToolIds = toolAuthorization.enabledToolIds(),
            enabledMcpServerIds = mcpRepository.currentServers()
                .filter { it.isEnabled }
                .mapTo(linkedSetOf()) { it.id },
            enabledOfficialFunctionIdsByService = officialSelections,
        )
    }

    private suspend fun loadOrInitializeToolConfiguration(
        sessionId: String,
        selection: ModelSelection?,
    ) {
        val runtime = runtimeFor(sessionId)
        val stored = repository.conversationToolConfiguration(sessionId)
        val initial = stored ?: defaultToolConfiguration(selection)
        val availableLocalIds = toolAuthorization.tools.value.mapTo(hashSetOf()) { it.id }
        val availableMcpIds = mcpRepository.currentServers().mapTo(hashSetOf()) { it.id }
        val sanitized = initial.sanitize(availableLocalIds, availableMcpIds)
        val initialized = selection?.let { current ->
            sanitized.initializeOfficialFunctions(
                current.serviceId,
                supportedOfficialToolIds(current),
            )
        } ?: sanitized
        runtime.toolConfiguration = initialized
        if (stored != initialized) {
            val saved = repository.setConversationToolConfiguration(sessionId, initialized)
            if (!saved) {
                _uiState.update {
                    it.copy(hasToolConfigurationError = true)
                }
            }
        }
    }

    private suspend fun initializeOfficialFunctionsForSelection(
        sessionId: String,
        runtime: ChatSessionRuntime,
        selection: ModelSelection,
    ) {
        val current = runtime.toolConfiguration ?: defaultToolConfiguration(selection)
        val initialized = current.initializeOfficialFunctions(
            selection.serviceId,
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

    private fun setLocalToolEnabled(toolId: String, enabled: Boolean) {
        updateToolConfiguration { configuration ->
            configuration.copy(
                enabledLocalToolIds = if (enabled) {
                    configuration.enabledLocalToolIds + toolId
                } else {
                    configuration.enabledLocalToolIds - toolId
                },
            )
        }
    }

    private fun setToolAccessMode(mode: ToolAccessMode) {
        updateToolConfiguration { configuration ->
            configuration.copy(toolAccessMode = mode)
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
                selection.serviceId,
                toolId,
                functionId,
                supportedFunctionIds,
                enabled,
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
            val raw = configuration.enabledOfficialFunctionIds(selection.serviceId, descriptor.id)
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
        val ids = configuration.enabledOfficialFunctionIds(selection.serviceId, tool.id)
        if (ConversationToolConfiguration.ALL_FUNCTIONS_MARKER !in ids) return
        if (tool.functions.isEmpty()) return
        updateToolConfiguration { configuration ->
            configuration.expandOfficialFunctionsMarker(
                selection.serviceId,
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
        val current = runtime.toolConfiguration ?: defaultToolConfiguration(runtime.modelSelection)
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

    private fun supportedOfficialToolIds(selection: ModelSelection?): Set<String> =
        selection?.let { current ->
            modelServices.currentServices()
                .firstOrNull { it.id == current.serviceId }
                ?.supportedOfficialTools
                ?.toSet()
        }.orEmpty()

    /** New conversations use the saved default assistant model, with a first-available fallback. */
    private fun defaultModelPayload(): String = defaultSelection()
        ?.let(ModelSelectionCodec::encode)
        .orEmpty()

    private fun defaultSelection(): ModelSelection? {
        val services = modelServices.currentServices()
        return modelServices.currentAssistantSelection()
            ?.takeIf { selection -> services.isUsableChatSelection(selection) }
            ?: services.asSequence()
                .filter { it.isEnabled && it.apiKey.isNotBlank() }
                .flatMap { service ->
                    service.groups.asSequence().flatMap { group ->
                        group.models.asSequence()
                            .filter { !it.isStt && !it.isTts }
                            .map { ModelSelection(service.id, group.id, it.id) }
                    }
                }
                .firstOrNull()
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
     * 滚回时,LazyColumn 重新 Composition 后 [TextContent] 会用 `partial = true && chunkChannel != null`
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
        publishRuntime(runtime)
    }

    // ── Reducer ────────────────────────────────────────────────────────────

    /**
     * 顶层 reducer：partial 合并，否则当作完整事件构造新的 `Message`。
     */
    private fun applyEvent(sessionId: String, event: ChatRunEvent, runToken: Any) {
        if (sessionRuntimes[sessionId]?.runToken !== runToken) return
        // 错误优先：errorCode / errorMessage 非空 → 错误消息。
        val errMsg = event.errorMessage
        if (event.errorCode != null || !errMsg.isNullOrBlank()) {
            applyError(sessionId, errMsg ?: event.errorCode ?: "Unknown error", event.invocationId)
            return
        }

        if (event.partial) {
            mergePartialEvent(sessionId, event)
        } else {
            appendCompleteEvent(sessionId, event)
        }
        applyAgentRunEvent(sessionId, event)
        captureToolConfirmation(sessionId, event)
    }

    private fun applyAgentRunEvent(sessionId: String, event: ChatRunEvent) {
        val runtime = runtimeFor(sessionId)
        val phase = when {
            event.functionCalls.any { it.confirmationRequest == null } -> AgentTaskPhase.EXECUTING_TOOL
            event.functionResponses.isNotEmpty() -> AgentTaskPhase.GENERATING
            else -> null
        }
        if (phase != null) {
            runtime.phase = phase
            viewModelScope.launch { runtime.lease?.updatePhase(phase) }
        }
        val status = AgentRunStatus(
            isRunning = runtime.isAgentRunning,
            turnComplete = runtime.turnComplete,
        ).afterEvent(
            partial = event.partial,
            turnComplete = event.turnComplete,
        )
        runtime.isAgentRunning = status.isRunning
        runtime.turnComplete = status.turnComplete
        publishRuntime(runtime)
        if (event.turnComplete) {
            viewModelScope.launch { repository.refreshConversation(sessionId) }
        }
    }

    /** Extracts queued ADK confirmation requests without allowing later calls to overwrite them. */
    private fun captureToolConfirmation(sessionId: String, event: ChatRunEvent) {
        val runtime = runtimeFor(sessionId)
        val incoming = event.functionCalls.mapNotNull { call ->
            val confirmationId = call.id ?: return@mapNotNull null
            val request = call.confirmationRequest ?: return@mapNotNull null
            val description = toolAuthorization.tools.value
                .firstOrNull { it.id == request.toolName }
                ?.description
                ?: ""
            PendingToolConfirmation(
                confirmationCallId = confirmationId,
                toolName = request.toolName,
                description = description,
                arguments = request.args.entries.joinToString(separator = "\n") { (key, value) ->
                    "$key: ${summarizeConfirmationArgument(key, value)}"
                },
            )
        }
        if (incoming.isEmpty()) return
        // Full access 或「总是允许」白名单命中的工具：预置进 approvedToolsThisTurn，
        // run 流结束时 finishRunIfOwned 会走既有的同轮自动放行通道直接 confirmed=true，
        // 不再弹出确认卡片。
        incoming.filter { toolApproval.isAutoApproved(it.toolName) }
            .forEach { runtime.approvedToolsThisTurn += it.toolName }
        runtime.phase = AgentTaskPhase.WAITING_FOR_CONFIRMATION
        viewModelScope.launch {
            runtime.lease?.updatePhase(AgentTaskPhase.WAITING_FOR_CONFIRMATION)
        }
        val knownIds = runtime.pendingToolConfirmations.mapTo(mutableSetOf()) {
            it.confirmationCallId
        }
        runtime.pendingToolConfirmations = runtime.pendingToolConfirmations + incoming.filter {
            knownIds.add(it.confirmationCallId)
        }
        runtime.isAgentRunning = true
        publishRuntime(runtime)
    }

    private fun applyError(sessionId: String, message: String, invocationId: String? = null) {
        val runtime = runtimeFor(sessionId)
        clearToolConfirmationState(runtime)
        runtime.messages = runtime.messages + Messages.fromError(error = message, invocationId = invocationId)
        runtime.isAgentRunning = false
        runtime.turnComplete = false
        runtime.failed = true
        publishRuntime(runtime)
    }

    /**
     * Partial 事件合并：必须满足"上一条也是 partial + 同 author"才合并。
     * 否则作为新消息起一段（保留 partial 流被打断时的鲁棒性）。
     */
    private fun mergePartialEvent(sessionId: String, event: ChatRunEvent) {
        val runtime = runtimeFor(sessionId)
        val author = event.author
        val role = authorToRole(author)

        val currentMessages = runtime.messages
        val existingIndex = currentMessages.indexOfLast { msg ->
            msg.partial &&
                msg.author == author &&
                msg.role == role &&
                msg.invocationId == event.invocationId
        }

        if (existingIndex < 0) {
            // 没有可合并的上一条 — 当成完整事件起一段。
            appendCompleteEvent(sessionId, event)
            return
        }

        val updated = mergeInto(sessionId, currentMessages[existingIndex], event)
        runtime.messages = runtime.messages.toMutableList().also { it[existingIndex] = updated }
        publishRuntime(runtime)
    }

    /**
     * 完整事件（非 partial） — 走 [EventMapper.fromEvent] 构造 [Message]，空 Event 跳过。
     *
     * 流式收尾时,已经有一条同 author + invocationId 的 partial message 在 `_uiState.messages` 尾部
     * (`mergePartialEvent` 累积 typewriter 文本用的就是这一条);此时若再 append 一条
     * non-partial 完整 message,UI 会看到重复文本。因此这里检测 partial 尾部并就地 replace,
     * 用 final event 的稳定 id 替换 partial message,既保留打字机视觉效果又避免重复气泡。
     *
     * **就地翻标志位**(不要整体替换):原实现用 `buildMessageFromParts(event)` 返回的新 Message 整体
     * 替换 partial message,新 Message 的 `TextPart.id` 由 `finalEvent.id` 派生,与 partial 阶段累积
     * 用的 `TextPart.id` 不同,导致 `partChannelProvider(part.id)` 在收尾瞬间查不到 channel,
     * `TextContent` 的 `partial` 分支条件不成立,会从 streaming 切到 static,触发整段 markdown
     * 重 parse / 重布局 — 气泡闪一下。这里改为 `old.copy(partial = false, turnComplete = ...)`,
     * 保留 `TextPart.id`,channel 订阅继续命中,TextContent 不切分支。
     */
    private fun appendCompleteEvent(sessionId: String, event: ChatRunEvent) {
        val runtime = runtimeFor(sessionId)
        val message = buildMessageFromParts(event) ?: return
        val current = runtime.messages
        val mergeIndex = current.indexOfLast { msg ->
            msg.partial &&
                msg.author == event.author &&
                msg.invocationId == event.invocationId
        }
        if (mergeIndex >= 0) {
            // 流式收尾:就地翻 partial 标志位,保留原 Message.id / TextPart.id / channel 订阅。
            runtime.messages = runtime.messages.toMutableList().also {
                val old = it[mergeIndex]
                it[mergeIndex] = old.copy(
                    partial = false,
                    turnComplete = message.turnComplete,
                )
            }
        } else {
            // 首个 partial 尚没有可合并的消息。EventMapper 直接构造了完整的
            // TextPart，因此必须在发布 UI state 前把它作为初始 chunk 入队；否则
            // 下一段 partial 才创建 channel 时，StreamingMarkdownState 只会收到
            // 下一段文本，导致首段文字在流式渲染中丢失。
            if (message.partial) {
                message.textParts.forEach { part ->
                    emitPartDelta(sessionId, part.id, part.text)
                }
            }
            runtime.messages = runtime.messages + message
        }
        publishRuntime(runtime)
    }

    /**
     * 把单个 Event 映射为 Message（走 [EventMapper]，与历史回放共用同一映射规则）。
     * 返回 null 表示 Event 内容为空（无 text part / 无 tool call / 无 error），跳过。
     */
    private fun buildMessageFromParts(event: ChatRunEvent): Message? =
        ChatRunEventMapper.fromEvent(event)

    /**
     * 把 partial Event 的所有 part 合并进已有 message；tool calls / responses 追加。
     *
     * reducer 的合并阶段仍按 part-by-part 累积（保留 streaming typewriter 语义），
     * [EventMapper] 只负责"完整 Event → Message"的入口（保证历史回放复用）。
     */
    private fun mergeInto(sessionId: String, message: Message, event: ChatRunEvent): Message {
        val parts = event.parts
        var working = message
        parts.forEachIndexed { index, part ->
            val text = part.text
            if (!text.isNullOrEmpty()) {
                val thought = part.thought
                working = appendTextPart(sessionId, working, event, index, text, thought)
            }
        }
        val newCalls = event.functionCalls.map { it.toView() }
        val newResponses = event.functionResponses.map { it.toView() }
        if (newCalls.isNotEmpty() || newResponses.isNotEmpty()) {
            working = working.copy(
                functionCalls = working.functionCalls + newCalls,
                functionResponses = working.functionResponses + newResponses,
            )
        }
        return working.copy(
            partial = true,
            turnComplete = event.turnComplete,
        )
    }

    /**
     * 与 adk-web `addTextToParts` 等价：
     * - 若末段的 `thought` 标志与本次相同 → 追加；
     * - 否则新建段。
     * - 同时把新增的文本作为 delta 推到对应 TextPart 的 channel，供渲染端做增量 markdown 解析。
     */
    private fun appendTextPart(
        sessionId: String,
        message: Message,
        event: ChatRunEvent,
        partIndex: Int,
        text: String,
        thought: Boolean,
    ): Message {
        if (text.isEmpty()) return message
        val parts = message.textParts.toMutableList()
        val last = parts.lastOrNull()
        if (last != null && last.thought == thought) {
            parts[parts.lastIndex] = last.copy(text = last.text + text)
            emitPartDelta(sessionId, last.id, text)
        } else {
            val newPart = TextPart(
                id = "${event.id}:$partIndex",
                text = text,
                thought = thought,
            )
            parts += newPart
            emitPartDelta(sessionId, newPart.id, text)
        }
        return message.copy(textParts = parts)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun authorToRole(author: String): MessageRole =
        if (author == "user") MessageRole.User else MessageRole.Assistant

    companion object {
        private const val TAG: String = "ChatViewModel"
        private const val MAX_PARALLEL_TASKS: Int = 3
        private const val MAX_DOCUMENT_REQUEST_BYTES: Long = 50L * 1024 * 1024
    }
}

data class PendingToolConfirmation(
    val confirmationCallId: String,
    val toolName: String,
    val description: String,
    val arguments: String,
)

private fun summarizeConfirmationArgument(key: String, value: Any?): String {
    val sensitiveKey = listOf(
        "phone",
        "contact",
        "message",
        "text",
        "content",
        "uri",
        "path",
        "file",
        "email",
        "token",
        "key",
    ).any { marker -> key.contains(marker, ignoreCase = true) }
    return if (sensitiveKey) "••••" else summarizeValue(value).take(120)
}

private fun List<LLMModelSetting>.isUsableChatSelection(selection: ModelSelection): Boolean {
    val service = firstOrNull { it.id == selection.serviceId } ?: return false
    if (!service.isEnabled || service.apiKey.isBlank()) return false
    val group = service.groups.firstOrNull { it.id == selection.groupId } ?: return false
    val model = group.models.firstOrNull { it.id == selection.modelId } ?: return false
    return !model.isStt && !model.isTts
}
