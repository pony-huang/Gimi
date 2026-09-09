package github.ponyhuang.gimi.data.assistant

import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantPresentationEvent
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import github.ponyhuang.gimi.domain.assistant.model.PendingAssistantConfirmation
import github.ponyhuang.gimi.domain.assistant.model.appendAssistantMessage
import github.ponyhuang.gimi.domain.assistant.model.appendUserMessage
import github.ponyhuang.gimi.domain.assistant.model.applyPresentationEvent
import github.ponyhuang.gimi.domain.assistant.model.failLastAssistantMessage
import github.ponyhuang.gimi.domain.assistant.model.updateLastAssistantMessage
import github.ponyhuang.gimi.domain.assistant.repository.AssistantConfirmationHandler
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSubmissionResult
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.UserInputRequest
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.NoAvailableAssistantModelException
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionBusyException
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.NonCancellable

/**
 * [AssistantSessionCoordinator] 的进程级实现。
 *
 * 同一助手展示只允许一个活动任务；竞争提交立即返回 busy。任务在协调器自有作用域执行，
 * [stop] 只取消任务协程，展示界面关闭（[hidePresentation]）不影响任务。
 */
@Singleton
class DefaultAssistantSessionCoordinator @Inject constructor(
    private val conversations: ConversationRepository,
    private val chatAgent: ChatAgentRepository,
    private val runtimeGate: AgentRuntimeGate,
    private val sessionResolver: ConversationSessionResolver,
    private val toolApproval: ToolApprovalRepository,
) : AssistantSessionCoordinator {

    /** 测试可替换的任务调度器；必须在首次提交前设置。 */
    internal var taskDispatcher: CoroutineDispatcher = Dispatchers.Default

    private val scope by lazy { CoroutineScope(SupervisorJob() + taskDispatcher) }
    private val submitMutex = Mutex()
    private var runningJob: Job? = null
    private var confirmationResponse: PendingConfirmationResponse? = null

    private val _state = MutableStateFlow(AssistantSessionState())
    override val state: StateFlow<AssistantSessionState> = _state.asStateFlow()

    override fun noteInvocation(source: AssistantInvocationSource) {
        updatePresentation(AssistantPresentationEvent.CaptureStarted(source))
    }

    override fun updatePresentation(event: AssistantPresentationEvent) {
        _state.update { it.applyPresentationEvent(event) }
    }

    override suspend fun submit(
        text: String,
        source: AssistantInvocationSource,
        confirmationHandler: AssistantConfirmationHandler?,
    ): AssistantSubmissionResult {
        if (!submitMutex.tryLock()) {
            return AssistantSubmissionResult.Busy(_state.value.sessionId)
        }
        val result = CompletableDeferred<AssistantSubmissionResult>()
        val job = scope.launch {
            try {
                result.complete(runTask(text, source, confirmationHandler))
            } catch (cancelled: CancellationException) {
                result.complete(AssistantSubmissionResult.Stopped)
                throw cancelled
            }
        }
        runningJob = job
        return try {
            result.await()
        } catch (cancelled: CancellationException) {
            job.cancel()
            throw cancelled
        } finally {
            withContext(NonCancellable) { job.join() }
            if (runningJob === job) runningJob = null
            submitMutex.unlock()
        }
    }

    override fun stop() {
        confirmationResponse?.response?.complete(false)
        _state.update {
            if (it.taskActive) it.applyPresentationEvent(AssistantPresentationEvent.Stopped) else it
        }
        runningJob?.cancel()
    }

    override fun respondToConfirmation(
        confirmationCallId: String,
        confirmed: Boolean,
    ): Boolean {
        val pending = confirmationResponse
            ?.takeIf { it.confirmationCallId == confirmationCallId }
            ?: return false
        return pending.response.complete(confirmed)
    }

    override fun hidePresentation() {
        _state.update { it.copy(presentationVisible = false) }
    }

    private suspend fun runTask(
        text: String,
        source: AssistantInvocationSource,
        confirmationHandler: AssistantConfirmationHandler?,
    ): AssistantSubmissionResult {
        val session = try {
            sessionResolver.resolveCurrentOrCreate()
        } catch (_: NoAvailableAssistantModelException) {
            _state.update {
                it.copy(
                    phase = AssistantSessionPhase.MISSING_CONFIG,
                    source = source,
                    taskActive = false,
                )
            }
            return AssistantSubmissionResult.MissingConfiguration
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val message = error.message ?: "无法准备会话"
            _state.update {
                it.copy(
                    phase = AssistantSessionPhase.ERROR,
                    source = source,
                    errorMessage = message,
                    taskActive = false,
                )
            }
            return AssistantSubmissionResult.Failed(message)
        }
        val sessionId = session.sessionId
        val gateSource = when (source) {
            AssistantInvocationSource.BLUETOOTH_WAKE -> AgentTaskSource.BLUETOOTH_VOICE
            AssistantInvocationSource.ASSISTANT_PANEL -> AgentTaskSource.SYSTEM_ASSISTANT
        }
        val lease = try {
            runtimeGate.acquire(gateSource, sessionId)
        } catch (_: AgentSessionBusyException) {
            _state.update {
                it.copy(
                    sessionId = sessionId,
                    phase = AssistantSessionPhase.BUSY,
                    source = source,
                    taskActive = false,
                    pendingConfirmation = null,
                    errorMessage = null,
                )
            }
            return AssistantSubmissionResult.Busy(sessionId)
        }
        val run = TaskRun()
        _state.update {
            it.copy(
                sessionId = sessionId,
                phase = AssistantSessionPhase.GENERATING,
                source = source,
                pendingConfirmation = null,
                errorMessage = null,
                taskActive = true,
                presentationVisible = true,
            ).appendUserMessage(text).appendAssistantMessage()
        }
        try {
            val execution = chatAgent.createExecution(
                sessionId, session.modelSelection, session.toolConfiguration,
            )
            collectTurn(execution.send(text, emptyList()), run)
            run.error?.let { return finishFailed(it) }
            run.pendingInputRequest?.let { return finishAwaitingInput(it) }
            while (run.pendingConfirmations.isNotEmpty()) {
                lease.updatePhase(AgentTaskPhase.WAITING_FOR_CONFIRMATION)
                val request = run.pendingConfirmations.removeFirst()
                val confirmed = request.toolName in run.approvedTools ||
                    toolApproval.isAutoApproved(request.toolName) ||
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
                    execution.respondToToolConfirmation(
                        confirmationCallId = request.confirmationCallId,
                        confirmed = confirmed,
                    ),
                    run,
                )
                run.error?.let { return finishFailed(it) }
                run.pendingInputRequest?.let { return finishAwaitingInput(it) }
            }
            conversations.refreshConversation(sessionId)
            _state.update {
                it.copy(
                    phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                    taskActive = false,
                    pendingConfirmation = null,
                ).updateLastAssistantMessage()
            }
            return AssistantSubmissionResult.Completed(
                sessionId = sessionId,
                responseText = run.responseText(),
            )
        } catch (cancelled: CancellationException) {
            _state.update {
                if (it.phase == AssistantSessionPhase.STOPPED) {
                    it.copy(taskActive = false, pendingConfirmation = null)
                        .updateLastAssistantMessage()
                } else {
                    it.copy(
                        phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                        taskActive = false,
                        pendingConfirmation = null,
                    ).updateLastAssistantMessage()
                }
            }
            throw cancelled
        } catch (error: Throwable) {
            val message = error.message ?: "出现问题"
            _state.update {
                it.copy(
                    phase = AssistantSessionPhase.ERROR,
                    errorMessage = message,
                    taskActive = false,
                    pendingConfirmation = null,
                ).failLastAssistantMessage(message)
            }
            return AssistantSubmissionResult.Failed(message)
        } finally {
            confirmationResponse?.response?.cancel()
            confirmationResponse = null
            lease.release()
            conversations.notifyConversationContentChanged(sessionId)
        }
    }

    /** 收集一轮事件流：归并回答文本、推导工具阶段、提取确认请求。 */
    private suspend fun collectTurn(events: Flow<ChatRunEvent>, run: TaskRun) {
        events.collect { event ->
            if (run.error != null) return@collect
            event.errorMessage?.let { run.error = it; return@collect }
            event.errorCode?.let { run.error = it; return@collect }
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
                    ).updateLastAssistantMessage(toolNames = run.toolNames)
                }
            } else if (event.functionResponses.isNotEmpty()) {
                _state.update {
                    it.copy(phase = AssistantSessionPhase.GENERATING)
                        .updateLastAssistantMessage(streaming = true)
                }
            }
            event.functionCalls.forEach { call ->
                call.inputRequest?.let { run.pendingInputRequest = it }
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
        confirmationResponse = PendingConfirmationResponse(
            confirmationCallId = request.confirmationCallId,
            response = deferred,
        )
        return withTimeoutOrNull(CONFIRMATION_TIMEOUT_MS) { deferred.await() } ?: false
    }

    private fun publishTurn(run: TaskRun) {
        _state.update {
            it.updateLastAssistantMessage(
                text = run.responseText(),
                streaming = run.completed.isBlank(),
            )
        }
    }

    private fun finishFailed(message: String): AssistantSubmissionResult {
        _state.update {
            it.copy(
                phase = AssistantSessionPhase.ERROR,
                errorMessage = message,
                taskActive = false,
                pendingConfirmation = null,
            ).failLastAssistantMessage(message)
        }
        return AssistantSubmissionResult.Failed(message)
    }

    private fun finishAwaitingInput(request: UserInputRequest): AssistantSubmissionResult {
        val message = request.message.ifBlank { "需要你的输入。" }
        _state.update {
            it.copy(
                phase = AssistantSessionPhase.AWAITING_INPUT,
                taskActive = false,
                pendingConfirmation = null,
            ).updateLastAssistantMessage(text = message, streaming = false)
        }
        return AssistantSubmissionResult.AwaitingInput(message)
    }

    private class TaskRun {
        val partial = StringBuilder()
        var completed = ""
        var error: String? = null
        var pendingInputRequest: UserInputRequest? = null
        val toolNames = mutableListOf<String>()
        val pendingConfirmations = ArrayDeque<PendingAssistantConfirmation>()
        val seenConfirmationIds = mutableSetOf<String>()
        val approvedTools = mutableSetOf<String>()

        fun responseText(): String = completed.ifBlank { partial.toString() }
    }

    private data class PendingConfirmationResponse(
        val confirmationCallId: String,
        val response: CompletableDeferred<Boolean>,
    )

    private companion object {
        const val CONFIRMATION_TIMEOUT_MS = 15_000L
    }
}
