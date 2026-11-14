package github.ponyhuang.gimi.data.modelaccess.shared

/**
 * Realtime 协议的工具回写通道：由各厂商 [com.google.adk.kt.models.LiveConnection] 实现，
 * 门面的工具轮据此把执行结果写回服务端上下文并触发二轮推理。
 *
 * 两条厂商链路的事件语义一致（GA Realtime 与 Qwen-Audio 同源）：先
 * `conversation.item.create`（`function_call_output`）写回结果，再 `response.create`
 * 触发模型基于结果继续生成；发送失败静默降级并记日志，不向会话层暴露瞬时抖动。
 */
interface RealtimeFunctionCallSink {
    /** 写回一次工具执行结果；[outputJson] 为 JSON 字符串（含错误对象）。 */
    suspend fun sendFunctionResult(callId: String, outputJson: String)

    /** 触发服务端基于已写回的工具结果继续生成（二轮推理）。 */
    suspend fun sendResponseTrigger()
}
