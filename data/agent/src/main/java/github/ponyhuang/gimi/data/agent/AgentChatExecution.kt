package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.domain.conversation.model.ChatbotStopped
import github.ponyhuang.gimi.domain.conversation.model.WebCitationMetadata
import github.ponyhuang.gimi.domain.conversation.model.WebCitation
import kotlinx.coroutines.CancellationException
import android.util.Log
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.agents.ToolExecutionMode
import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.domain.conversation.model.CONVERSATION_TITLE_STATE_KEY
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.VendorFileReference
import github.ponyhuang.gimi.domain.mobileuse.MobileUseController
import github.ponyhuang.gimi.domain.modelaccess.CONVERSATION_TITLE_METADATA_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.cancelAndJoin
import github.ponyhuang.gimi.data.agent.a2ui.A2uiCreateScope
import github.ponyhuang.gimi.data.agent.a2ui.taskResult
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationTaskCodec
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationStatus

/** 单轮执行句柄，恢复只使用创建时的 Runner 与元数据，不访问全局配置或 LRU。 */
class AgentChatExecution internal constructor(
    private val userId: String,
    private val sessionId: String,
    private val runner: InMemoryRunner,
    private val customMetadata: Map<String, Any>,
    private val mobileUseController: MobileUseController? = null,
    /** Chatbot 形态：流正常收尾后把标题插件写入会话状态的 AI 标题补发成一次事件。 */
    private val emitSessionTitle: Boolean = false,
    private val toolExecutionMode: ToolExecutionMode = ToolExecutionMode.PARALLEL,
) {
    private val pendingConfirmationIds = linkedSetOf<String>()
    private val confirmationResponses = linkedMapOf<String, FunctionResponse>()

    /** 把新用户消息发送给本轮 Agent。 */
    suspend fun send(
        text: String,
        fileAttachments: List<FileAttachment> = emptyList(),
        retry: Boolean = false,
    ): Flow<Event> {
        val resumeInvocationId = if (retry) {
            // 用户事件先于模型输出落盘；即使首个输出前报错，也从 SDK 日志取回原执行。
            runner.sessionService.getSession(
                SessionKey(AgentChatRunner.APP_NAME, userId, sessionId),
            )?.events?.lastOrNull { it.invocationId != null }?.invocationId
        } else null
        val parts = if (resumeInvocationId != null) emptyList() else buildList {
            text.takeIf(String::isNotBlank)?.let { add(Part(text = it)) }
            fileAttachments.forEach { attachment ->
                // 厂商 file 引用（上传直发/抽屉复用）与本地路径共用 FileData 通道：
                // FILE_REFERENCE 模式下 file 引用在解析边界直通，本地文档路径降级占位。
                val vendorUri = attachment.fileVendor?.let { vendor ->
                    attachment.fileId?.let { VendorFileReference.uri(vendor, it) }
                }
                add(
                    Part(
                        fileData = FileData(
                            mimeType = attachment.mimeType,
                            displayName = attachment.displayName,
                            fileUri = vendorUri ?: requireNotNull(attachment.payloadReference) {
                                "Managed attachment reference is missing"
                            },
                        ),
                    ),
                )
            }
        }
        val newMessage = Content(
            role = Role.USER,
            parts = parts,
        )
        val stopSnapshot = if (emitSessionTitle) StoppedChatbotReply() else null
        return runner.runAsync(
            userId = userId,
            sessionId = sessionId,
            // 没有已保存的执行时才发送原请求；恢复时不重复写入用户事件。
            invocationId = resumeInvocationId,
            newMessage = newMessage.takeIf { resumeInvocationId == null },
            stateDelta = null,
            runConfig = RunConfig(
                streamingMode = StreamingMode.SSE,
                customMetadata = customMetadata,
                toolExecutionMode = toolExecutionMode,
            ),
        ).withAsyncA2uiCreates().transform { event ->
            captureConfirmations(event)
            stopSnapshot?.accept(event)
            emit(event)
            // SDK 错误事件也属于失败；立即结束收集，避免被记为已完成而无法恢复。
            if (event.errorCode != null || !event.errorMessage.isNullOrBlank()) {
                throw IllegalStateException(event.errorMessage ?: event.errorCode)
            }
        }.onCompletion { cause ->
            if (cause is ChatbotStopped || (cause is CancellationException && cause.cause is ChatbotStopped)) {
                withContext(NonCancellable) {
                    try {
                        stopSnapshot?.save()
                    } catch (failure: Exception) {
                        Log.w("AgentChatExecution", "Could not save the stopped Chatbot reply", failure)
                    }
                }
            }
        }.withSessionTitleOnCompletion().flushSessionOnCompletion().flowOn(Dispatchers.IO).releaseMobileUseOnCompletion()
    }

    private inner class StoppedChatbotReply {
        private var reply: Event? = null
        private val text = StringBuilder()
        private var citations = emptyList<WebCitation>()

        fun accept(event: Event) {
            if (!event.partial || event.author == Role.USER) return
            val addition = event.content?.parts.orEmpty().filterNot { it.thought == true }
                .mapNotNull { it.text }.joinToString("")
            if (addition.isNotEmpty()) {
                if (reply == null) reply = event
                text.append(addition)
            }
            event.customMetadata?.get(WebCitationMetadata.KEY)?.let { citations = WebCitationMetadata.decode(it) }
        }

        suspend fun save() {
            val event = reply ?: return
            val invocation = event.invocationId ?: return
            val body = text.toString().takeIf(String::isNotBlank) ?: return
            val id = "$invocation:stopped-chatbot-reply"
            val key = SessionKey(AgentChatRunner.APP_NAME, userId, sessionId)
            val service = runner.sessionService
            service.flush(key)
            val session = service.getSession(key) ?: return
            if (session.events.any {
                it.id == id || (it.invocationId == invocation && it.author == event.author &&
                    it.isFinalResponse && it.content?.parts.orEmpty().any { part ->
                        part.thought != true && !part.text.isNullOrBlank()
                    })
            }) return
            // 快照独立于原 invocation 的执行检查点，恢复运行时仍可找到原用户事件。
            val marker = WebCitationMetadata.STOPPED_REPLY_KEY
            service.appendEvent(session, Event(
                id = id,
                author = event.author,
                branch = event.branch,
                content = Content(role = Role.MODEL, parts = listOf(
                    Part(text = body, partMetadata = mapOf(marker to invocation)),
                )),
                customMetadata = mapOf(
                    marker to invocation,
                    WebCitationMetadata.KEY to WebCitationMetadata.encode(citations.filter { it.endOffset <= body.length }),
                ),
            ))
        }
    }

    /**
     * Chatbot 形态的标题收尾：标题插件在 afterAgent 里完成标题生成（快模型总结）并写入
     * 会话状态，此时事件流已经关闭，宿主拿不到。流正常收尾后读状态补发一次标题事件，
     * 走既有的 `conversationTitle` 通道；厂商内联标题（DeepSeek）在轮内已带出，状态键
     * 相同，因此补发是幂等的。失败/取消不补发。
     */
    private fun Flow<Event>.withSessionTitleOnCompletion(): Flow<Event> = if (!emitSessionTitle) {
        this
    } else {
        onCompletion { cause ->
            if (cause != null) return@onCompletion
            val session = runner.sessionService.getSession(
                SessionKey(AgentChatRunner.APP_NAME, userId, sessionId),
            ) ?: return@onCompletion
            val title = (session.state[CONVERSATION_TITLE_STATE_KEY] as? String)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@onCompletion
            emit(Event(customMetadata = mapOf(CONVERSATION_TITLE_METADATA_KEY to title)))
        }
    }

    /**
     * ADK 1.6.0 写入缓冲刷新：回合结束（无论成功、异常或取消）后主动触发 SessionService.flush，
     * 保证 OrderedEventWriter 队列和缓冲写入全部落盘，防止应用异常退出或切换时事件丢失。
     */
    private fun Flow<Event>.flushSessionOnCompletion(): Flow<Event> = onCompletion {
        withContext(NonCancellable) {
            try {
                runner.sessionService.flush(SessionKey(AgentChatRunner.APP_NAME, userId, sessionId))
            } catch (_: Throwable) {
                // 忽略刷新异常，避免遮蔽上游真实故障
            }
        }
    }

    /**
     * 恢复暂停的 ADK 工具确认请求。
     *
     * @param confirmationCallId 工具确认的调用 ID
     * @param confirmed 用户是否确认
     * @return Event 流
     */
    suspend fun respondToToolConfirmation(
        confirmationCallId: String,
        confirmed: Boolean,
    ): Flow<Event> {
        val response = FunctionResponse(
            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
            id = confirmationCallId,
            response = mapOf("confirmed" to confirmed),
        )
        if (confirmationCallId !in pendingConfirmationIds) {
            return resumeWithFunctionResponse(response)
        }
        confirmationResponses[confirmationCallId] = response
        // ADK 1.2.0 逐个恢复同批确认时会提前结束 invocation；收齐各自决策再恢复，
        // 避免后续批准被已结束的 invocation 静默忽略，也不替用户批准其他工具。
        if (!confirmationResponses.keys.containsAll(pendingConfirmationIds)) return emptyFlow()
        val responses = pendingConfirmationIds.map { confirmationResponses.getValue(it) }
        pendingConfirmationIds.clear()
        confirmationResponses.clear()
        return resumeWithFunctionResponses(responses)
    }

    /**
     * 用用户答复恢复挂起的用户输入请求（`adk_request_input` / `get_user_choice`）。
     *
     * @param callId 挂起的 function call ID
     * @param toolName 触发挂起的工具名（决定 FunctionResponse.name，需与挂起调用一致）
     * @param payload 答复负载（键约定见 `UserInputToolProtocol`）
     * @return Event 流
     */
    suspend fun respondToInputRequest(
        callId: String,
        toolName: String,
        payload: Map<String, Any?>,
    ): Flow<Event> = resumeWithFunctionResponse(
        response = FunctionResponse(
            name = toolName,
            id = callId,
            response = payload,
        ),
    )

    /**
     * 以 role=user 的 `FunctionResponse` 新消息恢复暂停的 invocation。
     *
     * ADK 靠"响应 id 覆盖挂起的长时运行调用 id"判定这是恢复而非新的暂停，
     * 恢复时挂起工具不重跑，响应直接作为调用结果进入模型上下文。
     */
    private suspend fun resumeWithFunctionResponse(
        response: FunctionResponse,
    ): Flow<Event> = resumeWithFunctionResponses(listOf(response))

    private suspend fun resumeWithFunctionResponses(
        responses: List<FunctionResponse>,
    ): Flow<Event> {
        val resumeMessage = Content(
            role = Role.USER,
            parts = responses.map { Part(functionResponse = it) },
        )
        return runner.runAsync(
            userId = userId,
            sessionId = sessionId,
            invocationId = null,
            newMessage = resumeMessage,
            stateDelta = null,
            // 恢复调用沿用最近一次 send 的工具配置，保证 Toolset 过滤上下文一致。
            runConfig = RunConfig(
                streamingMode = StreamingMode.SSE,
                customMetadata = customMetadata,
                toolExecutionMode = toolExecutionMode,
            ),
        ).withAsyncA2uiCreates().transform { event ->
            captureConfirmations(event)
            emit(event)
            if (event.errorCode != null || !event.errorMessage.isNullOrBlank()) {
                throw IllegalStateException(event.errorMessage ?: event.errorCode)
            }
        }.flushSessionOnCompletion().flowOn(Dispatchers.IO).releaseMobileUseOnCompletion()
    }

    private fun captureConfirmations(event: Event) {
        event.functionCalls().filter {
            it.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME
        }.mapNotNullTo(pendingConfirmationIds) { it.id }
    }

    /** SDK 错误事件已投递后用于中止 Runner 收集的内部信号，不传播给下游。 */
    private class SdkErrorHandled : RuntimeException()

    /** 正文与创建并行；结果在 Runner 结束后归档，整轮完成仍等待所有卡片。 */
    private fun Flow<Event>.withAsyncA2uiCreates(): Flow<Event> = channelFlow {
        val creates = A2uiCreateScope(this)
        var stoppedBySdkError = false
        suspend fun persist(result: Map<String, Any>): Event? {
            val task = requireNotNull(A2uiGenerationTaskCodec.extract(result))
            val session = runner.sessionService.getSession(
                SessionKey(AgentChatRunner.APP_NAME, userId, sessionId),
            ) ?: return null
            val event = Event(
                author = A2uiGenerationTaskCodec.AUTHOR,
                invocationId = task.invocationId,
                content = Content(role = Role.SYSTEM, parts = listOf(Part(
                    text = "A2UI surface ${task.surfaceId}: ${task.status.name.lowercase()}.",
                ))),
                customMetadata = mapOf(A2uiGenerationTaskCodec.RESULT_KEY to result),
            )
            runner.sessionService.appendEvent(session, event)
            return event
        }
        try {
            withContext(creates) {
                try {
                    this@withAsyncA2uiCreates.collect { event ->
                        send(event)
                        // SDK 错误事件也属于失败：投递后立即在 Runner 流的上游侧中止收集，
                        // 避免 Runner 正常收尾把该 invocation 记为已完成、重试不再调用模型。
                        // 失败异常仍由下游 transform 抛出，保证错误事件先被业务侧收到。
                        if (event.errorCode != null || !event.errorMessage.isNullOrBlank()) {
                            throw SdkErrorHandled()
                        }
                    }
                } catch (_: SdkErrorHandled) {
                    stoppedBySdkError = true
                }
            }
            if (!stoppedBySdkError) {
                for (entry in creates.snapshot()) {
                    val result = entry.result.await()
                    val event = persist(entry.replacement?.plus(taskResult(entry.task.copy(status = A2uiGenerationStatus.COMPLETED))) ?: result)
                    entry.delivered = true
                    if (event != null) send(event)
                }
            }
        } finally {
            // 停止、模型错误或宿主销毁都结束尚未交付的占位；不能留下永久 loading。
            withContext(NonCancellable) {
                for (entry in creates.snapshot().filterNot { it.delivered }) {
                    entry.result.cancelAndJoin()
                    persist(taskResult(entry.task.copy(status = A2uiGenerationStatus.CANCELLED)))
                }
            }
        }
    }

    private fun Flow<Event>.releaseMobileUseOnCompletion(): Flow<Event> = onStart {
        ToolRunMetadata.mobileUseOwner(customMetadata)?.let { owner ->
            mobileUseController?.registerExecution(owner, sessionId)
        }
    }.onCompletion {
        val owner = ToolRunMetadata.mobileUseOwner(customMetadata) ?: return@onCompletion
        // 取消也释放本轮占用；画面属于聊天，保留到用户主动关闭。
        withContext(NonCancellable) { mobileUseController?.finishExecution(owner) }
    }
}
