package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.data.voicewake.R
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import github.ponyhuang.gimi.domain.speech.repository.VoiceWakeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class BluetoothVoiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: BluetoothVoicePreferences,
    private val modelRepository: WakeModelRepository,
) : VoiceWakeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        BluetoothVoiceUiState(
            availableModels = WakeModelCatalog.models,
            activeModelId = preferences.activeModelId.value,
            wakeWord = preferences.wakeWord.value,
            modelStates = modelRepository.states.value,
        ),
    )
    override val state: StateFlow<BluetoothVoiceUiState> = _state.asStateFlow()

    init {
        scope.launch {
            preferences.activeModelId.collect { modelId ->
                _state.update { it.copy(activeModelId = modelId, wakeWord = preferences.wakeWord.value) }
            }
        }
        scope.launch {
            preferences.wakeWord.collect { wakeWord -> _state.update { it.copy(wakeWord = wakeWord) } }
        }
        scope.launch {
            modelRepository.states.collect { states -> _state.update { it.copy(modelStates = states) } }
        }
    }

    override fun selectModel(modelId: String) {
        runCatching { preferences.setActiveModel(modelId) }.onFailure { return }
        _state.update {
            it.copy(activeModelId = preferences.activeModelId.value, wakeWord = preferences.wakeWord.value)
        }
        if (_state.value.isRunning) {
            stop()
            start()
        }
    }

    override fun installModel(modelId: String) = modelRepository.install(modelId)

    override fun cancelInstall(modelId: String) = modelRepository.cancelInstall(modelId)

    override fun removeModel(modelId: String) = modelRepository.remove(modelId)

    override fun start() {
        if (modelPath() == null) {
            setStatus(
                BluetoothVoiceStatus.Error,
                message = context.getString(R.string.bluetooth_voice_model_required),
            )
            return
        }
        setStatus(
            BluetoothVoiceStatus.Starting,
            message = context.getString(R.string.bluetooth_voice_starting_listener),
        )
        ContextCompat.startForegroundService(
            context,
            Intent(BLUETOOTH_VOICE_ACTION_START).setClassName(context, BLUETOOTH_VOICE_SERVICE_CLASS),
        )
    }

    override fun stop() {
        context.startService(
            Intent(BLUETOOTH_VOICE_ACTION_STOP).setClassName(context, BLUETOOTH_VOICE_SERVICE_CLASS),
        )
    }

    override fun setWakeWord(modelId: String, wakeWord: String): Result<Unit> = runCatching {
        val restartActiveListener = _state.value.isRunning && _state.value.activeModelId == modelId
        val previousWakeWord = preferences.wakeWord(modelId)
        preferences.setWakeWord(modelId, wakeWord)
        try {
            if (_state.value.activeModelId == modelId) {
                _state.update { it.copy(wakeWord = preferences.wakeWord.value) }
            }
            if (restartActiveListener) {
                stop()
                start()
            }
        } catch (error: RuntimeException) {
            // 重启请求同步失败时回滚持久化值，保证旧检测器/下次启动仍使用旧唤醒词。
            runCatching { preferences.setWakeWord(modelId, previousWakeWord) }
            if (_state.value.activeModelId == modelId) {
                _state.update { it.copy(wakeWord = previousWakeWord) }
            }
            if (restartActiveListener) runCatching { start() }
            throw error
        }
    }

    fun pauseOrResume() {
        val action = if (_state.value.status == BluetoothVoiceStatus.Paused) {
            BLUETOOTH_VOICE_ACTION_RESUME
        } else {
            BLUETOOTH_VOICE_ACTION_PAUSE
        }
        context.startService(Intent(action).setClassName(context, BLUETOOTH_VOICE_SERVICE_CLASS))
    }

    fun modelPath(): String? = modelRepository.modelPath(preferences.activeModelId.value)

    fun setStatus(
        status: BluetoothVoiceStatus,
        deviceName: String? = _state.value.deviceName,
        lastCommand: String? = _state.value.lastCommand,
        message: String? = null,
    ) {
        _state.update {
            it.copy(
                status = status,
                deviceName = deviceName,
                lastCommand = lastCommand,
                message = message,
            )
        }
    }

    companion object {
        const val BLUETOOTH_VOICE_ACTION_START = "github.ponyhuang.gimi.voice.START"
        const val BLUETOOTH_VOICE_ACTION_STOP = "github.ponyhuang.gimi.voice.STOP"
        const val BLUETOOTH_VOICE_ACTION_PAUSE = "github.ponyhuang.gimi.voice.PAUSE"
        const val BLUETOOTH_VOICE_ACTION_RESUME = "github.ponyhuang.gimi.voice.RESUME"
        private const val BLUETOOTH_VOICE_SERVICE_CLASS = "github.ponyhuang.gimi.voice.BluetoothVoiceService"
    }
}
