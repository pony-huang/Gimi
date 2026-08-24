package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.data.voicewake.R
import github.ponyhuang.gimi.domain.assistant.repository.VoiceSessionStore
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
    private val voiceSessionStore: VoiceSessionStore,
) : VoiceWakeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        BluetoothVoiceUiState(
            availableModels = WakeModelCatalog.models,
            activeModelId = preferences.activeModelId.value,
            modelStates = modelRepository.states.value,
            voiceSessionId = voiceSessionStore.voiceSessionId.value,
        ),
    )
    override val state: StateFlow<BluetoothVoiceUiState> = _state.asStateFlow()

    init {
        scope.launch {
            preferences.activeModelId.collect { modelId -> _state.update { it.copy(activeModelId = modelId) } }
        }
        scope.launch {
            modelRepository.states.collect { states -> _state.update { it.copy(modelStates = states) } }
        }
        scope.launch {
            voiceSessionStore.voiceSessionId.collect { sessionId ->
                _state.update { it.copy(voiceSessionId = sessionId) }
            }
        }
    }

    override fun selectModel(modelId: String) {
        runCatching { preferences.setActiveModel(modelId) }.onFailure { return }
        if (_state.value.isRunning) {
            stop()
            start()
        }
    }

    override fun installModel(modelId: String) = modelRepository.install(modelId)

    override fun cancelInstall(modelId: String) = modelRepository.cancelInstall(modelId)

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
        const val BLUETOOTH_VOICE_EXTRA_SESSION_ID = "voice_session_id"
        private const val BLUETOOTH_VOICE_SERVICE_CLASS = "github.ponyhuang.gimi.voice.BluetoothVoiceService"
    }
}
