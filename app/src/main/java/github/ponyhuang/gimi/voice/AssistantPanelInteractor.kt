package github.ponyhuang.gimi.voice

import github.ponyhuang.gimi.core.audio.VoiceAudioRecorder
import github.ponyhuang.gimi.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantPresentationEvent
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 助手面板的前台交互协调器：归属 [AssistantSessionCoordinator]，负责前台麦克风采集、
 * 转写并提交追问与键盘文本提交。录音期间展示层仍由共享状态驱动。
 */
@Singleton
class AssistantPanelInteractor @Inject constructor(
    private val coordinator: AssistantSessionCoordinator,
    private val speechRecognition: SpeechRecognitionRepository,
) {
    private val recorder = VoiceAudioRecorder()
    private val pcm = ByteArrayOutputStream()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    /** 检查执行所需配置；返回 null 表示可执行。 */
    suspend fun configurationIssue(): AssistantConfigIssue? = coordinator.configurationIssue()

    /** 切换麦克风：空闲时开始采集，录音中则停止并转写提交。 */
    fun toggleMic() {
        if (_recording.value) stopAndSubmit() else startRecording()
    }

    fun cancelRecording() {
        recorder.stop()
        _recording.value = false
        _audioLevel.value = 0f
    }

    fun submitText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            runCatching {
                coordinator.submit(trimmed, AssistantInvocationSource.ASSISTANT_PANEL, null)
            }.onFailure {
                coordinator.updatePresentation(
                    AssistantPresentationEvent.Failed(it.message ?: "提交失败"),
                )
            }
        }
    }

    fun release() {
        cancelRecording()
        recorder.release()
        scope.cancel()
    }

    private fun startRecording() {
        if (_recording.value) return
        pcm.reset()
        _audioLevel.value = 0f
        val started = recorder.start(
            onAudioChunk = { chunk -> pcm.write(chunk) },
            onAudioLevel = { _audioLevel.value = it },
            onError = { error ->
                cancelRecording()
                coordinator.updatePresentation(
                    AssistantPresentationEvent.Failed(error.message ?: "录音失败"),
                )
            },
        )
        _recording.value = started
    }

    private fun stopAndSubmit() {
        recorder.stop()
        _recording.value = false
        _audioLevel.value = 0f
        val bytes = pcm.toByteArray()
        pcm.reset()
        if (bytes.isEmpty()) {
            coordinator.updatePresentation(AssistantPresentationEvent.Failed("未收到音频，请重试"))
            return
        }
        scope.launch {
            coordinator.updatePresentation(AssistantPresentationEvent.Transcribing)
            val text = try {
                speechRecognition.transcribe(bytes)
            } catch (error: Throwable) {
                coordinator.updatePresentation(
                    AssistantPresentationEvent.Failed(error.message ?: "识别失败"),
                )
                return@launch
            }
            if (text.isBlank()) {
                coordinator.updatePresentation(AssistantPresentationEvent.Failed("识别无结果"))
                return@launch
            }
            runCatching {
                coordinator.submit(text, AssistantInvocationSource.ASSISTANT_PANEL, null)
            }.onFailure {
                coordinator.updatePresentation(
                    AssistantPresentationEvent.Failed(it.message ?: "提交失败"),
                )
            }
        }
    }
}
