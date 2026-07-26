package github.ponyhuang.asssistantai.feature.assistant

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.core.audio.CaptureDecision
import github.ponyhuang.asssistantai.core.audio.VoiceAudioRecorder
import github.ponyhuang.asssistantai.core.audio.VoiceCommandCapture
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.asssistantai.domain.speech.model.SpeechPlaybackStatus
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechSynthesisRepository
import github.ponyhuang.asssistantai.domain.speech.usecase.markdownToSpeechText
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 空闲关闭与确认倒计时的计时参数。 */
internal const val IDLE_CLOSE_TIMEOUT_MS = 30_000L
private const val MAX_WAVEFORM_LEVELS = 96

/**
 * 助理浮层 ViewModel：录音/转写在界面层驱动，任务执行完全交给共享协调器。
 * 浮层销毁（ViewModel 清除）不会取消协调器中已提交的任务。
 */
@HiltViewModel
class AssistantOverlayViewModel @Inject constructor(
    private val coordinator: AssistantSessionCoordinator,
    private val speechRecognition: SpeechRecognitionRepository,
    private val speechPlayback: SpeechPlaybackRepository,
    private val speechSynthesis: SpeechSynthesisRepository,
) : ViewModel() {

    private data class LocalState(
        val listening: Boolean = false,
        val transcribing: Boolean = false,
        val speaking: Boolean = false,
        val recordingLevels: List<Float> = emptyList(),
        val draftText: String = "",
        val configIssue: AssistantConfigIssue? = null,
        val canRetryListening: Boolean = false,
        val transcribeFailed: Boolean = false,
        val ttsNotice: Boolean = false,
        val micPermissionDenied: Boolean = false,
        val preparationFailed: Boolean = false,
        val confirmationRemainingSeconds: Int = 0,
    )

    private val recorder = VoiceAudioRecorder()
    private var capture: VoiceCommandCapture? = null
    private val local = MutableStateFlow(LocalState())
    private val _events = MutableSharedFlow<AssistantOverlayEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AssistantOverlayEvent> = _events.asSharedFlow()

    private var idleCloseJob: Job? = null
    private var countdownJob: Job? = null
    private var invoked = false
    private var source: AssistantInvocationSource = AssistantInvocationSource.TILE
    private var lastSpokenResponse = ""
    /** AudioRecord initialization must never block the main/UI dispatcher. */
    internal var recordingDispatcher: CoroutineDispatcher = Dispatchers.IO
    private var recorderStartJob: Job? = null

    val uiState: StateFlow<AssistantOverlayUiState> =
        combine(coordinator.state, local, speechPlayback.state) { cs, l, playback ->
            val phase = when {
                l.listening -> AssistantSessionPhase.LISTENING
                l.transcribing -> AssistantSessionPhase.TRANSCRIBING
                l.configIssue != null -> AssistantSessionPhase.MISSING_CONFIG
                l.transcribeFailed -> AssistantSessionPhase.ERROR
                cs.taskActive -> cs.phase
                l.preparationFailed || l.canRetryListening ||
                    l.micPermissionDenied -> AssistantSessionPhase.FOLLOW_UP_IDLE
                cs.phase != AssistantSessionPhase.FOLLOW_UP_IDLE -> cs.phase
                l.speaking && playback.status != SpeechPlaybackStatus.Idle ->
                    AssistantSessionPhase.SPEAKING
                else -> AssistantSessionPhase.FOLLOW_UP_IDLE
            }
            AssistantOverlayUiState(
                phase = phase,
                source = cs.source ?: source,
                userText = cs.turn?.userText.orEmpty(),
                responseText = cs.turn?.responseText.orEmpty(),
                toolNames = cs.turn?.toolNames.orEmpty(),
                pendingConfirmation = cs.pendingConfirmation,
                confirmationRemainingSeconds = l.confirmationRemainingSeconds,
                configIssue = l.configIssue ?: cs.configIssue,
                errorMessage = cs.errorMessage,
                recordingLevels = if (l.listening) l.recordingLevels else emptyList(),
                draftText = l.draftText,
                isSpeaking = l.speaking && playback.status != SpeechPlaybackStatus.Idle,
                ttsNotice = l.ttsNotice,
                canRetryListening = l.canRetryListening || l.transcribeFailed ||
                    l.micPermissionDenied,
                preparationFailed = l.preparationFailed,
                voiceSessionId = cs.sessionId,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AssistantOverlayUiState(),
        )

    init {
        // 任务结束（取消/完成）后自动播报；TTS 不可用时仅给非阻塞提示。
        viewModelScope.launch {
            var wasActive = coordinator.state.value.taskActive
            coordinator.state.collect { cs ->
                if (wasActive && !cs.taskActive &&
                    cs.phase == AssistantSessionPhase.FOLLOW_UP_IDLE
                ) {
                    onTaskSettled(cs.turn?.responseText.orEmpty())
                }
                wasActive = cs.taskActive
            }
        }
        // 播报自然结束 → 回到可追问并开始空闲计时。
        viewModelScope.launch {
            speechPlayback.state.collect { playback ->
                if (playback.status == SpeechPlaybackStatus.Idle && local.value.speaking) {
                    local.update { it.copy(speaking = false) }
                    scheduleIdleClose()
                }
            }
        }
        viewModelScope.launch {
            speechPlayback.errors.collect {
                local.update { it.copy(speaking = false, ttsNotice = true) }
                scheduleIdleClose()
            }
        }
        // 敏感操作确认倒计时。
        viewModelScope.launch {
            coordinator.state.collect { cs ->
                val pending = cs.pendingConfirmation
                if (pending != null && countdownJob?.isActive != true) {
                    countdownJob = viewModelScope.launch {
                        while (true) {
                            val remaining =
                                (pending.deadlineEpochMs - System.currentTimeMillis()) / 1000
                            local.update {
                                it.copy(
                                    confirmationRemainingSeconds =
                                        remaining.coerceAtLeast(0).toInt(),
                                )
                            }
                            // 截止后协调器会自动拒绝并清除待确认，循环随之结束。
                            if (remaining <= 0) break
                            delay(200)
                        }
                    }
                } else if (pending == null) {
                    countdownJob?.cancel()
                    countdownJob = null
                    local.update { it.copy(confirmationRemainingSeconds = 0) }
                }
            }
        }
    }

    /** 浮层被唤起（含再次唤起）；有活动任务时只恢复状态，不重新录音。 */
    fun onInvoked(source: AssistantInvocationSource, microphoneGranted: Boolean) {
        this.source = source
        coordinator.noteInvocation(source)
        if (invoked) return
        invoked = true
        if (coordinator.state.value.taskActive) return
        if (!microphoneGranted) {
            local.update { it.copy(micPermissionDenied = true) }
            _events.tryEmit(AssistantOverlayEvent.RequestMicrophonePermission)
            return
        }
        begin()
    }

    fun onAction(action: AssistantOverlayAction) {
        when (action) {
            AssistantOverlayAction.MicTapped -> {
                if (uiState.value.inputEnabled) startListening()
            }
            AssistantOverlayAction.StopListening -> stopListening(retryable = true)
            is AssistantOverlayAction.DraftChanged -> {
                local.update { it.copy(draftText = action.value) }
                restartIdleClose()
            }
            AssistantOverlayAction.SubmitDraft -> submitDraft()
            AssistantOverlayAction.StopTask -> stopTask()
            AssistantOverlayAction.CloseOverlay -> close()
            is AssistantOverlayAction.ApproveConfirmation -> {
                coordinator.respondToConfirmation(action.confirmationCallId, true)
                restartIdleClose()
            }
            is AssistantOverlayAction.RejectConfirmation ->
                coordinator.respondToConfirmation(action.confirmationCallId, false)
            AssistantOverlayAction.StopSpeaking -> {
                speechPlayback.stop()
                local.update { it.copy(speaking = false) }
                scheduleIdleClose()
            }
            AssistantOverlayAction.RetryAfterError -> {
                if (local.value.configIssue != null) begin() else startListening()
            }
            AssistantOverlayAction.UserActivity -> restartIdleClose()
            is AssistantOverlayAction.MicPermissionResult -> {
                if (action.granted) {
                    local.update { it.copy(micPermissionDenied = false) }
                    begin()
                } else {
                    local.update { it.copy(micPermissionDenied = true) }
                }
            }
        }
    }

    private fun begin() {
        viewModelScope.launch {
            local.update { it.copy(preparationFailed = false, canRetryListening = false) }
            val issue = runCatching { coordinator.configurationIssue() }
                .getOrElse {
                    local.update {
                        it.copy(
                            preparationFailed = true,
                            canRetryListening = true,
                        )
                    }
                    scheduleIdleClose()
                    return@launch
                }
            if (issue != null) {
                local.update { it.copy(configIssue = issue) }
                return@launch
            }
            startListening()
        }
    }

    private fun startListening() {
        if (coordinator.state.value.taskActive) return
        speechPlayback.stop()
        cancelIdleClose()
        local.update {
            it.copy(
                listening = true,
                transcribing = false,
                speaking = false,
                recordingLevels = emptyList(),
                canRetryListening = false,
                transcribeFailed = false,
                ttsNotice = false,
                configIssue = null,
                preparationFailed = false,
            )
        }
        val commandCapture = VoiceCommandCapture(ByteArray(0), SystemClock.elapsedRealtime())
        capture = commandCapture
        recorderStartJob?.cancel()
        recorderStartJob = viewModelScope.launch(recordingDispatcher) {
            val started = recorder.start(
                onAudioChunk = { chunk ->
                    when (val decision = commandCapture.append(chunk, SystemClock.elapsedRealtime())) {
                        CaptureDecision.Continue -> Unit
                        CaptureDecision.Cancel ->
                            viewModelScope.launch { stopListening(retryable = true) }
                        is CaptureDecision.Complete -> viewModelScope.launch {
                            transcribeAndSubmit(decision.pcm16)
                        }
                    }
                },
                onAudioLevel = { level ->
                    local.update {
                        it.copy(
                            recordingLevels =
                                (it.recordingLevels + level).takeLast(MAX_WAVEFORM_LEVELS),
                        )
                    }
                },
                onError = {
                    viewModelScope.launch {
                        stopListening(retryable = true, preparationFailed = true)
                    }
                },
            )
            if (!started) {
                stopListening(retryable = true, preparationFailed = true)
            }
        }
    }

    private fun stopListening(
        retryable: Boolean,
        preparationFailed: Boolean = false,
    ) {
        capture = null
        recorder.stop()
        local.update {
            it.copy(
                listening = false,
                canRetryListening = retryable,
                preparationFailed = preparationFailed,
            )
        }
        if (retryable && !coordinator.state.value.taskActive) scheduleIdleClose()
    }

    private fun transcribeAndSubmit(pcm16: ByteArray) {
        capture = null
        recorder.stop()
        local.update { it.copy(listening = false, transcribing = true) }
        viewModelScope.launch {
            val transcript = runCatching { speechRecognition.transcribe(pcm16) }.getOrNull()
            if (transcript.isNullOrBlank()) {
                local.update { it.copy(transcribing = false, transcribeFailed = true) }
                scheduleIdleClose()
                return@launch
            }
            local.update { it.copy(transcribing = false) }
            submitToAgent(transcript)
        }
    }

    private fun submitDraft() {
        val text = local.value.draftText.trim()
        if (text.isEmpty() || !uiState.value.inputEnabled) return
        speechPlayback.stop()
        cancelIdleClose()
        local.update { it.copy(draftText = "", ttsNotice = false) }
        submitToAgent(text)
    }

    private fun submitToAgent(text: String) {
        viewModelScope.launch { coordinator.submit(text, source) }
    }

    private fun stopTask() {
        capture = null
        recorder.stop()
        speechPlayback.stop()
        coordinator.stop()
        local.update {
            it.copy(listening = false, transcribing = false, speaking = false)
        }
    }

    private fun onTaskSettled(responseText: String) {
        if (responseText.isBlank() || responseText == lastSpokenResponse) {
            scheduleIdleClose()
            return
        }
        lastSpokenResponse = responseText
        if (speechSynthesis.isAvailable()) {
            local.update { it.copy(speaking = true, ttsNotice = false) }
            speechPlayback.play(
                "assistant-${responseText.hashCode()}",
                markdownToSpeechText(responseText),
            )
        } else {
            local.update { it.copy(ttsNotice = true) }
            scheduleIdleClose()
        }
    }

    private fun scheduleIdleClose() {
        if (coordinator.state.value.taskActive) return
        idleCloseJob?.cancel()
        idleCloseJob = viewModelScope.launch {
            delay(IDLE_CLOSE_TIMEOUT_MS)
            close()
        }
    }

    private fun restartIdleClose() {
        if (uiState.value.phase == AssistantSessionPhase.FOLLOW_UP_IDLE) scheduleIdleClose()
    }

    private fun cancelIdleClose() {
        idleCloseJob?.cancel()
        idleCloseJob = null
    }

    private fun close() {
        cancelIdleClose()
        recorderStartJob?.cancel()
        recorderStartJob = null
        capture = null
        recorder.stop()
        speechPlayback.stop()
        coordinator.hideOverlay()
        _events.tryEmit(AssistantOverlayEvent.CloseOverlay)
    }

    override fun onCleared() {
        recorder.release()
        super.onCleared()
    }
}
