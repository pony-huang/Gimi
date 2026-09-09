package github.ponyhuang.gimi.feature.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatSessionRewindException
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
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
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
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
 * 取消语义：每个会话以 runToken 隔离事件，已接管的任务可在切换会话后继续。
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
    private val officialFunctionCatalog: OfficialToolFunctionCatalog,
    private val memoryRuntimeStatus: MemoryRuntimeStatus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val sessionRuntimes = linkedMapOf<String, ChatSessionRuntime>()
    private var sessionLoadJob: Job? = null
    private var activeSessionLoadToken: Any? = null
    private var loadingSessionId: String? = null

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
            ChatAction.RetryFailedTurn -> retryFailedTurn()
            ChatAction.EditFailedTurn -> editFailedTurn()
            ChatAction.CancelEditFailedTurn -> cancelEditFailedTurn()
            ChatAction.LeaveChat -> cancelFailedTurnEdit(restorePreviousDraft = false)
            is ChatAction.ResolveRepeatExecution -> resolveRepeatExecution(action.proceed)
            ChatAction.StopStreaming -> stopStreaming()
            is ChatAction.ToggleSpeechPlayback ->
                toggleSpeechPlayback(action.messageId, action.markdown)
            ChatAction.ToggleAutoSpeak ->
                speechSettings.setAutoSpeakEnabled(!speechSettings.autoSpeakEnabled.value)
            is ChatAction.RespondToToolConfirmation ->
                respondToToolConfirmation(action.confirmed, action.alwaysAllow)
            is ChatAction.RespondToInputRequest ->
                respondToInputRequest(action.callId, action.value)
            is ChatAction.SetFullAccess -> setFullAccess(action.enabled)
            ChatAction.RestoreOrCreateSession -> restoreOrCreateSession()
            ChatAction.NewConversation -> reset()
            is ChatAction.SwitchSession -> switchSession(action.sessionId)
            ChatAction.RefreshConversations -> refreshConversations()
            is ChatAction.DeleteConversation -> deleteConversation(action.sessionId)
            is ChatAction.SelectModel -> selectModel(action.selection)
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
        runtime.pendingToolConfirmations = runtime.pendingToolConfirmations.filterNot {
            it.confirmationCallId == request.confirmationCallId
        }
        respondToConfirmationRequest(sessionId, runtime, request, confirmed, alwaysAllow)
    }

    /**
     * 确认响应核心：用户确认卡片路径与自动放行通道共用。
     * `request` 由调用方先从对应队列（[ChatSessionRuntime.pendingToolConfirmations] /
     * [ChatSessionRuntime.autoApprovedConfirmations]）摘除，这里只负责落授权状态并发起恢复 run。
     */
    private fun respondToConfirmationRequest(
        sessionId: String,
        runtime: ChatSessionRuntime,
        request: PendingToolConfirmation,
        confirmed: Boolean,
        alwaysAllow: Boolean = false,
    ) {
        if (confirmed) {
            if (alwaysAllow) toolApproval.setAlwaysAllowed(request.toolName)
            runtime.approvedToolsThisTurn += request.toolName
        } else {
            runtime.approvedToolsThisTurn.clear()
            runtime.rejectedToolNames += request.toolName
        }
        val previousJob = cancelRun(runtime, releaseLease = false)
        launchRun(runtime) { runToken ->
            previousJob?.join()
            checkNotNull(runtime.execution) { "No active conversation execution" }.respondToToolConfirmation(
                confirmationCallId = request.confirmationCallId,
                confirmed = confirmed,
            ).collect { event ->
                eventReducer.applyEvent(sessionId, event, runToken)
            }
        }
    }

    /** 把用户对挂起输入请求的答复送回 ADK，恢复暂停的 invocation。 */
    private fun respondToInputRequest(callId: String, value: String) {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return
        val runtime = runtimeFor(sessionId)
        val request = runtime.pendingInputRequests.firstOrNull { it.callId == callId } ?: return
        runtime.pendingInputRequests = runtime.pendingInputRequests.filterNot {
            it.callId == callId
        }
        // ADK 恢复运行只把用户 FunctionResponse 落盘、不作为事件回流，实时消息流里
        // 永远收不到这条工具结果 —— 本地补一条响应消息，调用 chip 才能按 id 立即
        // 配对成 ✓（重启后由历史回放提供同样信息，见 EventMapper 的同规则处理）。
        runtime.messages += Messages.fromAssistant(
                    id = "input-response-${request.callId}",
                ).copy(
                    functionResponses = listOf(
                        FunctionResponseView(id = request.callId, name = request.toolName),
                    ),
                )
        val previousJob = cancelRun(runtime, releaseLease = false)
        launchRun(runtime) { runToken ->
            previousJob?.join()
            checkNotNull(runtime.execution) { "No active conversation execution" }.respondToInputRequest(
                callId = request.callId,
                toolName = request.toolName,
                value = value,
            ).collect { event ->
                eventReducer.applyEvent(sessionId, event, runToken)
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
            repository.conversationContentRevisions.collect {
                refreshVisibleHistoryIfStale()
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
            toolAuthorization = toolAuthorization,
            isAutoApproved = toolApproval::isAutoApproved,
        )
    }

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
                    failedTurn = runtime.failedRecoverableTurn(),
                    currentModelSelection = runtime.modelSelection,
                    toolConfiguration = runtime.toolConfiguration,
                    officialToolDescriptors = buildOfficialToolDescriptors(
                        runtime.modelSelection,
                        state.officialToolDescriptors,
                    ),
                    pendingToolConfirmations = runtime.pendingToolConfirmations,
                    pendingInputRequests = runtime.pendingInputRequests,
                    rejectedToolNames = runtime.rejectedToolNames.toSet(),
                    conversationTaskStatuses = statuses,
                )
            } else {
                state.copy(conversationTaskStatuses = statuses)
            }
        }
        scheduleMarkerExpansion()
    }

    private var navigationVersion = 0L

    private fun showRuntime(sessionId: String, isInitializing: Boolean = false) {
        val runtime = runtimeFor(sessionId)
        _uiState.update { state ->
            state.copy(
                sessionId = sessionId,
                messages = runtime.messages,
                isAgentRunning = runtime.isAgentRunning,
                failedTurn = runtime.failedRecoverableTurn(),
                currentModelSelection = runtime.modelSelection,
                toolConfiguration = runtime.toolConfiguration,
                officialToolDescriptors = buildOfficialToolDescriptors(
                    runtime.modelSelection,
                    state.officialToolDescriptors,
                ),
                pendingToolConfirmations = runtime.pendingToolConfirmations,
                pendingInputRequests = runtime.pendingInputRequests,
                rejectedToolNames = runtime.rejectedToolNames.toSet(),
                isInitializing = isInitializing,
            )
        }
        scheduleMarkerExpansion()
        publishRuntime(runtime)
    }

    private fun cancelRun(runtime: ChatSessionRuntime, releaseLease: Boolean = true): Job? {
        runtime.runToken = Any()
        val job = runtime.job
        job?.cancel()
        runtime.job = null
        if (releaseLease) {
            runtime.execution = null
            val lease = runtime.lease
            runtime.lease = null
            // 取消请求不等于执行已终止；旧 Job 真正结束前仍阻止同会话的新任务。
            if (job == null) lease?.release() else job.invokeOnCompletion { lease?.release() }
        }
        return job
    }

    private suspend fun ensureRunLease(runtime: ChatSessionRuntime, token: Any): AgentRunLease {
        runtime.lease?.let { return it }
        val lease = agentRuntimeGate.acquire(
            source = AgentTaskSource.CHAT,
            sessionId = runtime.sessionId,
        )
        try {
            currentCoroutineContext().ensureActive()
            if (runtime.runToken !== token) throw CancellationException("Superseded lease")
            runtime.lease = lease
            return lease
        } catch (cancelled: CancellationException) {
            lease.release()
            throw cancelled
        }
    }

    private fun releaseRunLease(runtime: ChatSessionRuntime) {
        runtime.lease?.release()
        runtime.lease = null
    }

    /** 三种执行入口共同持有一次协程所有权，准备失败、取消和恢复都从同一处收尾。 */
    private fun launchRun(
        runtime: ChatSessionRuntime,
        onFailure: (Throwable) -> Unit = { failure ->
            runtime.lastTurn?.let { saveFailedTurn(runtime.sessionId, it, failure) }
        },
        execute: suspend (Any) -> Unit,
    ): Job {
        val token = Any()
        runtime.runToken = token
        runtime.isAgentRunning = true
        runtime.phase = AgentTaskPhase.GENERATING
        publishRuntime(runtime)
        // 先登记 job 再执行，避免 Main.immediate 同步结束后把已完成 job 写回 runtime。
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var completedNormally = false
            try {
                ensureRunLease(runtime, token).updatePhase(AgentTaskPhase.GENERATING)
                execute(token)
                completedNormally = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AgentSessionBusyException) {
                if (runtime.runToken === token) emitNotice(ChatNotice.CurrentConversationBusy)
            } catch (failure: Exception) {
                if (runtime.runToken === token) {
                    eventReducer.applyError(
                        runtime.sessionId,
                        failure.message ?: failure::class.simpleName ?: "Unknown error",
                    )
                    if (_uiState.value.sessionId.isBlank()) {
                        emitNotice(ChatNotice.Message(failure.message ?: "Unknown error"))
                    }
                    onFailure(failure)
                }
            } finally {
                finishRunIfOwned(runtime.sessionId, token, completedNormally)
                repository.refreshConversation(runtime.sessionId)
            }
        }
        runtime.job = job
        job.start()
        return job
    }

    private suspend fun finishRunIfOwned(sessionId: String, runToken: Any, completedNormally: Boolean) {
        val runtime = runtimeFor(sessionId)
        if (runtime.runToken !== runToken) return
        runtime.job = null
        val pending = runtime.pendingToolConfirmations.firstOrNull()
        val autoApproved = runtime.autoApprovedConfirmations.firstOrNull()
        val pendingInput = runtime.pendingInputRequests.firstOrNull()
        runtime.isAgentRunning = pending != null || autoApproved != null || pendingInput != null
        when {
            // 用户本轮已手动批准过该工具：后续同工具确认沿用同轮放行通道直接确认。
            pending != null && pending.toolName in runtime.approvedToolsThisTurn -> {
                runtime.pendingToolConfirmations = runtime.pendingToolConfirmations.filterNot {
                    it.confirmationCallId == pending.confirmationCallId
                }
                respondToConfirmationRequest(sessionId, runtime, pending, confirmed = true)
            }
            // 有用户卡片在等决策时先不排空自动放行队列，保持"用户答复优先"的旧顺序。
            pending != null -> {
                runtime.lease?.updatePhase(AgentTaskPhase.WAITING_FOR_CONFIRMATION)
                publishRuntime(runtime)
            }
            // 自动放行通道：不弹卡片，run 流暂停后静默回复 ADK confirmed=true。
            autoApproved != null -> {
                runtime.autoApprovedConfirmations = runtime.autoApprovedConfirmations.filterNot {
                    it.confirmationCallId == autoApproved.confirmationCallId
                }
                respondToConfirmationRequest(sessionId, runtime, autoApproved, confirmed = true)
            }
            // 挂起的用户输入请求：保持等待态，等用户在输入卡片上答复后恢复运行。
            pendingInput != null -> {
                runtime.phase = AgentTaskPhase.WAITING_FOR_INPUT
                runtime.lease?.updatePhase(AgentTaskPhase.WAITING_FOR_INPUT)
                publishRuntime(runtime)
            }
            else -> {
                runtime.execution = null
                if (runtime.failed) {
                    runtime.lastTurn?.takeIf { it.status != ChatTurnStatus.FAILED }
                        ?.let { saveFailedTurn(sessionId, it) }
                } else {
                    runtime.lastTurn = null
                }
                runtime.approvedToolsThisTurn.clear()
                releaseRunLease(runtime)
                if (_uiState.value.sessionId != sessionId) {
                    runtime.attention = when {
                        runtime.failed -> SessionResultAttention.FAILED
                        completedNormally -> SessionResultAttention.COMPLETED
                        else -> SessionResultAttention.NONE
                    }
                } else if (completedNormally) {
                    autoSpeakCompletedReply(sessionId, runtime)
                }
                publishRuntime(runtime)
            }
        }
        viewModelScope.launch { refreshVisibleHistoryIfStale() }
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
        runtime.autoApprovedConfirmations = emptyList()
        publishRuntime(runtime)
    }

    override fun onCleared() {
        sessionRuntimes.values.forEach { runtime ->
            cancelRun(runtime)
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

    private fun contentRevision(sessionId: String): Long =
        repository.conversationContentRevisions.value[sessionId] ?: 0L

    /** 历史只在该会话无执行者时更新；版本留在仓库中，后台会话不依赖瞬时通知。 */
    private suspend fun refreshVisibleHistoryIfStale() {
        val sessionId = _uiState.value.sessionId
        val runtime = sessionRuntimes[sessionId] ?: return
        fun canReload(): Boolean = _uiState.value.sessionId == sessionId &&
            !_uiState.value.isInitializing && loadingSessionId == null && !runtime.isActive

        while (canReload()) {
            val revision = contentRevision(sessionId)
            if (!runtime.isLoaded || runtime.loadedContentRevision >= revision) return
            val runToken = runtime.runToken
            val messages = repository.loadMessages(sessionId) ?: return
            // 加载期间即使一次新任务已经完成，也不能用旧读取覆盖它的内存结果。
            if (!canReload() || runtime.runToken !== runToken) return
            if (runtime.loadedContentRevision > revision) return
            runtime.closePartChannels()
            runtime.messages = messages
            runtime.loadedContentRevision = revision
            publishRuntime(runtime)
        }
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
            state.isInitializing || loadingSessionId != null ||
            state.failedTurnRecovery is FailedTurnRecoveryState.AwaitingRepeatConfirmation
        ) {
            submission.complete(ChatSubmissionResult.REJECTED)
            return
        }
        val editing = state.failedTurnRecovery as? FailedTurnRecoveryState.Editing
        val failedTurn = state.failedTurn
        if (editing != null && failedTurn?.hasToolCalls == true) {
            _uiState.update {
                it.copy(
                    failedTurnRecovery = FailedTurnRecoveryState.AwaitingRepeatConfirmation(
                        request = FailedTurnResendRequest.SubmitEdit(
                            MessageData(text = text, attachments = draftAttachments),
                        ),
                        previousDraft = editing.previousDraft,
                    ),
                )
            }
            submission.complete(ChatSubmissionResult.REJECTED)
            return
        }
        val retry = failedTurn.takeIf { editing != null }
        val selection = state.currentModelSelection?.takeIf(::isUsableChatSelection)
        if (state.sessionId.isNotBlank() && selection != null) {
            startSend(state.sessionId, selection, text, draftAttachments, retry, submission = submission)
            return
        }
        if (resolvingSubmission) {
            submission.complete(ChatSubmissionResult.REJECTED)
            return
        }
        resolvingSubmission = true
        ownsResolution = true
        val navigationAtSend = navigationVersion
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
                if (navigationVersion != navigationAtSend || loadingSessionId != null) return@launch
                handedOff = true
                startSend(
                    snapshot.sessionId, snapshot.modelSelection, text, draftAttachments, retry,
                    submission = submission,
                    showAfterAcceptance = true,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emitNotice(ChatNotice.ConfigureChatModel)
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
        reuseOriginal: Boolean = false,
        submission: ChatSubmission = ChatSubmission {},
        showAfterAcceptance: Boolean = false,
    ) {
        val runtime = runtimeFor(sessionId)
        if (runtime.isActive || loadingSessionId != null ||
            sessionRuntimes.values.count { it.isActive } >= MAX_PARALLEL_TASKS
        ) {
            if (!runtime.isActive && loadingSessionId == null) {
                emitNotice(ChatNotice.ParallelTaskLimitReached)
            }
            submission.complete(ChatSubmissionResult.REJECTED)
            return
        }
        validateAttachments(selection, draftAttachments)?.let {
            emitNotice(it)
            submission.complete(ChatSubmissionResult.REJECTED)
            return
        }
        clearToolConfirmationState(runtime)
        runtime.modelSelection = selection
        runtime.failed = false
        runtime.attention = SessionResultAttention.NONE
        val navigationAtSend = navigationVersion
        var preparedTurn: ChatTurn? = null
        val job = launchRun(runtime, onFailure = { failure ->
            preparedTurn?.let { saveFailedTurn(sessionId, it, failure) }
        }) { runToken ->
            try {
                // 准备必须在 lease 内完成；配置读取失败不能悄悄沿用旧配置启动。
                val configuration = sessionResolver.resolveToolConfiguration(sessionId, selection)
                val revision = contentRevision(sessionId)
                val history = if (runtime.isLoaded &&
                    runtime.loadedContentRevision >= revision
                ) runtime.messages else repository.loadMessages(sessionId).orEmpty()
                val turn = prepareChatTurn(
                    sessionId = sessionId,
                    text = text,
                    drafts = draftAttachments,
                    history = history,
                    retry = retry,
                    reuseOriginal = reuseOriginal,
                )
                val execution = runner.createExecution(sessionId, selection, configuration)
                currentCoroutineContext().ensureActive()
                if (runtime.runToken !== runToken) throw CancellationException("Superseded preparation")
                // 未接管前的导航取消发送；已接管的后台任务则继续执行。
                if (navigationVersion != navigationAtSend) throw CancellationException("Navigation changed")
                runtime.execution = execution
                runtime.toolConfiguration = configuration
                preparedTurn = turn
                runtime.lastTurn = turn
                runtime.messages = turn.messages
                runtime.loadedContentRevision = revision
                runtime.isLoaded = true
                runtime.failed = false
                submission.complete(ChatSubmissionResult.ACCEPTED)
                if (showAfterAcceptance) showRuntime(sessionId) else publishRuntime(runtime)
                if (retry != null && !reuseOriginal && _uiState.value.sessionId == sessionId) {
                    finishFailedTurnEdit()
                }
                execution.send(
                    text = turn.userMessage.textParts.joinToString("") { it.text },
                    fileAttachments = turn.userMessage.fileAttachments,
                    rewindBeforeInvocationId = turn.rewindBeforeInvocationId,
                ).collect { event -> eventReducer.applyEvent(sessionId, event, runToken) }
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
     * 把发送轮落盘为可恢复的 FAILED 轮次（保留部分输出与附件），供错误区的“编辑/重试”恢复。
     * 流式失败与用户主动停止都走这里；重试/编辑时通过 ADK invocation 边界回退到本轮之前。
     */
    private fun saveFailedTurn(sessionId: String, turn: ChatTurn, failure: Throwable? = null) {
        val runtime = runtimeFor(sessionId)
        val executedTool = runtime.messages.hasToolCallsAfter(turn.userMessage.id)
        val failed = turn.copy(
            status = ChatTurnStatus.FAILED,
            hasToolCalls = executedTool,
            messages = runtime.messages,
            rewindBeforeInvocationId = if (failure is ChatSessionRewindException) {
                turn.rewindBeforeInvocationId
            } else {
                // ADK 0.8.0 自建 invocation id；回退边界必须是该 id（由事件回流携带），
                // 而不是领域层随机 attemptId —— 否则重试时 rewindAsync 找不到对应事件。
                runtime.messages.lastOrNull { it.invocationId != null }?.invocationId
            },
        )
        runtime.lastTurn = failed
        publishRuntime(runtime)
    }

    /** 重新发送最近失败轮次；若工具已执行则先请求用户确认。 */
    private fun retryFailedTurn() {
        val state = _uiState.value
        val failedTurn = state.failedTurn ?: return
        if (state.isAgentRunning || state.failedTurnRecovery !is FailedTurnRecoveryState.Idle) return
        if (failedTurn.hasToolCalls) {
            _uiState.update {
                it.copy(
                    failedTurnRecovery = FailedTurnRecoveryState.AwaitingRepeatConfirmation(
                        FailedTurnResendRequest.RetryOriginal,
                    ),
                )
            }
            return
        }
        executeFailedTurnResend(failedTurn, FailedTurnResendRequest.RetryOriginal)
    }

    /** 编辑失败消息：把原始文字与附件回填输入框，进入编辑态。 */
    private fun editFailedTurn() {
        val state = _uiState.value
        val failedTurn = state.failedTurn ?: return
        if (state.isAgentRunning || state.failedTurnRecovery !is FailedTurnRecoveryState.Idle) return
        val text = failedTurn.userMessage.textParts.joinToString("") { it.text }
        val editing = FailedTurnRecoveryState.Editing(
            sessionId = state.sessionId,
            previousDraft = state.composerSeed,
        )
        _uiState.update { it.copy(failedTurnRecovery = editing) }
        viewModelScope.launch {
            val draftAttachments = runCatching {
                attachments.createDrafts(failedTurn.userMessage.fileAttachments)
            }.getOrElse { failure ->
                emitNotice(ChatNotice.EditDraftsRestoreFailed)
                Log.w(TAG, "Failed to create edit drafts", failure)
                emptyList()
            }
            if (_uiState.value.failedTurnRecovery != editing) {
                if (draftAttachments.isNotEmpty()) attachments.deleteDrafts(draftAttachments)
                return@launch
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
        cancelFailedTurnEdit(restorePreviousDraft = true)
    }

    /** 统一结束编辑状态；会话切换/离开页面时不把旧草稿带到目标会话。 */
    private fun cancelFailedTurnEdit(restorePreviousDraft: Boolean) {
        val state = _uiState.value
        val recovery = state.failedTurnRecovery
        if (recovery is FailedTurnRecoveryState.Idle) return
        val previousDraft = when (recovery) {
            is FailedTurnRecoveryState.Editing -> recovery.previousDraft
            is FailedTurnRecoveryState.AwaitingRepeatConfirmation -> recovery.previousDraft
            FailedTurnRecoveryState.Idle -> null
        }
        val pendingEditDrafts = (
            recovery as? FailedTurnRecoveryState.AwaitingRepeatConfirmation
        )?.request.let { request ->
            (request as? FailedTurnResendRequest.SubmitEdit)?.message?.attachments.orEmpty()
        }
        val editDrafts = (state.composerSeed.attachments + pendingEditDrafts).distinctBy { it.reference }
        _uiState.update {
            it.copy(
                failedTurnRecovery = FailedTurnRecoveryState.Idle,
                composerSeed = if (restorePreviousDraft) previousDraft ?: MessageData() else MessageData(),
            )
        }
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
        val pending = state.failedTurnRecovery as? FailedTurnRecoveryState.AwaitingRepeatConfirmation
            ?: return
        if (!proceed) {
            _uiState.update {
                it.copy(
                    failedTurnRecovery = when (pending.request) {
                        FailedTurnResendRequest.RetryOriginal -> FailedTurnRecoveryState.Idle
                        is FailedTurnResendRequest.SubmitEdit -> FailedTurnRecoveryState.Editing(
                            sessionId = state.sessionId,
                            previousDraft = pending.previousDraft ?: MessageData(),
                        )
                    },
                )
            }
            return
        }
        val failedTurn = state.failedTurn ?: return
        _uiState.update {
            it.copy(
                failedTurnRecovery = when (pending.request) {
                    FailedTurnResendRequest.RetryOriginal -> FailedTurnRecoveryState.Idle
                    is FailedTurnResendRequest.SubmitEdit -> FailedTurnRecoveryState.Editing(
                        sessionId = state.sessionId,
                        previousDraft = pending.previousDraft ?: MessageData(),
                    )
                },
            )
        }
        executeFailedTurnResend(failedTurn, pending.request)
    }

    /** 原样重试和编辑提交的唯一执行入口。 */
    private fun executeFailedTurnResend(
        failedTurn: ChatTurn,
        request: FailedTurnResendRequest,
    ) {
        val state = _uiState.value
        val selection = state.currentModelSelection?.takeIf(::isUsableChatSelection) ?: run {
            emitNotice(ChatNotice.ConfigureChatModel)
            return
        }
        if (state.sessionId.isBlank()) return
        when (request) {
            FailedTurnResendRequest.RetryOriginal -> startSend(
                sessionId = state.sessionId,
                selection = selection,
                text = failedTurn.userMessage.textParts.joinToString("") { it.text },
                draftAttachments = emptyList(),
                retry = failedTurn,
                reuseOriginal = true,
            )
            is FailedTurnResendRequest.SubmitEdit -> startSend(
                sessionId = state.sessionId,
                selection = selection,
                text = request.message.text,
                draftAttachments = request.message.attachments,
                retry = failedTurn,
                reuseOriginal = false,
            )
        }
    }

    private fun finishFailedTurnEdit() {
        _uiState.update {
            it.copy(
                failedTurnRecovery = FailedTurnRecoveryState.Idle,
                composerSeed = MessageData(),
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
        if (_uiState.value.sessionId.isNotBlank() || _uiState.value.isInitializing) return
        val loadToken = Any()
        activeSessionLoadToken = loadToken
        _uiState.update { it.copy(isInitializing = true) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val snapshot = sessionResolver.resolveCurrentOrCreate()
                val revision = contentRevision(snapshot.sessionId)
                val history = repository.loadMessages(snapshot.sessionId).orEmpty()
                currentCoroutineContext().ensureActive()
                if (activeSessionLoadToken !== loadToken) return@launch
                val runtime = runtimeFor(snapshot.sessionId)
                runtime.messages = history
                runtime.modelSelection = snapshot.modelSelection
                runtime.toolConfiguration = snapshot.toolConfiguration
                runtime.isLoaded = true
                runtime.loadedContentRevision = revision
                showRuntime(snapshot.sessionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Unable to restore current conversation", failure)
            } finally {
                if (activeSessionLoadToken === loadToken) {
                    activeSessionLoadToken = null
                    sessionLoadJob = null
                    _uiState.update { it.copy(isInitializing = false) }
                    refreshVisibleHistoryIfStale()
                }
            }
        }
        sessionLoadJob = job
        job.start()
    }

    /**
     * 开始一个全新的会话 — 调 [ConversationRepository.createConversation] 创建并切到新会话。
     *
     * 即使 [ConversationRepository.createConversation] 失败（例如 Room 暂时不可用），也先清掉
     * 上一会话遗留的 [partChannels]，避免 channel 跨"空 session"残留。
     */
    private fun reset() {
        navigationVersion++
        val navigationAtReset = navigationVersion
        cancelFailedTurnEdit(restorePreviousDraft = false)
        viewModelScope.launch {
            val newId = createConversationWithDefaults()
            if (navigationAtReset != navigationVersion) return@launch
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
        navigationVersion++
        cancelFailedTurnEdit(restorePreviousDraft = false)
        sessionRuntimes[_uiState.value.sessionId]?.closePartChannels()
        sessionLoadJob?.cancel()
        speechPlaybackController.clearSession()
        val loadToken = Any()
        activeSessionLoadToken = loadToken
        loadingSessionId = sessionId
        // 让 MainScreen 中央 spinner 立刻接管，避免 history commit 之前旧 messages 残留闪烁。
        val targetRuntime = runtimeFor(sessionId)
        showRuntime(sessionId, isInitializing = !targetRuntime.isLoaded)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val snapshot = sessionResolver.activate(sessionId)
                currentCoroutineContext().ensureActive()
                if (activeSessionLoadToken !== loadToken) return@launch
                targetRuntime.modelSelection = snapshot?.modelSelection
                targetRuntime.toolConfiguration = snapshot?.toolConfiguration
                if (targetRuntime.isLoaded && (targetRuntime.isActive ||
                    targetRuntime.loadedContentRevision >= contentRevision(sessionId))
                ) {
                    targetRuntime.attention = SessionResultAttention.NONE
                    targetRuntime.reseedPartialChannels()
                    showRuntime(sessionId)
                    return@launch
                }
                val revision = contentRevision(sessionId)
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
                        targetRuntime.messages = messages
                        targetRuntime.isLoaded = true
                        targetRuntime.loadedContentRevision = revision
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
                    refreshVisibleHistoryIfStale()
                }
            }
        }
        sessionLoadJob = job
        job.start()
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
            initializeOfficialFunctionsForSelection(sessionId, runtime, selection)
            publishRuntime(runtime)
        }
    }

    /**
     * 把指定会话持久化的配置装载为当前运行时配置，并重建后续消息使用的 agent。
     *
     * 已保存且仍可用的模型优先；新会话或模型已失效时使用当前默认模型。若没有任何
     * 可用模型，则清空运行时选择和旧 runner，保留空会话等待用户完成模型配置。
     */
    private suspend fun createConversationWithDefaults(): String {
        val snapshot = sessionResolver.createAndActivate()
        runtimeFor(snapshot.sessionId).apply {
            modelSelection = snapshot.modelSelection
            toolConfiguration = snapshot.toolConfiguration
        }
        return snapshot.sessionId
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
        if (runtime.job?.isActive != true &&
            runtime.pendingToolConfirmations.isEmpty() &&
            runtime.pendingInputRequests.isEmpty() &&
            runtime.autoApprovedConfirmations.isEmpty()
        ) {
            return
        }
        if (runtime.pendingInputRequests.isNotEmpty()) {
            // 输入请求没有"拒绝"语义（ADK 协议只认 FunctionResponse 答复），
            // 停止按钮不消费挂起请求，用户仍可在卡片上答复。
            return
        }
        if (runtime.pendingToolConfirmations.isNotEmpty()) {
            runtime.approvedToolsThisTurn.clear()
            respondToToolConfirmation(sessionId, confirmed = false)
            return
        }
        if (runtime.autoApprovedConfirmations.isNotEmpty()) {
            // 中断仍在流式中的自动放行轮，沿用"停止 = 拒绝挂起确认"的旧语义。
            val request = runtime.autoApprovedConfirmations.first()
            runtime.autoApprovedConfirmations = runtime.autoApprovedConfirmations.drop(1)
            respondToConfirmationRequest(sessionId, runtime, request, confirmed = false)
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
        runtime.attention = SessionResultAttention.NONE
        // 用户主动停止的轮次同样保留“编辑/重试”：把已生成的部分回答一并落盘为可恢复轮，
        // 重试/编辑时交给 ADK Runner 回退到本轮 invocation 之前（与失败轮语义一致）。
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

/** 最近一轮若处于失败态，则可作为当前进程内“编辑/重试”的可恢复轮。 */
private fun ChatSessionRuntime.failedRecoverableTurn(): ChatTurn? =
    lastTurn?.takeIf { it.status == ChatTurnStatus.FAILED }

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
