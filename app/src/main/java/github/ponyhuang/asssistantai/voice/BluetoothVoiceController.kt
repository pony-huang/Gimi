package github.ponyhuang.asssistantai.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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
) : VoiceWakeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        BluetoothVoiceUiState(
            keyword = preferences.keyword.value,
            model = modelRepository.state.value,
            voiceSessionId = preferences.voiceSessionId.value,
        ),
    )
    override val state: StateFlow<BluetoothVoiceUiState> = _state.asStateFlow()

    init {
        scope.launch { preferences.keyword.collect { keyword -> _state.update { it.copy(keyword = keyword) } } }
        scope.launch { modelRepository.state.collect { model -> _state.update { it.copy(model = model) } } }
        scope.launch {
            preferences.voiceSessionId.collect { sessionId ->
                _state.update { it.copy(voiceSessionId = sessionId) }
            }
        }
    }

    override fun setKeyword(keyword: String): Result<Unit> =
        runCatching { preferences.setKeyword(keyword) }

    override fun installModel() = modelRepository.install()

    override fun start() {
        if (modelRepository.modelPath() == null) {
            setStatus(BluetoothVoiceStatus.Error, message = "请先下载唤醒模型")
            return
        }
        setStatus(BluetoothVoiceStatus.Starting, message = "正在启动蓝牙语音监听")
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

    internal fun modelPath(): String? = modelRepository.modelPath()

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
