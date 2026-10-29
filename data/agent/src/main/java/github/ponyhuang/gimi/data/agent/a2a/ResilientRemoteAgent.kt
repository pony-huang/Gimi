package github.ponyhuang.gimi.data.agent.a2a

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.callbacks.BeforeModelCallback
import com.google.adk.kt.callbacks.BeforeToolCallback
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform

/** 隔离远程调用失败；最后输出非终结的本地结果，让父助手在同一轮继续回答。 */
internal class ResilientRemoteAgent(
    private val delegate: BaseAgent,
) : BaseAgent(name = delegate.name, description = delegate.description) {

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        val alreadyFailed = context.getEvents(currentInvocation = true).failedRemoteNames().contains(name)
        if (alreadyFailed) {
            emitFailure(context)
            return@flow
        }
        emitAll(delegate.runAsync(context).transform { event ->
            // SDK 既可能抛异常，也可能输出任务失败事件；不能让后者穿透到聊天级错误处理。
            if (event.errorCode != null || !event.errorMessage.isNullOrBlank()) {
                throw RemoteAgentResponseException()
            }
            emit(event)
        }.catch { failure ->
            if (failure is CancellationException || failure !is Exception) throw failure
            emitFailure(context)
        })
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<Event>.emitFailure(context: InvocationContext) {
        val callId = FunctionCall.generateId()
        val event = Event(
            invocationId = context.invocationId,
            author = name,
            branch = context.branch,
            customMetadata = mapOf(REMOTE_FAILURE_KEY to name),
        )
        // 必须成对输出调用和结果：普通文本被 ADK 视为最终回答，会直接结束父模型循环。
        emit(event.copy(content = Content(role = "model", parts = listOf(
            Part(functionCall = FunctionCall(id = callId, name = REMOTE_FAILURE_RESULT, args = emptyMap())),
        ))))
        emit(event.copy(content = Content(role = "user", parts = listOf(
            Part(functionResponse = FunctionResponse(
                id = callId,
                name = REMOTE_FAILURE_RESULT,
                response = remoteFailureResult(),
            )),
        ))))
    }
}

/** 只用于把 SDK 错误事件转换为本地失败结果；不传播可能含敏感信息的远端错误文本。 */
private class RemoteAgentResponseException : Exception()

private const val REMOTE_FAILURE_KEY = "gimi.remote_agent_failure"
private const val REMOTE_FAILURE_RESULT = "remote_agent_unavailable"
private const val TRANSFER_TOOL = "transfer_to_agent"

private fun List<Event>.failedRemoteNames(): Set<String> =
    mapNotNull { it.customMetadata?.get(REMOTE_FAILURE_KEY) as? String }.toSet()

private fun remoteFailureResult(): Map<String, Any?> = mapOf(
    "status" to "remote_agent_unavailable",
    "execution_status" to "unknown",
    "instruction" to "Continue as the main assistant. Explain that the remote task could not be confirmed " +
        "and any partial remote output is incomplete. Do not claim success or retry this remote task in this invocation.",
)

/** 按 invocation 的事件隔离失败目标；Runner 跨会话共享，不能把失败状态存到 agent 实例。 */
internal object RemoteAgentFailureRecovery {
    val beforeModel = BeforeModelCallback { context, request ->
        val failed = context.getEvents(currentInvocation = true).failedRemoteNames()
        if (failed.isEmpty()) {
            CallbackChoice.Continue(request)
        } else {
            val tools = request.config.tools?.mapNotNull { tool ->
                val declarations = tool.functionDeclarations ?: return@mapNotNull tool
                val filtered = declarations.mapNotNull declarations@{ declaration ->
                    if (declaration.name != TRANSFER_TOOL) return@declarations declaration
                    val parameters = declaration.parameters ?: return@declarations declaration
                    val properties = parameters.properties ?: return@declarations declaration
                    val target = properties["agent_name"] ?: return@declarations declaration
                    val remaining = target.enum.orEmpty().filterNot { it in failed }
                    if (remaining.isEmpty()) null else declaration.copy(
                        parameters = parameters.copy(properties = properties + ("agent_name" to target.copy(enum = remaining))),
                    )
                }
                if (filtered.isEmpty()) null else tool.copy(functionDeclarations = filtered)
            }
            CallbackChoice.Continue(request.copy(config = request.config.copy(tools = tools)).appendInstructions(
                Content(parts = listOf(Part(text =
                    "Remote agents unavailable for this invocation: ${failed.joinToString()}. " +
                        "Do not transfer to them again. Continue helping the user and disclose that their remote tasks are unconfirmed.",
                ))),
            ))
        }
    }

    val beforeTool = BeforeToolCallback { context, tool, args ->
        if (tool.name == TRANSFER_TOOL && args["agent_name"] in context.getEvents(currentInvocation = true).failedRemoteNames()) {
            CallbackChoice.Break(remoteFailureResult())
        } else {
            CallbackChoice.Continue(args)
        }
    }
}
