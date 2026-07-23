package github.ponyhuang.asssistantai.data.assistant

import github.ponyhuang.asssistantai.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionState
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantTurn
import github.ponyhuang.asssistantai.domain.assistant.model.PendingAssistantConfirmation
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantConfirmationHandler
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.asssistantai.domain.conversation.model.ChatRunEvent
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ConversationRepository
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelectionCodec
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechRecognitionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [AssistantSessionCoordinator] 的进程级实现。
 *
 * 同一语音会话的任务经 [submitMutex] 串行；任务在协调器自有作用域执行，
 * [stop] 只取消任务协程，浮层关闭（[hideOverlay]）不影响任务。
 */
@Singleton
class DefaultAssistantSessionCoordinator @Inject constructor(
    private val conversations: ConversationRepository,
    private val chatAgent: ChatAgentRepository,
    private val modelCatalog: ModelCatalogRepository,
    private val speechRecognition: SpeechRecognitionRepository,
    private val runtimeGate: AgentRuntimeGate,
    private val sessionStore: VoiceSessionIdStore,
) : AssistantSessionCoordinator {

    /** 测试可替换的任务调度器；必须在首次提交前设置。 */
    internal var taskDispatcher: CoroutineDispatcher = Dispatchers.Default

    private val scope by lazy { CoroutineScope(SupervisorJob() + taskDispatcher) }
    private val submitMutex = Mutex()
    private var runningJob: Job? = null
    private var confirmationResponse: CompletableDeferred<Boolean>? = null

    private val _state = MutableStateFlow(AssistantSessionState())
    override val state: StateFlow<AssistantSessionState> = _state.asStateFlow()
    override val voiceSessionId: StateFlow<String?> = sessionStore.voiceSessionId

    override suspend fun configurationIssue(): AssistantConfigIssue? {
        modelCatalog.awaitReady()
        if (defaultSelection() == null) return AssistantConfigIssue.MISSING_AGENT_MODEL
        if (!speechRecognition.availability.first()) return AssistantConfigIssue.MISSING_STT
        return null
    }

    override fun noteInvocation(source: AssistantInvocationSource) {
        _state.update { it.copy(source = source, overlayVisible = true) }
    }

    override suspend fun submit(
        text: String,
        source: AssistantInvocationSource,
        confirmationHandler: AssistantConfirmationHandler?,
    ) {
        submitMutex.withLock {
            val job = scope.launch { runTask(text, source, confirmationHandler) }
            runningJob = job
            try {
                job.join()
            } finally {
                if (runningJob === job) runningJob = null
            }
        }
    }

    override fun stop() {
        confirmationResponse?.complete(false)
        runningJob?.cancel()
    }

    override fun respondToConfirmation(confirmed: Boolean) {
        confirmationResponse?.complete(confirmed)
    }

    override fun hideOverlay() {
        _state.update { it.copy(overlayVisible = false) }
    }

    private suspend fun runTask(
        text: String,
        source: AssistantInvocationSource,
        confirmationHandler: AssistantConfirmationHandler?,
    ) {
        modelCatalog.awaitReady()
        val selection = defaultSelection()
        if (selection == null) {
            _state.update {
                it.copy(
                    phase = AssistantSessionPhase.MISSING_CONFIG,
                    source = source,
                    configIssue = AssistantConfigIssue.MISSING_AGENT_MODEL,
                    taskActive = false,
                )
            }
            return
        }
        val sessionId = ensureSession(selection)
        val gateSource = when (source) {
            AssistantInvocationSource.BLUETOOTH_WAKE -> AgentTaskSource.BLUETOOTH_VOICE
            else -> AgentTaskSource.SYSTEM_ASSISTANT
        }
        val lease = runtimeGate.acquire(gateSource, sessionId)
        val run = TaskRun()
        _state.update {
            it.copy(
                sessionId = sessionId,
                phase = AssistantSessionPhase.GENERATING,
                source = source,
                turn = AssistantTurn(userText = text),
                pendingConfirmation = null,
                errorMessage = null,
                configIssue = null,
                taskActive = true,
            )
        }
        try {
            conversations.setConversationModel(sessionId, ModelSelectionCodec.encode(selection))
            collectTurn(chatAgent.send(sessionId, selection, text, emptyList()), run)
            while (run.pendingConfirmations.isNotEmpty()) {
                lease.updatePhase(AgentTaskPhase.WAITING_FOR_CONFIRMATION)
                val request = run.pendingConfirmations.removeFirst()
                val confirmed = request.toolName in run.approvedTools ||
                    awaitConfirmation(request, confirmationHandler)
                if (confirmed) {
                    run.approvedTools += request.toolName
                } else {
                    run.approvedTools.clear()
                }
                lease.updatePhase(AgentTaskPhase.GENERATING)
                _state.update {
                    it.copy(
                        phase = AssistantSessionPhase.GENERATING,
                        pendingConfirmation = null,
                    )
                }
                collectTurn(
                    chatAgent.respondToToolConfirmation(
                        sessionId = sessionId,
                        confirmationCallId = request.confirmationCallId,
                        confirmed = confirmed,
                    ),
                    run,
                )
            }
            conversations.refreshConversation(sessionId)
            conversations.notifyConversationContentChanged(sessionId)
            _state.update {
                it.copy(
                    phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                    taskActive = false,
                    pendingConfirmation = null,
                )
            }
        } catch (cancelled: CancellationException) {
            _state.update {
                it.copy(
                    phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                    taskActive = false,
                    pendingConfirmation = null,
                )
            }
            throw cancelled
        } catch (error: Throwable) {
            _state.update {
                it.copy(
                    phase = AssistantSessionPhase.ERROR,
                    errorMessage = error.message,
                    taskActive = false,
                    pendingConfirmation = null,
                )
            }
        } finally {
            confirmationResponse?.cancel()
            confirmationResponse = null
            lease.release()
        }
    }

    /** 收集一轮事件流：归并回答文本、推导工具阶段、提取确认请求。 */
    private suspend fun collectTurn(events: Flow<ChatRunEvent>, run: TaskRun) {
        events.collect { event ->
            if (event.author != "user") {
                val text = event.parts
                    .filter { !it.thought && it.text != null }
                    .joinToString("") { it.text.orEmpty() }
                if (text.isNotBlank()) {
                    if (event.partial) run.partial.append(text) else run.completed = text
                    publishTurn(run)
                }
            }
            val toolCalls = event.functionCalls.filter { it.confirmationRequest == null }
            if (toolCalls.isNotEmpty()) {
                run.toolNames += toolCalls.map { it.name }.filter { it !in run.toolNames }
                _state.update {
                    it.copy(
                        phase = AssistantSessionPhase.EXECUTING_TOOL,
                        turn = it.turn?.copy(toolNames = run.toolNames),
                    )
                }
            } else if (event.functionResponses.isNotEmpty()) {
                _state.update { it.copy(phase = AssistantSessionPhase.GENERATING) }
            }
            event.functionCalls.forEach { call ->
                val confirmationId = call.id ?: return@forEach
                val request = call.confirmationRequest ?: return@forEach
                if (!run.seenConfirmationIds.add(confirmationId)) return@forEach
                run.pendingConfirmations += PendingAssistantConfirmation(
                    confirmationCallId = confirmationId,
                    toolName = request.toolName,
                    arguments = request.args,
                    deadlineEpochMs = 0L,
                )
            }
        }
    }

    private suspend fun awaitConfirmation(
        request: PendingAssistantConfirmation,
        handler: AssistantConfirmationHandler?,
    ): Boolean {
        val presented = request.copy(
            deadlineEpochMs = System.currentTimeMillis() + CONFIRMATION_TIMEOUT_MS,
        )
        _state.update {
            it.copy(
                phase = AssistantSessionPhase.AWAITING_CONFIRMATION,
                pendingConfirmation = presented,
            )
        }
        if (handler != null) return handler.confirm(presented)
        val deferred = CompletableDeferred<Boolean>()
        confirmationResponse = deferred
        return withTimeoutOrNull(CONFIRMATION_TIMEOUT_MS) { deferred.await() } ?: false
    }

    private fun publishTurn(run: TaskRun) {
        val response = run.completed.ifBlank { run.partial.toString() }
        _state.update { state ->
            state.copy(turn = state.turn?.copy(responseText = response))
        }
    }

    private suspend fun ensureSession(selection: ModelSelection): String {
        sessionStore.voiceSessionId.value?.takeIf(String::isNotBlank)?.let { saved ->
            if (conversations.loadMessages(saved) != null) return saved
        }
        val created = conversations.createConversation(
            initialModel = ModelSelectionCodec.encode(selection),
            activate = false,
        )
        check(created.isNotBlank()) { "Unable to create the shared voice session." }
        sessionStore.setVoiceSessionId(created)
        return created
    }

    private fun defaultSelection(): ModelSelection? {
        val services = modelCatalog.currentServices()
        val saved = modelCatalog.currentAssistantSelection()
        if (saved != null && services.isUsable(saved)) return saved
        return services.asSequence()
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

    private class TaskRun {
        val partial = StringBuilder()
        var completed = ""
        val toolNames = mutableListOf<String>()
        val pendingConfirmations = ArrayDeque<PendingAssistantConfirmation>()
        val seenConfirmationIds = mutableSetOf<String>()
        val approvedTools = mutableSetOf<String>()
    }

    private companion object {
        const val CONFIRMATION_TIMEOUT_MS = 15_000L
    }
}

private fun List<ModelService>.isUsable(selection: ModelSelection): Boolean {
    val service = firstOrNull { it.id == selection.serviceId } ?: return false
    if (!service.isEnabled || service.apiKey.isBlank()) return false
    val group = service.groups.firstOrNull { it.id == selection.groupId } ?: return false
    val model = group.models.firstOrNull { it.id == selection.modelId } ?: return false
    return !model.isStt && !model.isTts
}
