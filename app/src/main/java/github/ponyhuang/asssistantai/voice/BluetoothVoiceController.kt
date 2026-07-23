package github.ponyhuang.asssistantai.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.R
import github.ponyhuang.asssistantai.data.assistant.VoiceSessionIdStore
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelCatalog
import github.ponyhuang.asssistantai.domain.speech.repository.VoiceWakeRepository
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
    private val voiceSessionStore: VoiceSessionIdStore,
) : VoiceWakeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        BluetoothVoiceUiState(
            keyword = preferences.keyword.value,
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
        scope.launch { preferences.keyword.collect { keyword -> _state.update { it.copy(keyword = keyword) } } }
        scope.launch {
            modelRepository.states.collect { states -> _state.update { it.copy(modelStates = states) } }
        }
        scope.launch {
            voiceSessionStore.voiceSessionId.collect { sessionId ->
                _state.update { it.copy(voiceSessionId = sessionId) }
            }
        }
    }

    override fun setKeyword(keyword: String): Result<Unit> =
        runCatching { preferences.setKeyword(keyword) }

    override fun selectModel(modelId: String) {
        runCatching { preferences.setActiveModel(modelId) }.onFailure { return }
        if (_state.value.isRunning) {
            stop()
            start()
        }
    }

    override fun installModel(modelId: String) = modelRepository.install(modelId)

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
            Intent(context, BluetoothVoiceService::class.java).setAction(BluetoothVoiceService.ACTION_START),
        )
    }

    override fun stop() {
        context.startService(
            Intent(context, BluetoothVoiceService::class.java).setAction(BluetoothVoiceService.ACTION_STOP),
        )
    }

    fun pauseOrResume() {
        val action = if (_state.value.status == BluetoothVoiceStatus.Paused) {
            BluetoothVoiceService.ACTION_RESUME
        } else {
            BluetoothVoiceService.ACTION_PAUSE
        }
        context.startService(Intent(context, BluetoothVoiceService::class.java).setAction(action))
    }

    internal fun modelPath(): String? = modelRepository.modelPath(preferences.activeModelId.value)

    internal fun setStatus(
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
}
