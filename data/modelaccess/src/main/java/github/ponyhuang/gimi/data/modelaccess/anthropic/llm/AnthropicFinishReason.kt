package github.ponyhuang.gimi.data.modelaccess.anthropic.llm

import com.anthropic.models.messages.Message
import com.anthropic.models.messages.StopReason
import com.google.adk.kt.types.FinishReason

/**
 * Anthropic 协议族的停止原因 → ADK 统一停止原因。
 *
 * DeepSeek 的 Anthropic 兼容端点复用同一套取值：refusal 表示模型拒答，
 * max_tokens 表示输出触顶被截断，两者都需要传给宿主区分「答完了」和「没答完」。
 * 未知取值归为 [FinishReason.FINISH_REASON_UNSPECIFIED]，不让协议扩展打穿宿主。
 */
internal fun Message.toAdkFinishReason(): FinishReason? = stopReason().map { reason ->
    when (reason.value()) {
        StopReason.Value.REFUSAL -> FinishReason.SAFETY
        StopReason.Value.MAX_TOKENS -> FinishReason.MAX_TOKENS
        StopReason.Value.END_TURN,
        StopReason.Value.STOP_SEQUENCE,
        StopReason.Value.TOOL_USE,
        -> FinishReason.STOP
        else -> FinishReason.FINISH_REASON_UNSPECIFIED
    }
}.orElse(null)
