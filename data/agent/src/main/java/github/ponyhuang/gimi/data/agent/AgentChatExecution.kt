package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
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
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** 单轮执行句柄，恢复只使用创建时的 Runner 与元数据，不访问全局配置或 LRU。 */
class AgentChatExecution internal constructor(
    private val userId: String,
    private val sessionId: String,
    private val runner: InMemoryRunner,
    private val customMetadata: Map<String, Any>,
    private val mobileUseRepository: MobileUseRepository? = null,
) {
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
                add(
                    Part(
                        fileData = FileData(
                            mimeType = attachment.mimeType,
                            displayName = attachment.displayName,
                            fileUri = requireNotNull(attachment.payloadReference) {
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
            ),
        ).transform { event ->
            emit(event)
            // SDK 错误事件也属于失败；立即结束收集，避免被记为已完成而无法恢复。
            if (event.errorCode != null || !event.errorMessage.isNullOrBlank()) {
                throw IllegalStateException(event.errorMessage ?: event.errorCode)
            }
        }.flowOn(Dispatchers.IO).releaseMobileUseOnCompletion()
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
    ): Flow<Event> = resumeWithFunctionResponse(
        response = FunctionResponse(
            name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
            id = confirmationCallId,
            response = mapOf("confirmed" to confirmed),
        ),
    )

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
    ): Flow<Event> {
        val resumeMessage = Content(
            role = Role.USER,
            parts = listOf(Part(functionResponse = response)),
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
            ),
        ).flowOn(Dispatchers.IO).releaseMobileUseOnCompletion()
    }

    private fun Flow<Event>.releaseMobileUseOnCompletion(): Flow<Event> = onStart {
        ToolRunMetadata.mobileUseOwner(customMetadata)?.let { owner ->
            mobileUseRepository?.registerExecution(owner, sessionId)
        }
    }.onCompletion {
        val owner = ToolRunMetadata.mobileUseOwner(customMetadata) ?: return@onCompletion
        // 取消也释放本轮占用；画面属于聊天，保留到用户主动关闭。
        withContext(NonCancellable) { mobileUseRepository?.finishExecution(owner) }
    }
}
