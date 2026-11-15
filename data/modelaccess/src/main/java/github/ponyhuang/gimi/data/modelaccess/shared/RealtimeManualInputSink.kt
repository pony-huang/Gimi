package github.ponyhuang.gimi.data.modelaccess.shared

import com.google.adk.kt.types.Content

/** 原生协议明确支持的手动输入；配置确认前不允许提交，也不跨重连重放。 */
interface RealtimeManualInputSink {
    val manualInputReady: Boolean
    suspend fun submitUserContent(content: Content)
}
