package github.ponyhuang.gimi.domain.voice.repository

/**
 * 原生 Realtime 会话经由官方 customMetadata 附带的可选分句提示，仅含布尔值，
 * 不传递私有 SDK 对象。原生模型无需实现该提示：没有分句边界时以完整话轮作为
 * 播放确认单元，转写可以晚于音频。
 */
object VoiceAudioMarkers {
    const val SEGMENT_START = "gimi.voice.segment.start"
    const val SEGMENT_END = "gimi.voice.segment.end"

    /**
     * 原生 Realtime 模型标记：服务端已提交用户输入并开始生成响应，
     * 供编排层把「监听」推进到「响应中」；该响应不携带文本或音频负载。
     * 用户侧转写走协议层的 inputTranscription 通道，不用标记。
     */
    const val TURN_COMMITTED = "gimi.voice.turn.committed"
}

/** 将模型正文增量切成适合顺序合成的句子，并限制无标点长回答的等待长度。 */
class VoiceSentenceBuffer {
    private val pending = StringBuilder()

    fun append(delta: String): List<String> {
        pending.append(delta)
        val sentences = mutableListOf<String>()
        while (pending.isNotEmpty()) {
            val boundary = pending.indices.firstOrNull { index ->
                pending[index] in "。！？!?；;\n" ||
                    (pending[index] == '.' && index + 1 < pending.length && pending[index + 1].isWhitespace())
            } ?: -1
            var length = when {
                boundary in 0..<MAX_LENGTH -> boundary + 1
                pending.length >= MAX_LENGTH -> MAX_LENGTH
                else -> break
            }
            // 定长截断恰好落在句末标点之前时，把紧随的标点一并带走，
            // 否则下一轮会切出一个只有标点的合成单元（表现为莫名单独停顿）。
            if (boundary == -1) {
                while (length < pending.length && pending[length] in "。！？!?；,.，\n") length++
            }
            if (length < pending.length && pending[length - 1].isHighSurrogate() && pending[length].isLowSurrogate()) length--
            pending.substring(0, length).trim().takeIf { it.isNotEmpty() }?.let(sentences::add)
            pending.delete(0, length)
        }
        return sentences
    }

    fun finish(): String = pending.toString().trim().also { pending.clear() }

    private companion object {
        const val MAX_LENGTH = 100
    }
}

/**
 * Realtime 协议的工具回写通道：由各厂商的原生连接实现，门面的工具轮据此把
 * 执行结果写回服务端上下文并触发二轮推理。
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
