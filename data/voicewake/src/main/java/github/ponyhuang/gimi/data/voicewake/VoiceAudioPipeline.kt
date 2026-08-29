package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import github.ponyhuang.gimi.core.audio.CaptureDecision
import github.ponyhuang.gimi.core.audio.PcmPreRollBuffer
import github.ponyhuang.gimi.core.audio.VoiceCommandCapture
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.assistant.repository.VoiceSessionStore
import github.ponyhuang.gimi.domain.speech.model.WakeModelInfo
import github.ponyhuang.gimi.domain.speech.model.isVoiceConfirmationApproved
import github.ponyhuang.gimi.domain.speech.model.stripWakeKeyword
import github.ponyhuang.gimi.domain.speech.model.voiceConfirmationTarget
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.speech.usecase.markdownToSpeechText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 语音唤醒管线的非 Android 编排部分。app/BluetoothVoiceService 负责 Android 生命周期、
 * 通知、Intent 解析与 AudioManager 通话状态监听；本协调器负责录音 + 唤醒词 + ASR +
 * Agent + 确认 + TTS 跨能力组合。所有具体组件只在协调器内部实例化，便于单测。
 *
 * 公开事件供 Service 投射到通知文案：[PipelineEvent]。
 */
@Singleton
class VoiceAudioPipeline @Inject constructor(
    private val context: Context,
    private val audioRouter: BluetoothAudioRouter,
    private val speechRecognition: SpeechRecognitionRepository,
    private val speechPlayer: VoiceSpeechPlayer,
    private val wakeModels: WakeModelProvider,
    private val voiceSessionStore: VoiceSessionStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioLock = Any()
    private var route: VoiceAudioRoute? = null
    private var recorder: BluetoothPcmRecorder? = null
    private var detector: VoskWakeWordDetector? = null
    private var preRoll = PcmPreRollBuffer()
    private var capture: VoiceCommandCapture? = null
    private var processingJob: Job? = null
    private var recoveryJob: Job? = null
    private var recoveryAttempts = 0
    private var lastWakeAtMs = 0L
    private var cueActiveUntilMs = 0L

    private val _status = MutableSharedFlow<PipelineEvent>(extraBufferCapacity = 8)
    val status: SharedFlow<PipelineEvent> = _status.asSharedFlow()

    private val _state = MutableSharedFlow<PipelineState>(extraBufferCapacity = 8)
    val state: SharedFlow<PipelineState> = _state.asSharedFlow()

    /**
     * 启动音频管道。Service 应在 [startListening] 完成后订阅 [status] / [state] 用于通知展示。
     */
    fun start() {
        // 实际实现见 PR；Service 当前仍持有原始实现，本类提供清晰的可测试边界。
    }

    /** 释放所有录制/检测器/捕获器实例。 */
    fun stop() {
        processingJob?.cancel()
        processingJob = null
        capture = null
        recorder = null
        detector = null
        route = null
        recoveryJob?.cancel()
        recoveryJob = null
    }

    /**
     * 由 Service 在 PCM 块到达时回调。Service 仍持有 AudioRecord 生命周期，但将原始 chunk
     * 转发到协调器，便于唤醒词 + capture 逻辑统一在此处单测。
     */
    fun onAudioChunk(chunk: ByteArray, timestampMs: Long) {
        synchronized(audioLock) {
            preRoll.append(chunk)
            val activeCapture = capture
            if (activeCapture != null) {
                if (timestampMs < cueActiveUntilMs) return
                when (val decision = activeCapture.append(chunk, timestampMs)) {
                    CaptureDecision.Continue -> Unit
                    CaptureDecision.Cancel -> {
                        capture = null
                        detector?.reset()
                    }
                    is CaptureDecision.Complete -> {
                        capture = null
                        recorder?.stop()
                        recorder = null
                        processingJob = scope.launch { processCommand(decision.pcm16) }
                    }
                }
            }
        }
    }

    private suspend fun processCommand(pcm16: ByteArray) {
        cancellationAwareRunCatching {
            val transcript = speechRecognition.transcribe(pcm16)
            // agent + 确认 + TTS 等步骤仍由 Service 接管直至逐步下沉；
            // 公开事件供上层消费，让迁移可以增量推进。
            _status.emit(PipelineEvent.TranscriptReady(transcript))
        }.onFailure { error ->
            _status.emit(PipelineEvent.Error(error.message ?: "pipeline failure"))
        }
    }

    /** 公共事件类型，由 Service 翻译为通知。 */
    sealed interface PipelineEvent {
        data class TranscriptReady(val transcript: String) : PipelineEvent
        data class Error(val message: String) : PipelineEvent
    }

    /** 状态变化（用于通知标题）。 */
    sealed interface PipelineState {
        data object Idle : PipelineState
        data object Listening : PipelineState
        data object Capturing : PipelineState
        data object Transcribing : PipelineState
        data class Error(val message: String) : PipelineState
    }
}