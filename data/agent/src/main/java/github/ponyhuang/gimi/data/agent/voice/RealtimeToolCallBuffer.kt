package github.ponyhuang.gimi.data.agent.voice

import com.google.gson.Gson

/**
 * 服务端 function_call 参数的按连接累积器。
 *
 * 一轮响应内可有多个 function_call（GA 与 Qwen 同语义）；事件序为
 * `output_item.added`（起始）→ `function_call_arguments.delta`（增量，可多次）
 * → `.done`（定稿）。`.done` 缺失时由调用方在 `response.done` 前冲出未定稿调用，
 * 覆盖「从 response.done 读取完整参数」的服务端行为；响应被取消/打断/失败时未定稿
 * 调用不冲出而是丢弃（[discardUnfinished]）——截断的参数不作执行依据。
 * 仅限接收循环单线程使用。
 */
internal class RealtimeToolCallBuffer {

    /** 一次可执行的函数调用；arguments 为完整参数 JSON 字符串。 */
    class CompletedCall(val callId: String, val name: String, val arguments: String)

    private class Pending(val name: String) {
        val arguments = StringBuilder()
    }

    private val pending = mutableMapOf<String, Pending>()

    /** 记录调用起始；同一 call_id 重复起始时重置累积（防服务端异常重发）。 */
    fun onStarted(callId: String, name: String) {
        pending[callId] = Pending(name)
    }

    /** 累积一段参数增量；未收到起始事件时按空名兜底建档。 */
    fun onDelta(callId: String, delta: String) {
        pending.getOrPut(callId) { Pending("") }.arguments.append(delta)
    }

    /** 定稿一次调用；arguments 缺失时以累积增量为准。 */
    fun onDone(callId: String, arguments: String?): CompletedCall? =
        pending.remove(callId)?.let { pending -> CompletedCall(callId, pending.name, arguments ?: pending.arguments.toString()) }

    /** 冲出全部未定稿调用（响应结束兜底）；之后累积器清空。 */
    fun flushUnfinished(): List<CompletedCall> = pending.map { (callId, pending) ->
        CompletedCall(callId, pending.name, pending.arguments.toString())
    }.also { pending.clear() }

    /** 丢弃全部未定稿调用：响应被取消/打断/失败时截断参数不作执行依据，也不能写回。 */
    fun discardUnfinished() {
        pending.clear()
    }
}

/** 解析 function_call 参数 JSON；空串或非法 JSON 按空参数处理，由工具自身校验给出错误结果。 */
internal fun parseFunctionArguments(json: String): Map<String, Any?> = try {
    @Suppress("UNCHECKED_CAST")
    Gson().fromJson(json.ifBlank { "{}" }, Map::class.java) as? Map<String, Any?> ?: emptyMap()
} catch (_: Exception) {
    emptyMap()
}

/** `response.done` 的处置：正常完成（兜底冲出）、明确未完成（丢弃）、未知（按完成处理并告警）。 */
internal enum class RealtimeResponseDisposition { FLUSH, DISCARD, UNKNOWN }

private val DISCARDED_RESPONSE_STATUSES = setOf("cancelled", "canceled", "incomplete", "failed")
private val COMPLETED_RESPONSE_STATUSES = setOf("completed", "done", "finished", "succeeded", "success")

/**
 * 解析 `response.done` 的 status，两家厂商共用。
 *
 * 只有明确表示响应未正常完成的状态才丢弃未定稿调用；status 缺失、已知完成状态和
 * 未知的厂商终态都按完成处理（后者由调用方记日志），避免厂商新增状态字符串时
 * 静默漏掉本应兜底冲出的合法工具调用。
 */
internal fun realtimeResponseDisposition(status: String?): RealtimeResponseDisposition {
    if (status.isNullOrBlank()) return RealtimeResponseDisposition.FLUSH
    return when (status.trim().lowercase()) {
        in COMPLETED_RESPONSE_STATUSES -> RealtimeResponseDisposition.FLUSH
        in DISCARDED_RESPONSE_STATUSES -> RealtimeResponseDisposition.DISCARD
        else -> RealtimeResponseDisposition.UNKNOWN
    }
}
