package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.data.voicewake.R
import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.repository.AssistantConfirmationHandler
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音 Agent 一轮执行结果。
 *
 * @property sessionId 共享 Assistant 会话标识。
 * @property responseText 用于语音播报和通知预览的最终文本。
 */
data class VoiceAgentResult(
    val sessionId: String,
    val responseText: String,
)

/**
 * 语音工具调用的确认请求。
 *
 * @property callId 待确认工具调用标识。
 * @property toolName 面向用户展示的工具名称。
 * @property arguments 用于生成口头确认目标的工具参数。
 */
data class VoiceToolConfirmation(
    val callId: String,
    val toolName: String,
    val arguments: Map<String, Any?>,
)

/**
 * 蓝牙语音任务的执行入口：委托共享 [AssistantSessionCoordinator] 提交到同一语音会话，
 * 保留蓝牙侧的语音确认回调与结果形态。
 */
@Singleton
class VoiceAgentTaskExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: AssistantSessionCoordinator,
) {
    suspend fun execute(
        command: String,
        confirmTool: suspend (VoiceToolConfirmation) -> Boolean,
    ): VoiceAgentResult {
        val handler = AssistantConfirmationHandler { request ->
            confirmTool(
                VoiceToolConfirmation(
                    callId = request.confirmationCallId,
                    toolName = request.toolName,
                    arguments = request.arguments,
                ),
            )
        }
        coordinator.submit(command, AssistantInvocationSource.BLUETOOTH_WAKE, handler)
        val state = coordinator.state.value
        when (state.phase) {
            AssistantSessionPhase.ERROR ->
                error(state.errorMessage ?: context.getString(R.string.bluetooth_voice_status_task_failed))
            AssistantSessionPhase.MISSING_CONFIG ->
                error(context.getString(R.string.bluetooth_voice_no_agent_model))
            else -> Unit
        }
        return VoiceAgentResult(
            sessionId = state.sessionId.orEmpty(),
            responseText = state.turn?.responseText?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.bluetooth_voice_task_completed),
        )
    }

    /** 取消共享语音会话的当前任务（服务暂停/停止时调用）。 */
    fun stop() {
        coordinator.stop()
    }
}
