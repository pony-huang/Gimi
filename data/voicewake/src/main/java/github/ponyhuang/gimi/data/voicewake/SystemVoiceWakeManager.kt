package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.domain.speech.model.WakePhraseMatch
import github.ponyhuang.gimi.domain.speech.model.WakePhraseMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 系统语音识别器向唤醒流程上报的事件。 */
sealed interface VoiceWakeRecognitionEvent {
    /** 识别器已开始接收语音。 */
    data object Ready : VoiceWakeRecognitionEvent

    /**
     * 一次识别得到的文本。
     *
     * @property text 系统识别出的候选文本。
     * @property isFinal 是否为本轮最终结果；唤醒逻辑不会消费中间结果。
     */
    data class Transcript(
        val text: String,
        val isFinal: Boolean,
    ) : VoiceWakeRecognitionEvent

    /**
     * 本轮识别已经终止。
     *
     * @property retryable 是否允许在运行条件仍满足时重新开始识别。
     */
    data class Error(val retryable: Boolean = true) : VoiceWakeRecognitionEvent
}

/** 隔离 Android SpeechRecognizer 的窄接口，便于验证生命周期与迟到回调。 */
interface VoiceWakeRecognizer {
    val isAvailable: Boolean

    fun start(onEvent: (VoiceWakeRecognitionEvent) -> Unit)

    fun stop()
}

/**
 * 将应用可见性、权限和系统能力组合成一个短生命周期语音唤醒会话。
 *
 * 它只消费最终识别文本；命中唤醒短语后先结束本轮识别，再提交剥离短语后的命令。
 */
internal class SystemVoiceWakeManager(
    private val scope: CoroutineScope,
    private val recognizer: VoiceWakeRecognizer,
    initialTriggerWords: List<String>,
    private val hasRecordAudioPermission: () -> Boolean,
    private val restartDelayMs: Long = 350L,
    private val onCommand: suspend (WakePhraseMatch) -> Unit,
) {
    private val mutableIsListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = mutableIsListening.asStateFlow()

    private var enabled = false
    private var foreground = false
    private var triggerWords = initialTriggerWords
    private var sessionGeneration = 0L
    private var sessionActive = false
    private var restartJob: Job? = null

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        reconcile()
    }

    fun setForeground(foreground: Boolean) {
        this.foreground = foreground
        reconcile()
    }

    fun updateTriggerWords(triggerWords: List<String>) {
        this.triggerWords = triggerWords
    }

    fun refreshPermission() {
        reconcile()
    }

    fun shutdown() {
        enabled = false
        foreground = false
        stopSession()
    }

    private fun reconcile() {
        if (shouldRun()) {
            startSessionIfNeeded()
        } else {
            stopSession()
        }
    }

    private fun shouldRun(): Boolean =
        enabled && foreground && recognizer.isAvailable && hasRecordAudioPermission()

    private fun startSessionIfNeeded() {
        if (sessionActive || restartJob?.isActive == true) return

        sessionActive = true
        val generation = ++sessionGeneration
        recognizer.start { event ->
            if (!sessionActive || generation != sessionGeneration) return@start
            handleEvent(event)
        }
    }

    private fun handleEvent(event: VoiceWakeRecognitionEvent) {
        when (event) {
            VoiceWakeRecognitionEvent.Ready -> mutableIsListening.value = true
            is VoiceWakeRecognitionEvent.Transcript -> {
                if (!event.isFinal) return
                val match = WakePhraseMatcher.match(event.text, triggerWords)
                finishSession()
                if (match == null) {
                    scheduleRestart()
                } else {
                    scope.launch {
                        try {
                            onCommand(match)
                        } finally {
                            scheduleRestart()
                        }
                    }
                }
            }
            is VoiceWakeRecognitionEvent.Error -> {
                finishSession()
                if (event.retryable) scheduleRestart()
            }
        }
    }

    private fun scheduleRestart() {
        if (!shouldRun() || restartJob?.isActive == true) return
        restartJob = scope.launch {
            if (restartDelayMs > 0) delay(restartDelayMs)
            restartJob = null
            if (shouldRun()) startSessionIfNeeded()
        }
    }

    private fun finishSession() {
        if (!sessionActive) return
        sessionActive = false
        sessionGeneration += 1
        mutableIsListening.value = false
        recognizer.stop()
    }

    private fun stopSession() {
        restartJob?.cancel()
        restartJob = null
        finishSession()
    }
}
