@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import android.util.Log
import com.google.adk.kt.types.FunctionCall
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import github.ponyhuang.gimi.data.modelaccess.shared.RealtimeFunctionCallSink

/**
 * 一轮 Realtime 工具调用的执行器：串行执行模型发起的全部 function_call，
 * 结果逐个写回服务端上下文，整轮结束后触发一次二轮推理。
 *
 * 门面在事件接收循环中把工具轮放行到子协程，多轮之间经互斥串行——避免两轮的
 * function_call_output 与 response.create 交错下发，也让执行期间的事件接收
 * （打断、转写）不受阻塞。
 *
 * [bridge] 可为空：模型仍可能发起 function_call（重连后声明未生效、工具面解析
 * 失败或服务端内置工具），此时必须回写一条失败结果再触发二轮推理，否则服务端
 * 会一直等 function_call_output，界面永久停在「正在生成」。同理，任一回写或执行
 * 异常也不能跳过 response.create，它由 finally 保证。
 */
internal class RealtimeToolRoundRunner(
    private val bridge: RealtimeVoiceToolBridge?,
    private val reply: RealtimeFunctionCallSink?,
) {
    private val mutex = Mutex()

    suspend fun run(calls: List<FunctionCall>) = mutex.withLock {
        val sink = reply
        if (sink == null) {
            Log.w(TAG, "连接不支持工具回写，放弃本轮工具调用: ${calls.joinToString { it.name }}")
            return@withLock
        }
        try {
            val executor = bridge
            if (executor == null) {
                Log.w(TAG, "收到 Realtime 工具调用但未启用工具桥，回写失败结果结束本轮")
                for (call in calls) {
                    val callId = call.id.orEmpty()
                    if (callId.isEmpty()) continue
                    sink.sendFunctionResult(callId, TOOL_BRIDGE_UNAVAILABLE_JSON)
                }
                return@withLock
            }
            for (call in calls) {
                val callId = call.id.orEmpty()
                if (callId.isEmpty()) {
                    Log.w(TAG, "工具调用缺少 call_id，跳过: ${call.name}")
                    continue
                }
                sink.sendFunctionResult(callId, executor.execute(callId, call.name, call.args))
            }
        } finally {
            // 服务端只在收到 response.create 后才开始二轮推理。少了这一帧，会话层的
            // toolNotice 永不清除，TurnCompleted 也会把 phase 一直钉在 Thinking。
            sink.sendResponseTrigger()
        }
    }

    private companion object {
        const val TAG = "RealtimeToolRound"
        const val TOOL_BRIDGE_UNAVAILABLE_JSON = "{\"error\":\"Tool bridge unavailable\"}"
    }
}
