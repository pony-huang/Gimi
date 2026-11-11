package github.ponyhuang.gimi.data.agent.phone

import android.util.Log
import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneController
import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * 直控手机子代理：模型经 `transfer_to_agent` 委派的非 LLM 执行体，直接操作手机主屏
 * （区别于挂在本代理工具集上、操作隔离副屏的 mobile_* 工具）。
 *
 * 委派后从会话中最近一条带文本的用户消息提取任务（ADK `transfer_to_agent` 只传
 * `agent_name`，不携带整理后的指令），整段交给 [DirectPhoneController] 执行
 * （观察/决策/操作循环属于实现方内部细节），再把结构化终局结果以成对的
 * function call/response 事件发回，让父模型在同一轮内继续处理。不得发普通文本：
 * 文本会被 ADK 当作最终回答，直接结束父模型循环。
 *
 * 后端异常与会话标识缺失都不冒泡：转成非终局的结构化结果，父模型据此回退并说明；
 * 协程取消例外，必须原样传播。
 */
internal class DirectPhoneAgent(
    private val controller: DirectPhoneController,
) : BaseAgent(name = NAME, description = DESCRIPTION) {

    override fun runAsyncImpl(context: InvocationContext): Flow<Event> = flow {
        val instruction = delegatedInstruction(context)
        if (instruction.isBlank()) {
            emitResult(context, DirectPhoneResult(DirectPhoneResult.STATUS_EMPTY_TASK, EMPTY_TASK_MESSAGE))
            return@flow
        }
        val sessionId = context.session.key.id?.takeIf(String::isNotBlank)
        if (sessionId == null) {
            emitResult(context, DirectPhoneResult(DirectPhoneResult.STATUS_UNAVAILABLE, NO_SESSION_MESSAGE))
            return@flow
        }
        // 直控与副屏是两条独立的执行生命周期：回合的 mobileUseOwner 由
        // AgentChatExecution 用来 register/finish MobileUseController，直控不得复用同一
        // owner，否则两条通道会互相释放。回合身份只作诊断前缀，每次委派再生成唯一后缀。
        val turnOwner = ToolRunMetadata.mobileUseOwner(context.runConfig?.customMetadata)
        val owner = listOfNotNull(turnOwner, OWNER_SCOPE, UUID.randomUUID().toString())
            .joinToString(separator = "/")
        try {
            val result = try {
                controller.execute(owner, sessionId, instruction)
            } catch (failure: Exception) {
                // 取消必须传播；其它后端异常转成结构化失败，让父模型在同一轮继续回答。
                if (failure is CancellationException) throw failure
                DirectPhoneResult(DirectPhoneResult.STATUS_FAILED, BACKEND_FAILURE_MESSAGE)
            }
            emitResult(context, result)
        } finally {
            // 取消或失败都必须释放执行权；释放边界不可被取消，释放失败也不得覆盖本轮结果。
            withContext(NonCancellable) {
                try {
                    controller.finishExecution(owner)
                } catch (failure: Exception) {
                    // 取消原样传播；其余释放失败只记日志归因，已回传的结构化结果不因此变成整轮失败。
                    if (failure is CancellationException) throw failure
                    Log.w(TAG, "直控执行权释放失败: $owner", failure)
                }
            }
        }
    }

    /**
     * 取会话中最近一条带非空文本的用户消息。
     *
     * 恢复型 invocation（`resumeWithFunctionResponse` 以 `invocationId = null` 开启）里唯一的
     * 用户事件只有 FunctionResponse 而无文本，任务文本留在更早的 invocation；只按当前
     * invocation 过滤，或用 lastOrNull 命中无文本的用户事件，都会丢掉真实任务。会话事件按
     * 时间追加，倒序取第一条带文本的用户消息即「委派前最近的用户目标」。
     */
    private suspend fun delegatedInstruction(context: InvocationContext): String =
        context.getEvents(currentInvocation = false)
            .asReversed()
            .firstNotNullOfOrNull { event -> event.userTextOrNull() }
            .orEmpty()

    private fun Event.userTextOrNull(): String? {
        if (author != USER_AUTHOR) return null
        return content?.parts.orEmpty()
            .mapNotNull { part -> part.text?.takeIf(String::isNotBlank) }
            .joinToString(separator = "\n")
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private suspend fun FlowCollector<Event>.emitResult(context: InvocationContext, result: DirectPhoneResult) {
        val callId = FunctionCall.generateId()
        val event = Event(invocationId = context.invocationId, author = name, branch = context.branch)
        emit(event.copy(content = Content(role = "model", parts = listOf(
            Part(functionCall = FunctionCall(id = callId, name = RESULT_TOOL, args = emptyMap())),
        ))))
        emit(event.copy(content = Content(role = "user", parts = listOf(
            Part(functionResponse = FunctionResponse(
                id = callId,
                name = RESULT_TOOL,
                response = mapOf("status" to result.status, "message" to result.message),
            )),
        ))))
    }

    companion object {
        /** transfer_to_agent 的委派目标名；必须在整个 agent 树内唯一。 */
        const val NAME: String = "phone_operator"

        /** 委派目标描述：告诉父模型何时选择本代理而非副屏 mobile_* 工具。 */
        internal const val DESCRIPTION: String =
            "Directly operate the physical phone's main screen (the display the user is looking at). " +
                "Delegate here when the user asks to control the phone itself, such as opening apps, " +
                "tapping, typing or completing multi-step tasks on the phone's primary display, instead " +
                "of on the isolated secondary display."

        /** 结果回传的成对调用名；父模型据此读取 status/message 继续本轮回答。 */
        internal const val RESULT_TOOL: String = "direct_phone_result"

        /** 直控执行权的独立作用域；与副屏回合 owner 明确区分。 */
        internal const val OWNER_SCOPE: String = "direct_phone"

        private const val TAG: String = "DirectPhoneAgent"

        private const val USER_AUTHOR: String = "user"
        private const val EMPTY_TASK_MESSAGE: String =
            "No delegable user task was found in this session. Ask the user what to do on the phone " +
                "instead of transferring here again."
        private const val NO_SESSION_MESSAGE: String =
            "The chat session has no identifier, so a direct phone operation cannot be attributed. " +
                "Do not retry the transfer in this invocation; continue as the main assistant and " +
                "explain that direct phone control is unavailable for this session."
        private const val BACKEND_FAILURE_MESSAGE: String =
            "Direct phone control failed before completing the task. Do not claim success and do not " +
                "retry the transfer in this invocation. Continue as the main assistant, tell the " +
                "user the phone operation could not be completed, and use only tools that are " +
                "actually exposed in this session if they can help."
    }
}
