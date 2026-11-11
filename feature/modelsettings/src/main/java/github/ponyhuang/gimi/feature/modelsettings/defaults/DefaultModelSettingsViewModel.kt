package github.ponyhuang.gimi.feature.modelsettings.defaults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.isRecognition
import github.ponyhuang.gimi.domain.modelcatalog.model.recognitionCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.TtsVoiceCatalog
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.feature.modelsettings.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DefaultModelSettingsViewModel @Inject constructor(
    observeSettings: ObserveDefaultModelSettingsUseCase,
    private val updateSettings: UpdateDefaultModelSettingsUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val ttsVoiceRepository: TtsVoiceRepository,
    private val voiceCapabilities: VoiceCapabilityResolver,
) : ViewModel() {
    private val dialog = MutableStateFlow<DefaultModelDialog?>(null)
    private val ttsVoiceOptions = MutableStateFlow<List<TtsVoice>>(emptyList())
    private val isTtsVoiceLoading = MutableStateFlow(false)
    /** 当前选中的 Realtime 默认模型；音色覆盖要绑定到它上面。 */
    private val realtimeSelection = MutableStateFlow<ModelSelection?>(null)

    // 缓冲若干条一次性反馈，避免 Route 尚未开始收集时丢失。
    private val _effects = MutableSharedFlow<DefaultModelSettingsEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            observeSettings()
                .collectLatest { settings ->
                    realtimeSelection.value = settings.realtimeSelection
                    val selection = settings.ttsSelection
                    if (selection == null) {
                        ttsVoiceOptions.value = emptyList()
                        return@collectLatest
                    }
                    isTtsVoiceLoading.value = true
                    try {
                        // 音色与 TTS 模型绑定，因此按完整选择取音色，而不是只按服务。
                        ttsVoiceOptions.value = ttsVoiceRepository.getVoices(selection)
                        // 这里刻意不改写已选音色：音色集并不封闭（基础音色与声音复刻音色都不在
                        // 内置表里），按目录判定「失效」会误伤合法配置，用户填的值也会被静默覆盖。
                        // 是否需要换音色由选择器内的提示交给用户决定。
                    } catch (_: Exception) {
                        // 出错由 Repository / Provider 内部降级，若仍然异常则置空由兜底策略生效
                    } finally {
                        isTtsVoiceLoading.value = false
                    }
                }
        }
    }

    val uiState = combine(
        observeSettings(),
        dialog,
        runWhenAgentIdle.state,
        ttsVoiceOptions,
        isTtsVoiceLoading,
    ) { settings, currentDialog, runtimeState, voices, loading ->
        settings.toUiState(
            dialog = currentDialog,
            isMutationBlocked = runtimeState.isBusy,
            ttsVoiceOptions = voices.ifEmpty { TtsVoiceCatalog.forService(settings.ttsSelection?.serviceId) },
            isTtsVoiceLoading = loading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DefaultModelSettingsUiState(),
    )

    fun onAction(action: DefaultModelSettingsAction) {
        when (action) {
            is DefaultModelSettingsAction.ShowDialog -> dialog.value = action.dialog
            DefaultModelSettingsAction.DismissDialog -> dialog.value = null
            is DefaultModelSettingsAction.SelectVoice -> {
                mutate { updateSettings.voice(action.voiceId) }
            }
            is DefaultModelSettingsAction.SelectModel -> {
                mutate { select(action.target, action.selection) }
            }
            is DefaultModelSettingsAction.SelectRealtimeModel -> {
                mutate { updateSettings.realtimeModel(action.selection) }
            }
            is DefaultModelSettingsAction.SelectRealtimeVoice -> {
                mutate { updateSettings.realtimeVoice(action.voiceId?.let { overrideFor(it) }) }
            }
        }
    }

    /**
     * 直接打开 Realtime 模型选择弹窗。
     *
     * 通话页在 Realtime 模式不可用时引导用户来配置，落地即弹窗，省去用户在长列表里找。
     */
    fun showRealtimeModelDialog() {
        dialog.value = DefaultModelDialog.RealtimeModel
    }

    /**
     * 把音色覆盖绑定到当前的 Realtime 模型。
     *
     * 音色与模型强绑定（同一厂商不同代际的合法音色集互不相同），因此覆盖值必须记住所属模型，
     * 换模型后旧覆盖自动失效并回落到新模型的缺省音色。
     */
    private fun overrideFor(voiceId: String): RealtimeVoiceOverride? {
        val selection = realtimeSelection.value ?: return null
        return RealtimeVoiceOverride(selection = selection, voiceId = voiceId)
    }

    /** 执行一次配置写入；被前台 Agent 阻塞时用 Toast 反馈，成功则关闭当前弹窗。 */
    private fun mutate(block: () -> Unit) {
        viewModelScope.launch {
            when (runWhenAgentIdle { block() }) {
                is AgentMutationResult.Applied -> dialog.value = null
                AgentMutationResult.BlockedByActiveAgent -> _effects.emit(
                    DefaultModelSettingsEffect.ShowToast(
                        R.string.modelsettings_agent_mutation_blocked,
                    ),
                )
            }
        }
    }

    private fun select(target: DefaultModelDialog, selection: ModelSelection) {
        when (target) {
            DefaultModelDialog.Assistant -> updateSettings.assistant(selection)
            DefaultModelDialog.Fast -> updateSettings.fast(selection)
            DefaultModelDialog.Speech -> updateSettings.speech(selection)
            // 音色不改写：换模型后旧音色是否仍然有效由用户在音色选择器里判断。
            DefaultModelDialog.Tts -> updateSettings.tts(selection)
            DefaultModelDialog.TtsVoice -> Unit
            // Realtime 模型与音色有各自的取值语义（含"清除"），走专用动作。
            DefaultModelDialog.RealtimeModel -> Unit
            DefaultModelDialog.RealtimeVoice -> Unit
        }
    }

    /**
     * 按能力筛选候选模型，并标注其服务是否真的接入了该能力。
     *
     * @param requiredCapability 解析该行所需的能力；返回 `null` 表示该能力恒可用（例如聊天）。
     *   语音类模型按**模型自身声明的能力**逐行判定，而不是整批按同一个能力判定：
     *   专用转写（STT）与音频理解（ASR）是两套协议，同一个服务可能只接入其中一套，
     *   整批判定会把本来能用的模型一起误禁掉。
     */
    private fun List<LLMModelSetting>.rows(
        predicate: (Model) -> Boolean,
        requiredCapability: (Model) -> ModelCapability? = { null },
    ): List<SelectableModelRow> = flatMap { service ->
        service.groups.flatMap { group ->
            group.models.filter(predicate).map { model ->
                val capability = requiredCapability(model)
                SelectableModelRow(
                    service = service,
                    group = group,
                    model = model,
                    capability = capability,
                    isSupported = capability == null ||
                        voiceCapabilities.isSupported(service.id, capability),
                )
            }
        }
    }

    private fun DefaultModelSettings.toUiState(
        dialog: DefaultModelDialog?,
        isMutationBlocked: Boolean,
        ttsVoiceOptions: List<TtsVoice>,
        isTtsVoiceLoading: Boolean,
    ): DefaultModelSettingsUiState {
        val configuredServices = services.filter { it.isEnabled && it.apiKey.isNotBlank() }
        // Realtime 候选只列已接入原生全双工适配器的服务：带 Realtime 标记但没有适配器的模型
        // 会静默回落到级联链路（而级联要求 STT/TTS 能力），选它只会得到起不来的会话。
        // 这里整组隐藏而不是像 STT/TTS 那样"显示但不可选"——级联模型不可选仍可让用户看出
        // 厂商缺哪一项能力，而 Realtime 没有第二条链路可言，只会把人引到起不来的会话上。
        val realtimeRows = configuredServices
            .filter { voiceCapabilities.isSupported(it.id, ModelCapability.Realtime) }
            .flatMap { service ->
                service.groups.flatMap { group ->
                    group.models.filter { ModelCapability.Realtime in it.capabilities }.map { model ->
                        SelectableModelRow(
                            service = service,
                            group = group,
                            model = model,
                            capability = ModelCapability.Realtime,
                            isSupported = true,
                        )
                    }
                }
            }
        val realtimeSupport = realtimeSelection?.let { voiceCapabilities.realtimeSupport(it.serviceId) }
        val applicableVoice = realtimeVoice?.takeIf { it.selection == realtimeSelection }
        return DefaultModelSettingsUiState(
            assistantSelection = assistantSelection,
            fastSelection = fastSelection,
            speechSelection = speechSelection,
            ttsSelection = ttsSelection,
            ttsVoiceId = ttsVoiceId,
            ttsVoiceOptions = ttsVoiceOptions,
            isTtsVoiceLoading = isTtsVoiceLoading,
            ttsVoiceCustomInputAllowed = TtsVoiceCatalog.allowsCustomVoice(ttsSelection?.serviceId),
            chatModels = configuredServices.rows(
                predicate = { ModelCapability.LLM in it.capabilities },
            ),
            speechModels = configuredServices.rows(
                predicate = { it.capabilities.any { capability -> capability.isRecognition } },
                requiredCapability = { it.recognitionCapability },
            ),
            ttsModels = configuredServices.rows(
                predicate = { ModelCapability.TTS in it.capabilities },
                requiredCapability = { ModelCapability.TTS },
            ),
            realtimeSelection = realtimeSelection,
            realtimeModels = realtimeRows,
            realtimeVoiceOptions = realtimeSelection
                ?.let { selection -> realtimeSupport?.voicesFor(selection.modelId) }
                .orEmpty(),
            realtimeDefaultVoice = realtimeSelection
                ?.let { selection -> realtimeSupport?.defaultVoiceFor(selection.modelId) }
                .orEmpty(),
            realtimeVoiceId = applicableVoice?.voiceId,
            realtimeCustomVoiceAllowed = realtimeSupport?.customVoiceAllowed == true,
            dialog = dialog,
            isMutationBlocked = isMutationBlocked,
        )
    }
}
