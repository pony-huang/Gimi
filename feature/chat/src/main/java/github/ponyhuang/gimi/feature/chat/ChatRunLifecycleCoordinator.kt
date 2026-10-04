package github.ponyhuang.gimi.feature.chat

import android.util.Log
import github.ponyhuang.gimi.core.notifications.AppNotificationManager
import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ChatTurnStatus
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.UserInputKind
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionBusyException
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 会话运行的启动、暂停恢复、取消与收尾；发送和交互恢复共用同一套 lease/token 规则。
 *
 * runtime 与 UI 状态仍由 ViewModel 持有。暂停保留执行与 lease，恢复先等待旧 Job 退出；
 * 只有持有当前 token 的任务能收尾，避免旧任务释放新任务的运行权限。
 */
internal class ChatRunLifecycleCoordinator(
    private val uiState: StateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val agentRuntimeGate: AgentRuntimeGate,
    private val repository: ConversationRepository,
    private val toolApproval: ToolApprovalRepository,
    private val appNotificationManager: AppNotificationManager,
    private val runtimeFor: (String) -> ChatSessionRuntime,
    private val runtimeOrNull: (String) -> ChatSessionRuntime?,
    private val publishRuntime: (ChatSessionRuntime) -> Unit,
    private val emitNotice: (ChatNotice) -> Unit,
    private val eventReducer: () -> AgentEventReducer,
    private val recordFailure: (String, ChatTurn) -> Unit,
    private val onForegroundCompleted: (String, ChatSessionRuntime) -> Unit,
    private val refreshVisibleHistoryIfStale: suspend () -> Unit,
) {
    /** Sends the user's decision back to ADK, which then either runs or rejects the paused tool. */
    fun respondToToolConfirmation(confirmed: Boolean, alwaysAllow: Boolean = false) {
        val sessionId = uiState.value.sessionId
        if (sessionId.isBlank()) return
        respondToToolConfirmation(sessionId, confirmed, alwaysAllow)
    }

    fun respondToToolConfirmation(
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
            runtime.toolStatuses[ToolCallKey(request.originalCallId, request.toolName)] =
                ToolCallStatus.Running
        } else {
            runtime.approvedToolsThisTurn.clear()
            runtime.toolStatuses[ToolCallKey(request.originalCallId, request.toolName)] =
                ToolCallStatus.Rejected
        }
        val previousJob = cancelRun(runtime, releaseLease = false)
        launchRun(runtime) { runToken ->
            previousJob?.join()
            checkNotNull(runtime.execution) { "No active conversation execution" }.respondToToolConfirmation(
                confirmationCallId = request.confirmationCallId,
                confirmed = confirmed,
            ).collect { event ->
                eventReducer().applyEvent(sessionId, event, runToken)
            }
        }
    }

    /** 把用户对挂起输入请求的答复送回 ADK，恢复暂停的 invocation。 */
    fun respondToInputRequest(callId: String, value: String) {
        val sessionId = uiState.value.sessionId
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
                eventReducer().applyEvent(sessionId, event, runToken)
            }
        }
    }

    fun cancelRun(runtime: ChatSessionRuntime, releaseLease: Boolean = true): Job? {
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
    fun launchRun(
        runtime: ChatSessionRuntime,
        onFailure: (Throwable) -> Unit = { failure ->
            runtime.lastTurn?.let { recordFailure(runtime.sessionId, it) }
        },
        execute: suspend (Any) -> Unit,
    ): Job {
        val token = Any()
        runtime.runToken = token
        runtime.isAgentRunning = true
        runtime.phase = AgentTaskPhase.GENERATING
        publishRuntime(runtime)
        // 先登记 job 再执行，避免 Main.immediate 同步结束后把已完成 job 写回 runtime。
        val job = scope.launch(start = CoroutineStart.LAZY) {
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
                Log.w(TAG, "Chat run failed for session ${runtime.sessionId}", failure)
                if (runtime.runToken === token) {
                    eventReducer().applyError(
                        runtime.sessionId,
                        failure.message ?: failure::class.simpleName ?: "Unknown error",
                    )
                    if (uiState.value.sessionId.isBlank()) {
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
                appNotificationManager.notifyToolConfirmation(
                    toolName = pending.toolName,
                    taskId = sessionId,
                )
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
                when (pendingInput.kind) {
                    UserInputKind.CHOICE -> appNotificationManager.notifyChoice(sessionId)
                    UserInputKind.FREE_TEXT -> appNotificationManager.notifyTextInput(sessionId)
                }
                publishRuntime(runtime)
            }
            else -> {
                runtime.execution = null
                if (runtime.failed) {
                    runtime.lastTurn?.takeIf { it.status != ChatTurnStatus.FAILED }
                        ?.let { recordFailure(sessionId, it) }
                } else {
                    runtime.lastTurn = null
                }
                runtime.approvedToolsThisTurn.clear()
                appNotificationManager.cancelPendingInteractionNotifications(sessionId)
                if (completedNormally && !runtime.failed) {
                    // SDK 重试会替换未保存的 partial，但不会推进外部写入版本。
                    // 让已有的带会话、运行身份保护的刷新入口重新读取最终历史。
                    if (runtime.retryingInvocation) runtime.loadedContentRevision = -1L
                    appNotificationManager.notifyTaskCompleted(sessionId)
                }
                runtime.retryingInvocation = false
                releaseRunLease(runtime)
                if (uiState.value.sessionId != sessionId) {
                    runtime.attention = when {
                        runtime.failed -> SessionResultAttention.FAILED
                        completedNormally -> SessionResultAttention.COMPLETED
                        else -> SessionResultAttention.NONE
                    }
                } else if (completedNormally) {
                    onForegroundCompleted(sessionId, runtime)
                }
                publishRuntime(runtime)
            }
        }
        scope.launch { refreshVisibleHistoryIfStale() }
    }

    fun clearToolConfirmationState(runtime: ChatSessionRuntime) {
        runtime.approvedToolsThisTurn.clear()
        runtime.pendingToolConfirmations = emptyList()
        runtime.autoApprovedConfirmations = emptyList()
        runtime.toolStatuses.clear()
        publishRuntime(runtime)
    }

    /**
     * 主动停止同步解锁 composer，并结束部分消息的流式显示，保留失败轮供重试。
     * 挂起确认通过拒绝恢复协议；输入请求没有拒绝语义，保留请求等待用户答复。
     */
    fun stopStreaming() {
        val sessionId = uiState.value.sessionId
        val runtime = runtimeOrNull(sessionId) ?: return
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
        // 用户主动停止的轮次同样保留“重试”：把已生成的部分回答一并落盘为可恢复轮，
        // 重试时交给 ADK Runner 恢复原 invocation。
        runtime.lastTurn?.takeIf { it.status == ChatTurnStatus.RUNNING }?.let { stopped ->
            recordFailure(sessionId, stopped)
        }
        publishRuntime(runtime)
    }

    private companion object {
        private const val TAG = "ChatRunLifecycle"
    }
}
