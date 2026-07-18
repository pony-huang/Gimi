package github.ponyhuang.asssistantai.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.agent.AgentChatRunner
import github.ponyhuang.asssistantai.data.ConversationRepository
import github.ponyhuang.asssistantai.data.ChatDisplayPreferences
import github.ponyhuang.asssistantai.data.EventMapper
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.data.LLMModelSelectionCodec
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.model.FunctionCallView
import github.ponyhuang.asssistantai.model.FunctionResponseView
import github.ponyhuang.asssistantai.model.Message
import github.ponyhuang.asssistantai.model.MessageRole
import github.ponyhuang.asssistantai.model.ImageAttachment
import github.ponyhuang.asssistantai.model.Messages
import github.ponyhuang.asssistantai.model.TextPart
import github.ponyhuang.asssistantai.speech.SpeechRecognitionRepository
import github.ponyhuang.asssistantai.speech.SpeechPlaybackController
import github.ponyhuang.asssistantai.speech.markdownToSpeechText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val runner: AgentChatRunner,
    private val repository: ConversationRepository,
    private val modelServices: ModelServiceRepository,
    private val chatDisplayPreferences: ChatDisplayPreferences,
    private val speechRecognitionRepository: SpeechRecognitionRepository,
    private val speechPlaybackController: SpeechPlaybackController,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val availableModelServices = modelServices.services
    val showToolActivity = chatDisplayPreferences.showToolActivity
    val isSpeechRecognitionAvailable: StateFlow<Boolean> = combine(
        modelServices.services,
        modelServices.defaultSpeechSelection,
    ) { _, _ ->
        speechRecognitionRepository.isAvailable()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _currentLLMModelSelection = MutableStateFlow<LLMModelSelection?>(null)
    /** 当前打开会话的有效模型选择；无可用模型时为 null。 */
    val currentLLMModelSelection: StateFlow<LLMModelSelection?> = _currentLLMModelSelection.asStateFlow()
    private val _pendingToolConfirmation = MutableStateFlow<PendingToolConfirmation?>(null)
    val pendingToolConfirmation: StateFlow<PendingToolConfirmation?> = _pendingToolConfirmation.asStateFlow()
    val speechPlaybackState = speechPlaybackController.state

    suspend fun transcribeVoice(pcm16: ByteArray): String =
        speechRecognitionRepository.transcribe(pcm16)

    fun toggleSpeechPlayback(messageId: String, markdown: String) {
        speechPlaybackController.toggle(messageId, markdownToSpeechText(markdown))
    }

    /** Sends the user's decision back to ADK, which then either runs or rejects the paused tool. */
    fun respondToToolConfirmation(confirmed: Boolean) {
        val request = _pendingToolConfirmation.value ?: return
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return
        _pendingToolConfirmation.value = null
        cancelCurrentRun()
        val runToken = agentRunOwnership.claim()
        _uiState.update { it.copy(isAgentRunning = true, turnComplete = false) }
        currentJob = viewModelScope.launch {
            try {
                runner.respondToToolConfirmation(
                    userId = USER_ID,
                    sessionId = sessionId,
                    confirmationCallId = request.confirmationCallId,
                    confirmed = confirmed,
                ).collect { event ->
                    Log.i("chat", "tool confirmation event: $event")
                    applyEvent(event)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                applyError(t.message ?: t::class.simpleName ?: "Unknown error")
            } finally {
                finishRunIfOwned(runToken)
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
            repository.conversationContentUpdates.collect { sessionId ->
                handleConversationContentUpdate(sessionId)
            }
        }
        viewModelScope.launch {
            speechPlaybackController.errors.collect { message ->
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var currentJob: Job? = null
    private var sessionLoadJob: Job? = null
    private var activeSessionLoadToken: Any? = null
    private var loadingSessionId: String? = null
    private var pendingContentRefreshSessionId: String? = null
    private val agentRunOwnership = AgentRunOwnership()

    private fun cancelCurrentRun() {
        agentRunOwnership.invalidate()
        currentJob?.cancel()
        currentJob = null
    }

    private fun finishRunIfOwned(runToken: Any) {
        if (!agentRunOwnership.isOwnedBy(runToken)) return
        currentJob = null
        _uiState.update { it.copy(isAgentRunning = false) }
        viewModelScope.launch { flushPendingConversationContentUpdate() }
    }

    override fun onCleared() {
        speechPlaybackController.clearSession()
        super.onCleared()
    }

    /**
     * 每个 [TextPart] 的文本增量流 — 渲染端用 `rememberStreamingMarkdownState` + `append()`
     * 做增量解析，避免每次 partial 都重解析整段 markdown。
     */
    private val partChannels = mutableMapOf<String, Channel<String>>()

    /**
     * 返回指定 [TextPart.id] 的文本增量订阅 channel。如果该 part 还没有任何增量发出，返回 `null`。
     */
    fun partChannelFor(partId: String): ReceiveChannel<String>? = partChannels[partId]

    private fun emitPartDelta(partId: String, delta: String) {
        if (delta.isEmpty()) return
        partChannels.getOrPut(partId) { Channel(Channel.UNLIMITED) }.trySend(delta)
    }

    /**
     * 关闭并清空所有 [partChannels]。
     *
     * 切换 / 重置会话时调用，避免 channel 跨会话累积（`Channel(UNLIMITED)` 持有挂起的消费者协程，
     * 仅当 `partChannels` 不再引用时才会被 GC）。
     */
    private fun clearPartChannels() {
        partChannels.values.forEach { it.close() }
        partChannels.clear()
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

        clearPartChannels()
        _uiState.update { current ->
            if (current.sessionId == sessionId && !current.isAgentRunning &&
                !current.isInitializing && loadingSessionId == null
            ) {
                current.copy(messages = messages, turnComplete = false)
            } else {
                current
            }
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
    fun send(text: String, attachmentUris: List<Uri> = emptyList()) {
        if (text.isBlank() && attachmentUris.isEmpty()) return

        cancelCurrentRun()
        val runToken = agentRunOwnership.claim()
        // 新一轮 turn 一经提交就进入生成态，让 composer 立即显示停止按钮；不再等待首个
        // assistant partial event 到达。后续的完成、错误和取消路径仍会负责将其恢复为 false。
        _uiState.update { it.copy(isAgentRunning = true, turnComplete = false) }

        val job = viewModelScope.launch {
            val images = try {
                readImageAttachments(attachmentUris)
            } catch (t: Throwable) {
                applyError("Cannot read selected image: ${t.message ?: "unknown error"}")
                return@launch
            }
            val userMessage = Messages.fromUser(text = text, imageAttachments = images)
            // 用户消息在附件读取完成后乐观追加；turn 状态已在提交时同步切换。
            _uiState.update {
                it.copy(messages = it.messages + userMessage, turnComplete = false)
            }
            // 兜底：保证 sessionId 非空再发请求。
            val sid = ensureSessionId()
            if (sid.isBlank()) {
                applyError("Cannot create a session; please retry.")
                return@launch
            }
            try {
                runner.send(
                    userId = USER_ID,
                    sessionId = sid,
                    text = text,
                    imageAttachments = images,
                ).collect { event ->
                    Log.i("chat", "event: $event")
                    applyEvent(event)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                applyError(t.message ?: t::class.simpleName ?: "Unknown error")
            } finally {
                finishRunIfOwned(runToken)
                repository.refreshConversation(sid)
            }
        }
        currentJob = job
    }

    private suspend fun readImageAttachments(uris: List<Uri>): List<ImageAttachment> =
        withContext(Dispatchers.IO) {
            uris.map { uri ->
                prepareImageAttachment(appContext.contentResolver, uri)
            }
        }

    /**
     * 兜底：保证 [sessionId] 非空。当前值为空时调 [ConversationRepository.createConversation] 建一个。
     *
     * 注：是 `suspend` 内部方法，调用方必须在协程里调用以确保 `_uiState.value.sessionId` 已更新后再继续。
     */
    private suspend fun ensureSessionId(): String {
        modelServices.awaitReady()
        _uiState.value.sessionId.takeIf { it.isNotBlank() }?.let { return it }
        val newId = repository.createConversation(defaultModelPayload())
        if (newId.isNotBlank()) {
            _uiState.update { it.copy(sessionId = newId) }
            activateConversationSettings(newId)
        }
        return _uiState.value.sessionId
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
    fun restoreOrCreateSession() {
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
                    activateConversationSettings(remembered)
                    _uiState.update {
                        it.copy(
                            sessionId = remembered,
                            messages = history,
                            turnComplete = false,
                            isInitializing = false,
                        )
                    }
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
            val newId = repository.createConversation(defaultModelPayload())
            if (newId.isNotBlank()) {
                activateConversationSettings(newId)
                _uiState.update { it.copy(sessionId = newId, isInitializing = false) }
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
    fun reset() {
        cancelCurrentRun()
        speechPlaybackController.clearSession()
        _uiState.update { it.copy(isAgentRunning = false, turnComplete = false) }
        clearPartChannels()
        viewModelScope.launch {
            modelServices.awaitReady()
            val newId = repository.createConversation(defaultModelPayload())
            if (newId.isNotBlank()) {
                switchSession(newId)
            } else {
                Log.w(TAG, "reset() failed to create a new conversation; UI state unchanged.")
            }
        }
    }

    /**
     * 切换到指定 session
     */
    fun switchSession(sessionId: String) {
        if (sessionId.isBlank()) return
        cancelCurrentRun()
        sessionLoadJob?.cancel()
        speechPlaybackController.clearSession()
        clearPartChannels()
        val loadToken = Any()
        activeSessionLoadToken = loadToken
        loadingSessionId = sessionId
        if (pendingContentRefreshSessionId != sessionId) pendingContentRefreshSessionId = null
        // isAgentRunning=false 解锁输入；turnComplete=false 重置上一轮的徽章；isInitializing=true
        // 让 MainScreen 中央 spinner 立刻接管，避免 history commit 之前旧 messages 残留闪烁。
        _uiState.update { it.copy(isAgentRunning = false, turnComplete = false, isInitializing = true) }
        sessionLoadJob = viewModelScope.launch {
            try {
                modelServices.awaitReady()
                val messages = repository.loadMessages(sessionId)
                if (activeSessionLoadToken !== loadToken) return@launch
                when {
                    messages == null -> {
                        Log.i(TAG, "switchSession($sessionId): session missing; creating a fresh one.")
                        repository.discardConversationMetadata(sessionId)
                        val newId = repository.createConversation(defaultModelPayload())
                        if (activeSessionLoadToken !== loadToken) return@launch
                        if (newId.isNotBlank()) {
                            activateConversationSettings(newId)
                            if (activeSessionLoadToken !== loadToken) return@launch
                            _uiState.update {
                                it.copy(
                                    sessionId = newId,
                                    messages = emptyList(),
                                    turnComplete = false,
                                    isInitializing = false,
                                )
                            }
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
                        activateConversationSettings(sessionId)
                        if (activeSessionLoadToken !== loadToken) return@launch
                        _uiState.update {
                            it.copy(
                                sessionId = sessionId,
                                messages = messages,
                                turnComplete = false,
                                isInitializing = false,
                            )
                        }
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
    fun refreshConversations() {
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
    fun deleteConversation(sessionId: String) {
        if (sessionId.isBlank()) return
        if (sessionId == _uiState.value.sessionId) {
            Log.w(TAG, "deleteConversation($sessionId) refused: this is the active session.")
            return
        }
        viewModelScope.launch {
            repository.deleteConversation(sessionId)
        }
    }

    /**
     * 通知 runner 重建：让 ModelServiceStore 里最新启用的服务在下次新建会话时生效。
     * 当前正在流式输出的会话不会被中途打断（recreate 只换 runner 引用，下一次 send 才用上）。
     */
    fun recreateRunner() {
        viewModelScope.launch { runner.recreate() }
    }

    fun selectModel(selection: LLMModelSelection) {
        // AgentChatRunner snapshots its agent at send entry. Changing the backing model while
        // a turn is active would make the current UI state disagree with that in-flight call.
        if (_uiState.value.isAgentRunning) {
            Log.i(TAG, "selectModel() ignored while an agent call is in progress.")
            Toast.makeText(appContext, "正在生成回复，完成后再切换模型", Toast.LENGTH_SHORT).show()
            return
        }
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            repository.setConversationModel(sessionId, LLMModelSelectionCodec.encode(selection))
            _currentLLMModelSelection.value = selection
            modelServices.setCurrentSelection(selection)
            // 当前流式调用已在 AgentChatRunner.send 入口快照 runner，重建不会中断它；
            // 因此无论是否正在输出，下一条消息都会使用新模型。
            runner.recreate()
        }
    }

    /**
     * 把指定会话持久化的配置装载为当前运行时配置，并重建后续消息使用的 agent。
     *
     * 已保存且仍可用的模型优先；新会话或模型已失效时使用当前默认模型。若没有任何
     * 可用模型，则清空运行时选择和旧 runner，保留空会话等待用户完成模型配置。
     */
    private suspend fun activateConversationSettings(sessionId: String) {
        modelServices.awaitReady()
        val storedModel = repository.activateConversation(sessionId, defaultModelPayload())
        val savedSelection = LLMModelSelectionCodec.decode(storedModel)
        val validSavedSelection = savedSelection?.takeIf {
            modelServices.resolveChatSelection(it) != null
        }
        val selection = validSavedSelection ?: modelServices.defaultSelection()
        if (savedSelection != selection) {
            repository.setConversationModel(
                sessionId = sessionId,
                model = selection?.let(LLMModelSelectionCodec::encode).orEmpty(),
            )
        }
        _currentLLMModelSelection.value = selection
        modelServices.setCurrentSelection(selection)
        if (selection == null) {
            runner.invalidate()
            Log.w(TAG, "activateConversationSettings($sessionId): no available model; runner invalidated.")
            return
        }
        runCatching { runner.recreate() }
            .onFailure { error ->
                runner.invalidate()
                Log.w(TAG, "activateConversationSettings($sessionId): runner recreation failed.", error)
            }
    }

    /** New conversations use the saved default assistant model, with a first-available fallback. */
    private fun defaultModelPayload(): String = modelServices.defaultSelection()
        ?.let(LLMModelSelectionCodec::encode)
        .orEmpty()

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
    fun stopStreaming() {
        if (currentJob?.isActive != true) return
        cancelCurrentRun()
        _uiState.update { state ->
            val newMessages = state.messages.map { msg ->
                if (msg.partial && msg.role == MessageRole.Assistant) {
                    msg.copy(partial = false, turnComplete = false)
                } else {
                    msg
                }
            }
            state.copy(
                messages = newMessages,
                isAgentRunning = false,
                turnComplete = false,
            )
        }
    }

    // ── Reducer ────────────────────────────────────────────────────────────

    /**
     * 顶层 reducer：partial 合并，否则当作完整事件构造新的 `Message`。
     */
    private fun applyEvent(event: Event) {
        // 错误优先：errorCode / errorMessage 非空 → 错误消息。
        val errMsg = event.errorMessage
        if (event.errorCode != null || !errMsg.isNullOrBlank()) {
            applyError(errMsg ?: event.errorCode ?: "Unknown error", event.invocationId)
            return
        }

        if (event.partial) {
            mergePartialEvent(event)
        } else {
            appendCompleteEvent(event)
        }
        applyAgentRunEvent(event)
        captureToolConfirmation(event)
    }

    private fun applyAgentRunEvent(event: Event) {
        _uiState.update { state ->
            val status = AgentRunStatus(
                isRunning = state.isAgentRunning,
                turnComplete = state.turnComplete,
            ).afterEvent(
                partial = event.partial,
                turnComplete = event.turnComplete,
            )
            state.copy(
                isAgentRunning = status.isRunning,
                turnComplete = status.turnComplete,
            )
        }
        if (event.turnComplete) refreshCurrentConversation()
    }

    /**
     * Extracts ADK's synthetic `adk_request_confirmation` function call.
     *
     * Development/testing automatically approves it before Compose can render the dialog, while
     * retaining the ADK confirmation request/response protocol for later policy changes.
     */
    private fun captureToolConfirmation(event: Event) {
        val confirmationCall = event.functionCalls().firstOrNull {
            it.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME
        } ?: return
        val confirmationId = confirmationCall.id ?: return
        val originalCall = confirmationCall.args[FunctionCall.ORIGINAL_FUNCTION_CALL_KEY] as? Map<*, *> ?: return
        val toolName = originalCall["name"] as? String ?: return
        val args = originalCall["args"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val argsSummary = args.entries.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", ",
        ) { (key, value) -> "$key=${summarizeValue(value)}" }
        _pendingToolConfirmation.value = PendingToolConfirmation(
            confirmationCallId = confirmationId,
            title = "Allow tool execution?",
            summary = "Allow $toolName$argsSummary?",
        )
        respondToToolConfirmation(confirmed = true)
    }

    private fun applyError(message: String, invocationId: String? = null) {
        _uiState.update {
            it.copy(
                messages = it.messages + Messages.fromError(error = message, invocationId = invocationId),
                isAgentRunning = false,
                turnComplete = false,
            )
        }
    }

    /**
     * Partial 事件合并：必须满足"上一条也是 partial + 同 author"才合并。
     * 否则作为新消息起一段（保留 partial 流被打断时的鲁棒性）。
     */
    private fun mergePartialEvent(event: Event) {
        val author = event.author
        val role = authorToRole(author)

        val currentMessages = _uiState.value.messages
        val existingIndex = currentMessages.indexOfLast { msg ->
            msg.partial &&
                msg.author == author &&
                msg.role == role &&
                msg.invocationId == event.invocationId
        }

        if (existingIndex < 0) {
            // 没有可合并的上一条 — 当成完整事件起一段。
            appendCompleteEvent(event)
            return
        }

        val updated = mergeInto(currentMessages[existingIndex], event)
        _uiState.update { current ->
            val newMessages = current.messages.toMutableList().also { it[existingIndex] = updated }
            current.copy(messages = newMessages)
        }
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
    private fun appendCompleteEvent(event: Event) {
        val message = buildMessageFromParts(event) ?: return
        val current = _uiState.value.messages
        val mergeIndex = current.indexOfLast { msg ->
            msg.partial &&
                msg.author == event.author &&
                msg.invocationId == event.invocationId
        }
        if (mergeIndex >= 0) {
            // 流式收尾:就地翻 partial 标志位,保留原 Message.id / TextPart.id / channel 订阅。
            _uiState.update { state ->
                val newMessages = state.messages.toMutableList().also {
                    val old = it[mergeIndex]
                    it[mergeIndex] = old.copy(
                        partial = false,
                        turnComplete = message.turnComplete,
                    )
                }
                state.copy(messages = newMessages)
            }
        } else {
            // 首个 partial 尚没有可合并的消息。EventMapper 直接构造了完整的
            // TextPart，因此必须在发布 UI state 前把它作为初始 chunk 入队；否则
            // 下一段 partial 才创建 channel 时，StreamingMarkdownState 只会收到
            // 下一段文本，导致首段文字在流式渲染中丢失。
            if (message.partial) {
                message.textParts.forEach { part ->
                    emitPartDelta(part.id, part.text)
                }
            }
            _uiState.update { it.copy(messages = it.messages + message) }
        }
    }

    /**
     * 把单个 Event 映射为 Message（走 [EventMapper]，与历史回放共用同一映射规则）。
     * 返回 null 表示 Event 内容为空（无 text part / 无 tool call / 无 error），跳过。
     */
    private fun buildMessageFromParts(event: Event): Message? = EventMapper.fromEvent(event)

    /**
     * 把 partial Event 的所有 part 合并进已有 message；tool calls / responses 追加。
     *
     * reducer 的合并阶段仍按 part-by-part 累积（保留 streaming typewriter 语义），
     * [EventMapper] 只负责"完整 Event → Message"的入口（保证历史回放复用）。
     */
    private fun mergeInto(message: Message, event: Event): Message {
        val parts = event.content?.parts.orEmpty()
        var working = message
        parts.forEachIndexed { index, part ->
            val text = part.text
            if (!text.isNullOrEmpty()) {
                val thought = part.thought == true
                working = appendTextPart(working, event, index, text, thought)
            }
        }
        val newCalls = event.functionCalls().map { it.toView() }
        val newResponses = event.functionResponses().map { it.toView() }
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
        message: Message,
        event: Event,
        partIndex: Int,
        text: String,
        thought: Boolean,
    ): Message {
        if (text.isEmpty()) return message
        val parts = message.textParts.toMutableList()
        val last = parts.lastOrNull()
        if (last != null && last.thought == thought) {
            parts[parts.lastIndex] = last.copy(text = last.text + text)
            emitPartDelta(last.id, text)
        } else {
            val newPart = TextPart(
                id = "${event.id}:$partIndex",
                text = text,
                thought = thought,
            )
            parts += newPart
            emitPartDelta(newPart.id, text)
        }
        return message.copy(textParts = parts)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun authorToRole(author: String): MessageRole =
        if (author == "user") MessageRole.User else MessageRole.Assistant

    private fun FunctionCall.toView(): FunctionCallView {
        if (name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) {
            return FunctionCallView(id = id.orEmpty(), name = name, argsSummary = "")
        }
        val argsText = if (args.isEmpty()) "" else args.entries.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ", ",
        ) { (k, v) -> "$k=${summarizeValue(v)}" }
        return FunctionCallView(id = id.orEmpty(), name = name, argsSummary = argsText)
    }

    private fun FunctionResponse.toView(): FunctionResponseView =
        FunctionResponseView(id = id.orEmpty(), name = name)

    private fun summarizeValue(v: Any?): String = when (v) {
        null -> "null"
        is String -> if (v.length > 16) "\"${v.take(15)}…\"" else "\"$v\""
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> "{…}"
        is List<*> -> "[…]"
        else -> v.toString()
    }

    companion object {
        private const val TAG: String = "ChatViewModel"

        /**
         * 进程级稳定的 userId — 派生自进程启动时刻，让 Room 里的所有会话都归属于同一 user。
         */
        private const val USER_ID: String = "user-default"
    }
}

data class PendingToolConfirmation(
    val confirmationCallId: String,
    val title: String,
    val summary: String,
)
