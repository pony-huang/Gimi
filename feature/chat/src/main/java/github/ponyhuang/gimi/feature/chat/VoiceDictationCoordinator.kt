package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** 录音结束后的处理意图：保留可编辑转写，或通过普通消息入口立即发送。 */
enum class VoiceInputMode { TRANSCRIBE, SEND }

/**
 * 成功识别后的草稿快照及提交意图；识别失败、空结果和取消均不产生此结果。
 * @property message 已合并识别文字的草稿，保留原有附件。
 * @property sendImmediately 是否通过现有消息发送入口提交该快照。
 */
data class VoiceDictationResult(val message: MessageData, val sendImmediately: Boolean)

/** 空识别结果单独映射为本地化提示，禁止借此发送原草稿。 */
internal class EmptyVoiceTranscriptException : IllegalStateException()

/** 输入栏提交转写请求，由 ViewModel 管理任务与等待状态，完成后交还草稿快照。 */
typealias VoiceTranscriptionRequest = (ByteArray, VoiceInputMode, MessageData, (Result<VoiceDictationResult>) -> Unit) -> Unit

/** ViewModel 拥有的单次听写工作流；取消或被替换的任务不能回写草稿或触发发送。 */
internal class VoiceDictationCoordinator(
    private val scope: CoroutineScope,
    private val transcribe: suspend (ByteArray) -> String,
    private val onModeChange: (VoiceInputMode?) -> Unit,
) {
    private var operation: Any? = null
    private var job: Job? = null

    fun start(
        pcm16: ByteArray,
        mode: VoiceInputMode,
        draft: MessageData,
        onResult: (Result<VoiceDictationResult>) -> Unit,
    ) {
        if (operation != null) return
        val token = Any()
        operation = token
        onModeChange(mode)
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = cancellationAwareRunCatching {
                    val transcript = transcribe(pcm16).trim()
                    currentCoroutineContext().ensureActive()
                    if (transcript.isEmpty()) throw EmptyVoiceTranscriptException()
                    VoiceDictationResult(
                        message = draft.copy(text = appendTranscript(draft.text, transcript)),
                        sendImmediately = mode == VoiceInputMode.SEND,
                    )
                }
                if (operation !== token) return@launch
                operation = null
                job = null
                onModeChange(null)
                onResult(result)
            } finally {
                if (operation === token) {
                    operation = null
                    job = null
                    onModeChange(null)
                }
            }
        }
        job?.start()
    }

    fun cancel() {
        // 先失效令牌，再取消协程；不配合取消的网关结果也不能落入新会话。
        operation = null
        val cancelled = job
        job = null
        cancelled?.cancel()
        onModeChange(null)
    }
}
