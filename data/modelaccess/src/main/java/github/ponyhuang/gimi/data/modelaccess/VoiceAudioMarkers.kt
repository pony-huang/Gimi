package github.ponyhuang.gimi.data.modelaccess

/**
 * 组合后端通过官方 customMetadata 附带的可选分句提示，仅含布尔值，不传递私有 SDK 对象。
 * 原生模型无需实现该提示：没有分句边界时以完整话轮作为播放确认单元，转写可以晚于音频。
 */
object VoiceAudioMarkers {
    const val SEGMENT_START = "gimi.voice.segment.start"
    const val SEGMENT_END = "gimi.voice.segment.end"

    /**
     * 原生 Realtime 模型标记：服务端已提交用户输入并开始生成响应，
     * 供编排层把「监听」推进到「响应中」；该响应不携带文本或音频负载。
     * 用户侧转写走 [com.google.adk.kt.types.LlmResponse] 的 inputTranscription 通道，不用标记。
     */
    const val TURN_COMMITTED = "gimi.voice.turn.committed"
}
