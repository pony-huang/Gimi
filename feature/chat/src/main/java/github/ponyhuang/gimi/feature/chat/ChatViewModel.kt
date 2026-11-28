package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.a2ui.A2uiDeviceControls
import github.ponyhuang.gimi.domain.a2ui.A2uiFlashlightState
import github.ponyhuang.gimi.domain.a2ui.A2uiMediaVolume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onStart

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.repository.VendorAttachmentResolution
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.usecase.PrepareChatSendUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.ChatHistorySnapshot
import github.ponyhuang.gimi.domain.conversation.usecase.ValidateChatAttachmentsUseCase
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AssistantConversationPartition
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.NoAvailableAssistantModelException
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.core.notifications.AppNotificationManager
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.a2a.model.SkippedRemoteAgent
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentSkipReporter
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelectionCodec
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.mcp.model.McpSkippedServer
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.mcp.repository.McpSkipReporter
import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import github.ponyhuang.gimi.domain.memory.repository.MemoryRuntimeStatus
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSettingsRepository
import github.ponyhuang.gimi.domain.voice.usecase.markdownToSpeechText
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
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
 * 管理聊天会话的界面状态，协调消息流、工具配置和失败轮次恢复。
 *
 * [AgentEventReducer] 合并流式事件、构造完整消息并捕获工具确认。
 * [ChatRunLifecycleCoordinator] 管理运行生命周期，按会话隔离事件；切换会话后，已启动的任务可继续运行。
 * [ChatToolConfigurationCoordinator] 管理工具配置，[ChatTurnRecoveryCoordinator] 恢复失败轮次。
 * [ChatSessionNavigationCoordinator] 协调会话导航与历史刷新，会话数据由 [ConversationRepository] 提供。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    agentRuntimeGate: AgentRuntimeGate,
    @AssistantConversationPartition
    private val repository: ConversationRepository,
    private val sessionResolver: ConversationSessionResolver,
    private val modelServices: ModelCatalogRepository,
    private val toolApproval: ToolApprovalRepository,
    private val toolAuthorization: ToolAuthorizationRepository,
    private val mcpRepository: McpRepository,
    private val mcpSkipReporter: McpSkipReporter,
    private val remoteAgentSkipReporter: RemoteAgentSkipReporter,
    private val speechRecognitionRepository: SpeechRecognitionRepository,
    private val speechPlaybackController: SpeechPlaybackRepository,
    private val speechSettings: SpeechSettingsRepository,
    private val attachments: ChatAttachmentRepository,
    private val fileUploadGateway: github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway,
    private val fileUploadRegistry: github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry,
    private val vendorAttachmentResolver: github.ponyhuang.gimi.domain.conversation.repository.VendorAttachmentResolver,
    private val refreshUploadedFilesUseCase: github.ponyhuang.gimi.domain.conversation.usecase.RefreshUploadedFilesUseCase,
    private val prepareChatSend: PrepareChatSendUseCase,
    private val validateChatAttachments: ValidateChatAttachmentsUseCase,
    officialFunctionCatalog: OfficialToolFunctionCatalog,
    private val memoryRuntimeStatus: MemoryRuntimeStatus,
    private val appNotificationManager: AppNotificationManager,
    private val appUpdateRepository: AppUpdateRepository,
    private val a2uiProtocol: github.ponyhuang.gimi.domain.a2ui.A2uiProtocol,
    private val chatAgent: github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository,
    internal val a2uiRendererFunctions: github.ponyhuang.gimi.domain.a2ui.A2uiRendererFunctions =
        github.ponyhuang.gimi.domain.a2ui.A2uiRendererFunctions(),
    private val a2uiDeviceControls: A2uiDeviceControls? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val flashlightRefresh = MutableStateFlow(0L)
    internal val a2uiVolume = (a2uiDeviceControls?.observeVolume() ?: flowOf<A2uiMediaVolume?>(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val a2uiFlashlight = flashlightRefresh.flatMapLatest {
        a2uiDeviceControls?.observeFlashlight()?.onStart { emit(A2uiFlashlightState.Loading) }
            ?: flowOf(A2uiFlashlightState.Disabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), A2uiFlashlightState.Loading)

    internal fun refreshA2uiFlashlight() {
        flashlightRefresh.update { it + 1 }
    }

    internal fun setA2uiVolume(sessionId: String, level: Int) {
        val controls = a2uiDeviceControls ?: return
        updateA2uiDevice(sessionId, volume = true) { controls.setVolume(level) }
    }

    internal fun setA2uiFlashlight(sessionId: String, enabled: Boolean) {
        val controls = a2uiDeviceControls ?: return
        updateA2uiDevice(sessionId, volume = false) { controls.setFlashlight(enabled) }
    }

    private fun updateA2uiDevice(sessionId: String, volume: Boolean, apply: suspend () -> Boolean) {
        val state = _uiState.value
        if (sessionId.isBlank() || state.sessionId != sessionId || state.isInitializing) return
        if (if (volume) state.a2uiChangingVolume else state.a2uiChangingFlashlight) return
        _uiState.update {
            if (volume) it.copy(a2uiChangingVolume = true) else it.copy(a2uiChangingFlashlight = true)
        }
        viewModelScope.launch {
            try {
                if (!apply()) _uiState.update {
                    if (it.sessionId == sessionId) it.copy(notice = ChatNotice.A2uiDeviceChangeFailed) else it
                }
            } finally {
                _uiState.update {
                    if (volume) it.copy(a2uiChangingVolume = false) else it.copy(a2uiChangingFlashlight = false)
                }
            }
        }
    }

    private val fileUploads = ChatFileUploadCoordinator(
        scope = viewModelScope,
        gateway = fileUploadGateway,
        registry = fileUploadRegistry,
        resolveConnection = { selection -> modelServices.resolveConnection(selection) },
        isFileUploadEnabled = { selection ->
            modelServices.currentInstances().firstOrNull { it.id == selection.instanceId }
                ?.findModel(selection)?.multimodal?.documentInput?.ingestMode ==
                github.ponyhuang.gimi.domain.modelcatalog.model.FileIngestMode.FILE_REFERENCE
        },
    )

    private val voiceDictation = VoiceDictationCoordinator(
        scope = viewModelScope,
        transcribe = speechRecognitionRepository::transcribe,
        onModeChange = { mode -> _uiState.update { it.copy(voiceTranscriptionMode = mode) } },
    )

    /** 「上传的文件」强制刷新进行中；防重复请求并驱动抽屉刷新按钮转圈。 */
    private val _uploadedFilesRefreshing = MutableStateFlow(false)
    val uploadedFilesRefreshing: StateFlow<Boolean> = _uploadedFilesRefreshing.asStateFlow()

    private val sessionRuntimes = linkedMapOf<String, ChatSessionRuntime>()

    private val _effects = MutableSharedFlow<ChatEffect>(extraBufferCapacity = 8)

    private val a2ui by lazy {
        github.ponyhuang.gimi.feature.chat.a2ui.ChatA2uiCoordinator(
            scope = viewModelScope,
            protocol = a2uiProtocol,
            canSubmit = { sessionId ->
                _uiState.value.sessionId == sessionId && !_uiState.value.isInitializing &&
                    !_uiState.value.isAgentRunning
            },
            submit = { sessionId, interaction -> submitA2uiInteraction(sessionId, interaction) },
            openUrl = { url -> _effects.tryEmit(ChatEffect.OpenA2uiUrl(url)) },
            onChanged = { sessionId, surfaces, owners ->
                _uiState.update { state -> if (state.sessionId == sessionId) {
                    state.copy(a2uiSurfaces = surfaces, a2uiOwners = owners)
                } else state }
            },
        )
    }

    internal fun setA2uiNeteaseLiked(sessionId: String, songId: String, liked: Boolean) {
        val state = _uiState.value
        if (sessionId.isBlank() || state.sessionId != sessionId || state.isInitializing ||
            songId in state.a2uiPendingNeteaseLikes
        ) return
        _uiState.update { it.copy(a2uiPendingNeteaseLikes = it.a2uiPendingNeteaseLikes + songId) }
        viewModelScope.launch {
            try {
                val changed = chatAgent.setA2uiNeteaseTrackLiked(sessionId, songId, liked)
                _uiState.update {
                    when {
                        changed -> it.copy(a2uiNeteaseLikes = it.a2uiNeteaseLikes + (songId to liked))
                        it.sessionId == sessionId -> it.copy(notice = ChatNotice.A2uiMusicLikeFailed)
                        else -> it
                    }
                }
            } finally {
                _uiState.update { it.copy(a2uiPendingNeteaseLikes = it.a2uiPendingNeteaseLikes - songId) }
            }
        }
    }

    private fun submitA2uiInteraction(sessionId: String, interaction: github.ponyhuang.gimi.domain.a2ui.A2uiInteraction) {
        if (_uiState.value.sessionId != sessionId) return
        val selection = _uiState.value.currentModelSelection ?: return
        startSend(sessionId, selection, "", emptyList(), a2uiInteraction = interaction)
    }

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
        emitNotice = ::emitNotice,
    )

    /** 命令式副作用通道（打开外部 URL），由 Route 消费；见 [ChatEffect]。 */
    val effects = _effects.asSharedFlow()

    /**
     * 用户意图统一入口 — 所有"发后即忘"的用户操作都经这里分发，见 [ChatAction]。
     */
    fun onAction(action: ChatAction) {
        if (action is ChatAction.SwitchSession || action == ChatAction.NewConversation) {
            voiceDictation.cancel()
        }
        when (action) {
            ChatAction.CancelVoiceTranscription -> voiceDictation.cancel()
            ChatAction.RetryFailedTurn -> turnRecoveryCoordinator.retry()
            is ChatAction.RetryA2ui -> retryA2ui(action.callId)
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
            is ChatAction.DeleteUploadedFile -> deleteUploadedFile(action.entry)
            is ChatAction.RecentConversations -> {
                if (action.action == RecentConversationsAction.ConfirmDeletion) {
                    deleteSelectedConversations()
                } else {
                    _uiState.update { it.reduceRecentConversations(action.action) }
                }
            }
            is ChatAction.SelectModel -> selectModel(action.selection)
            is ChatAction.SetThinkingEnabled -> toolConfigurationCoordinator.setThinkingEnabled(action.enabled)
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
            ChatAction.ConsumeNotice -> _uiState.update { it.copy(notice = null) }
        }
    }

    /** 一次性提示以 UiState 承载：呈现前不丢失，呈现后由 Route 经 ConsumeNotice 清除。 */
    private fun emitNotice(notice: ChatNotice) {
        _uiState.update { it.copy(notice = notice) }
    }

    fun transcribeVoice(
        pcm16: ByteArray,
        mode: VoiceInputMode,
        draft: MessageData,
        onResult: (Result<VoiceDictationResult>) -> Unit,
    ) {
        voiceDictation.start(pcm16, mode, draft, onResult)
    }

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
            fileUploads.states.collect { states ->
                _uiState.update { it.copy(draftUploadStates = states) }
            }
        }

        viewModelScope.launch {
            repository.conversations.collect { convs ->
                _uiState.update { state ->
                    state.copy(
                        conversations = convs,
                        recentConversations = state.recentConversations.copy(
                            selectedIds = state.recentConversations.selectedIds.intersect(convs.map { it.id }.toSet()),
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            modelServices.observeInstances().collect { services ->
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
            speechPlaybackController.errors.collect { failure ->
                emitNotice(ChatNotice.VoiceFailed(failure))
            }
        }
        viewModelScope.launch {
            mcpSkipReporter.skipped.collect { skipped ->
                notifySkippedMcpServers(skipped)
            }
        }
        viewModelScope.launch {
            remoteAgentSkipReporter.skipped.collect { skipped ->
                notifySkippedRemoteAgents(skipped)
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

    /** 已提示过的 (sessionId, remoteAgentId)，保证每个远程 agent 每个会话只提示一次。 */
    private val notifiedSkippedRemoteAgents = mutableSetOf<String>()

    private fun notifySkippedRemoteAgents(skipped: List<SkippedRemoteAgent>) {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank() || skipped.isEmpty()) return
        skipped
            .filter { notifiedSkippedRemoteAgents.add("$sessionId:${it.remoteAgentId}") }
            .forEach { emitNotice(ChatNotice.RemoteAgentSkipped(it.displayName)) }
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
        a2ui.sync(runtime.sessionId, runtime.messages, progressive = runtime.job?.isActive == true &&
            _uiState.value.sessionId == runtime.sessionId)
    }


    private fun showRuntime(sessionId: String, isInitializing: Boolean = false) {
        val runtime = runtimeFor(sessionId)
        _uiState.update { state ->
            state.copy(
                sessionId = sessionId,
                a2uiSurfaces = emptyMap(),
                a2uiOwners = emptyMap(),
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
        voiceDictation.cancel()
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

    /** 触发文档草稿的厂商上传（chip 阶段预热）；重试复用同一路径。 */
    fun uploadDocumentDraft(draft: DraftAttachment) {
        val selection = _uiState.value.currentModelSelection ?: return
        fileUploads.upload(draft, selection)
    }

    /** 打开厂商引用文档附件：先查本地绑定（离线直开），绑定缺失或悬空时走厂商下载兜底。 */
    suspend fun openVendorDocument(attachment: FileAttachment): VendorAttachmentResolution =
        vendorAttachmentResolver.resolveVendorPayload(
            attachment,
            _uiState.value.currentModelSelection?.let { selection -> modelServices.resolveConnection(selection) },
        )

    /**
     * 强制刷新「上传的文件」列表：仅由用户点击刷新按钮触发；listFiles 对账写回注册表，
     * 成功后 Room Flow 自动驱动列表，失败弹一次性提示且列表保持不动。进行中的刷新
     * 直接忽略，避免重复请求。
     */
    fun refreshUploadedFiles() {
        val selection = _uiState.value.currentModelSelection ?: return
        if (_uploadedFilesRefreshing.value) return
        val resolved = modelServices.resolveConnection(selection) ?: run {
            emitNotice(ChatNotice.UploadedFilesRefreshFailed)
            return
        }
        _uploadedFilesRefreshing.value = true
        viewModelScope.launch {
            val result = refreshUploadedFilesUseCase(resolved.templateId.orEmpty(), resolved)
            _uploadedFilesRefreshing.value = false
            if (result is github.ponyhuang.gimi.domain.conversation.usecase.UploadedFilesRefreshResult.Failed) {
                emitNotice(ChatNotice.UploadedFilesRefreshFailed)
            } else {
                emitNotice(ChatNotice.UploadedFilesRefreshed)
            }
        }
    }

    /** 清理上传状态：附件移除或发送完成后调用。 */
    fun clearDocumentUploads(references: Collection<String>) {
        fileUploads.clear(references)
    }

    /** 当前厂商已上传文件的注册记录；抽屉"上传的文件"列表数据源（按当前厂商过滤）。 */
    val uploadedFiles: StateFlow<List<github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry>> =
        _uiState
            .map { state ->
                val selection = state.currentModelSelection
                state.availableLLMModelSettings
                    .firstOrNull { it.id == selection?.instanceId }?.templateId
            }
            .distinctUntilChanged()
            .flatMapLatest { vendor ->
                if (vendor == null) {
                    flowOf(emptyList())
                } else {
                    fileUploadRegistry.entriesFor(vendor)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _attachFileRequests =
        kotlinx.coroutines.flow.MutableSharedFlow<github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry>(
            extraBufferCapacity = 4,
        )

    /** 抽屉点击"上传的文件"后的回填请求；聊天页据此把 file 引用草稿插入输入框。 */
    val attachFileRequests: kotlinx.coroutines.flow.SharedFlow<github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry> =
        _attachFileRequests

    /** 抽屉复用：请求把已上传文件回填为输入框 chip（同厂商 file_id 即就绪态）。 */
    fun requestAttachVendorFile(entry: github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry) {
        _attachFileRequests.tryEmit(entry)
    }

    /** 删除已上传文件：厂商侧删除成功后本地注册表由网关同步移除，失败仅提示保留条目。 */
    private fun deleteUploadedFile(entry: github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry) {
        val resolved = modelServices.resolveConnection(_uiState.value.currentModelSelection)
        // 列表按当前厂商过滤；厂商不一致或无法解析连接时直接视为失败，不发请求。
        if (resolved == null || resolved.templateId != entry.vendor) {
            emitNotice(ChatNotice.UploadedFileDeleteFailed)
            return
        }
        viewModelScope.launch {
            try {
                val result = fileUploadGateway.deleteFile(
                    entry.vendor, resolved.protocol, resolved.endpoint, resolved.apiKey, entry.fileId,
                )
                if (result is github.ponyhuang.gimi.domain.conversation.repository.VendorFileDeleteResult.Failure) {
                    Log.w(TAG, "Vendor file delete failed: ${result.reason}")
                    emitNotice(ChatNotice.UploadedFileDeleteFailed)
                } else {
                    emitNotice(ChatNotice.UploadedFileDeleted)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Vendor file delete crashed", failure)
                emitNotice(ChatNotice.UploadedFileDeleteFailed)
            }
        }
    }

    /** 发送只回报真实接管结果；准备过程属于 ViewModel，不依赖输入框协程的生命周期。 */
    fun send(
        text: String,
        draftAttachments: List<DraftAttachment> = emptyList(),
        onResult: (ChatSubmissionResult) -> Unit = {},
    ) {
        var ownsResolution = false
        val submission = ChatSubmission { result ->
            if (ownsResolution) resolvingSubmission = false
            // 发送被接受即本轮附件已消费：清理对应上传状态，不让 Failed/Ready 跨轮残留。
            if (result == ChatSubmissionResult.ACCEPTED && draftAttachments.isNotEmpty()) {
                fileUploads.clear(draftAttachments.map(DraftAttachment::reference))
            }
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
        a2uiInteraction: github.ponyhuang.gimi.domain.a2ui.A2uiInteraction? = null,
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
        if (runCatching { Log.isLoggable("GimiA2uiTiming", Log.DEBUG) }.getOrDefault(false)) {
            github.ponyhuang.gimi.domain.a2ui.A2uiTiming.submitted(sessionId) { entry ->
                Log.d("GimiA2uiTiming", entry)
            }
        }
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
                    a2uiCapabilities = a2ui.capabilities,
                    a2uiInteraction = a2uiInteraction,
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
                (turn.userMessage.a2uiInteraction?.let {
                    execution.sendInteraction(it, retry = retry != null)
                } ?: execution.send(
                    text = turn.userMessage.textParts.joinToString("") { it.text },
                    fileAttachments = turn.userMessage.fileAttachments,
                    retry = retry != null,
                )).collect { event ->
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
    private fun retryA2ui(callId: String) {
        val sessionId = _uiState.value.sessionId
        val runtime = sessionRuntimes[sessionId] ?: return
        if (runtime.isActive || sessionNavigationCoordinator.loadingSessionId != null) return
        val task = runtime.messages.flatMap { it.functionResponses }.firstOrNull { it.a2uiTask?.callId == callId }?.a2uiTask ?: return
        if (task.status !in setOf(github.ponyhuang.gimi.domain.a2ui.A2uiGenerationStatus.FAILED,
                github.ponyhuang.gimi.domain.a2ui.A2uiGenerationStatus.CANCELLED)) return
        if (sessionRuntimes.values.count { it.isActive } >= MAX_PARALLEL_TASKS) {
            emitNotice(ChatNotice.ParallelTaskLimitReached)
            return
        }
        val selection = runtime.modelSelection ?: _uiState.value.currentModelSelection ?: return
        runLifecycleCoordinator.launchRun(runtime) { token ->
            chatAgent.retryA2ui(sessionId, selection, callId, a2ui.capabilities).collect {
                eventReducer.applyEvent(sessionId, it, token)
            }
        }
    }

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
            // 身份保持式采用：后台完成/外部写入后回前台，内容等价的消息保留内存身份，
            // 避免回前台换 id 触发整轮 item key 变化与静态重 parse。
            runtime.messages = adoptLoadedHistory(runtime.messages, messages)
            runtime.isLoaded = true
            // 恢复前台只同步完整内容，阅读位置由列表保留，不能当作用户要求回到最新。
            publishRuntime(runtime)
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

    /** 按确认时的 ID 集合逐个删除，保留失败选择并允许重试。 */
    private fun deleteSelectedConversations() {
        val state = _uiState.value
        val recent = state.recentConversations
        if (recent.isDeleting || recent.pendingDeletionIds.isEmpty()) return
        val ids = recent.pendingDeletionIds.toList()
        _uiState.update { it.copy(recentConversations = it.recentConversations.copy(isDeleting = true)) }
        viewModelScope.launch {
            val deleted = mutableSetOf<String>()
            var blocked = false
            var failed = false
            try {
                for (id in ids) {
                    // 确认框打开后用户可能切换会话或启动任务，每次真正删除前重新校验。
                    if (id !in _uiState.value.deletableConversationIds() || sessionRuntimes[id]?.isActive == true) {
                        blocked = true
                        continue
                    }
                    try {
                        repository.deleteConversation(id)
                        deleted += id
                        a2ui.release(id)
                        sessionRuntimes.remove(id)?.closePartChannels()
                        attachments.deleteSession(id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "Failed to delete conversation $id", error)
                        failed = true
                    }
                }
                if (blocked) emitNotice(ChatNotice.ActiveConversationDeleteBlocked)
                if (failed) emitNotice(ChatNotice.ConversationDeleteFailed)
            } finally {
                _uiState.update { current ->
                    val remaining = (current.recentConversations.selectedIds - deleted)
                        .intersect(current.deletableConversationIds())
                    current.copy(recentConversations = current.recentConversations.copy(
                        isDeleting = false,
                        pendingDeletionIds = emptySet(),
                        selectedIds = remaining,
                        isSelecting = remaining.isNotEmpty(),
                    ))
                }
            }
        }
    }

    /**
     * 删除指定 session，并刷新会话列表。
     *
     * 守卫：若 [sessionId] 等于当前 `_uiState.value.sessionId`（即用户正在用的会话），拒绝并提示 —
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
            emitNotice(ChatNotice.CurrentConversationDeleteProtected)
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteConversation(sessionId)
                attachments.deleteSession(sessionId)
                a2ui.release(sessionId)
                sessionRuntimes.remove(sessionId)?.closePartChannels()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Failed to delete conversation $sessionId", error)
                emitNotice(ChatNotice.ConversationDeleteFailed)
            }
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
        modelServices.currentInstances().isUsableChatSelection(selection)

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
            completedAtMillis = completedAtMillis.toMap(),
        ),
    )

/** 会话可用的选择要求：实例启用、对话凭据可解析、连接与模型存在且可对话。 */
private fun List<ModelServiceInstance>.isUsableChatSelection(selection: ModelSelection): Boolean {
    val instance = firstOrNull { it.id == selection.instanceId } ?: return false
    if (!instance.isEnabled) return false
    val connection = instance.connection(selection.connectionId) ?: return false
    if (instance.effectiveApiKey(connection).isBlank()) return false
    val model = instance.findModel(selection) ?: return false
    return ModelCapability.LLM in model.capabilities
}

/** 最近一轮若处于失败态，则可作为当前进程内“重试”的可恢复轮。 */
private fun ChatSessionRuntime.failedRecoverableTurn(): ChatTurn? =
    lastTurn?.takeIf { it.status == ChatTurnStatus.FAILED }
