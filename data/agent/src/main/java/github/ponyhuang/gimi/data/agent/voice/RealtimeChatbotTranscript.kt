package github.ponyhuang.gimi.data.agent.voice

import android.util.Log
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.CancellationException

/**
 * 原生实时 Chatbot 的终稿收集器：把一通的转写流聚合成轮终稿，经
 * [ChatbotConversationWriter.Call] 落盘到 Chatbot 分区当前会话。
 *
 * 写入策略（只写终稿；起呼与重连历史由会话门面读取并注入）：
 * - 输入转写（用户侧）按最新累计文本替换；输出转写按增量累计。
 * - 正常收尾写入用户 + 模型两侧；打断只写用户侧已定稿话语，被截断的半句
 *   回答不落盘（cancelled 的 response.done 仍会发 turnComplete，此时状态
 *   已清空，自然成为空轮不写）。
 * - 写入失败只告警不中断通话：持久化是附加收益，语音主链路不受影响。
 */
internal class RealtimeChatbotTranscript(private val writer: ChatbotConversationWriter.Call) {
    private var pendingUserText: String? = null
    private var pendingAssistantText: StringBuilder = StringBuilder()

    /** 用户侧输入转写更新（最新累计文本替换渲染）。 */
    fun onUserTranscript(text: String) {
        pendingUserText = text
    }

    /** 模型侧输出转写增量。 */
    fun onAssistantDelta(text: String) {
        pendingAssistantText.append(text)
    }

    /** 输入栏手动提交的文本；随本轮终稿一并落盘。 */
    fun onManualSubmit(text: String) {
        if (text.isNotBlank()) pendingUserText = text
    }

    /**
     * 轮结束：正常收尾传 [withAssistant]=true；打断/断线传 false（丢弃半句回答）。
     * 空轮不产生任何事件与写入。
     */
    suspend fun commit(withAssistant: Boolean) {
        val user = pendingUserText
        val assistant = if (withAssistant) pendingAssistantText.toString() else ""
        pendingUserText = null
        pendingAssistantText = StringBuilder()
        val userPart = user?.takeIf(String::isNotBlank)
        val assistantPart = assistant.takeIf(String::isNotBlank)
        if (userPart == null && assistantPart == null) return
        try {
            userPart?.let {
                writer.enqueue(
                    Content(role = Role.USER, parts = listOf(Part(text = it))),
                    isNativeRealtimeTranscript = true,
                )
            }
            assistantPart?.let {
                writer.enqueue(Content(role = Role.MODEL, parts = listOf(Part(text = it))))
            }
            writer.flush()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Chatbot 终稿写入失败，本轮不持久化", failure)
        }
    }

    private companion object {
        const val TAG = "RealtimeChatbotTranscript"
    }
}
