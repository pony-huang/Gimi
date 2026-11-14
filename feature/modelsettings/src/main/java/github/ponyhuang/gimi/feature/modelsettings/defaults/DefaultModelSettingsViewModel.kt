package github.ponyhuang.gimi.feature.modelsettings.defaults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.isRecognition
import github.ponyhuang.gimi.domain.modelcatalog.model.recognitionCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DefaultModelSettingsViewModel @Inject constructor(
    observeSettings: ObserveDefaultModelSettingsUseCase,
    private val updateSettings: UpdateDefaultModelSettingsUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val ttsVoiceRepository: TtsVoiceRepository,
    private val voiceCapabilities: VoiceCapabilityResolver,
    private val voiceCatalog: VoiceCatalogResolver,
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
                        // 音色与 TTS 模型绑定，因此按完整选择取音色，而不是只按模板。
                        val voices = ttsVoiceRepository.getVoices(selection)
                        ttsVoiceOptions.value = voices
                        // 未配置（空值）音色自动落到该模型的第一个音色：音色列表按模型加载，
                        // 换合成模型后由这里把默认音色落成显式选择。只写空值，不改写用户已
                        // 配置的音色——音色集并不封闭（基础音色与声音复刻音色都不在表里），
                        // 按目录判定「失效」会误伤合法配置。写入会再次触发本收集器，
                        // 届时已非空，不会成环。
                        if (settings.ttsVoiceId.isBlank()) {
                            voices.firstOrNull()?.let { updateSettings.voice(it.id) }
                        }
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
        val ttsTemplateId = settings.instances.resolveConnection(settings.ttsSelection)?.templateId
        settings.toUiState(
            dialog = currentDialog,
            isMutationBlocked = runtimeState.isBusy,
            ttsVoiceOptions = voices.ifEmpty { voiceCatalog.builtInVoices(ttsTemplateId) },
            isTtsVoiceLoading = loading,
            ttsTemplateId = ttsTemplateId,
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
     * 直接打开某个用途的选择弹窗。
     *
     * 通话页在 Realtime 模式不可用时引导用户来配置；用途选择器补配服务返回时也据此
     * 重新打开对应弹窗，落地即弹窗，省去用户在长列表里找。
     */
    fun showDialog(dialog: DefaultModelDialog) {
        this.dialog.value = dialog
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
                        github.ponyhuang.gimi.feature.modelsettings.R.string.modelsettings_agent_mutation_blocked,
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
            // 换合成模型：存储层清空旧音色（跨厂商音色必然无效），随后音色加载收集器
            // 把新模型的第一个音色落成显式选择；已配置音色不被改写。
            DefaultModelDialog.Tts -> updateSettings.tts(selection)
            DefaultModelDialog.TtsVoice -> Unit
            // Realtime 模型与音色有各自的取值语义（含"清除"），走专用动作。
            DefaultModelDialog.RealtimeModel -> Unit
            DefaultModelDialog.RealtimeVoice -> Unit
        }
    }

    /**
     * 按能力筛选候选模型，并标注其模板是否真的接入了该能力。
     *
     * @param requiredCapability 解析该行所需的能力；返回 `null` 表示该能力恒可用（例如聊天）。
     *   语音类模型按**模型自身声明的能力**逐行判定，而不是整批按同一个能力判定：
     *   专用转写（STT）与音频理解（ASR）是两套协议，同一个模板可能只接入其中一套，
     *   整批判定会把本来能用的模型一起误禁掉。
     */
    private fun List<ModelServiceInstance>.rows(
        predicate: (Model) -> Boolean,
        requiredCapability: (Model) -> ModelCapability? = { null },
    ): List<SelectableModelRow> = flatMap { instance ->
        instance.connections.flatMap { connection ->
            val templateId = instance.templateId
            connection.models.filter(predicate).map { model ->
                val capability = requiredCapability(model)
                SelectableModelRow(
                    instance = instance,
                    connection = connection,
                    model = model,
                    capability = capability,
                    isSupported = capability == null ||
                        (templateId != null && voiceCapabilities.isSupported(templateId, capability)),
                )
            }
        }
    }

    private fun DefaultModelSettings.toUiState(
        dialog: DefaultModelDialog?,
        isMutationBlocked: Boolean,
        ttsVoiceOptions: List<TtsVoice>,
        isTtsVoiceLoading: Boolean,
        ttsTemplateId: String?,
    ): DefaultModelSettingsUiState {
        val configuredInstances = instances.filter { it.isEnabled }
        // Realtime 候选只列已接入原生全双工适配器的模板：带 Realtime 标记但没有适配器的模型
        // 会静默回落到级联链路（而级联要求 STT/TTS 能力），选它只会得到起不来的会话。
        // 这里整组隐藏而不是像 STT/TTS 那样"显示但不可选"——级联模型不可选仍可让用户看出
        // 厂商缺哪一项能力，而 Realtime 没有第二条链路可言，只会把人引到起不来的会话上。
        val realtimeRows = configuredInstances
            .filter { instance ->
                val templateId = instance.templateId
                templateId != null && voiceCapabilities.isSupported(templateId, ModelCapability.Realtime)
            }
            .flatMap { instance ->
                instance.connections.flatMap { connection ->
                    connection.models.filter { ModelCapability.Realtime in it.capabilities }.map { model ->
                        SelectableModelRow(
                            instance = instance,
                            connection = connection,
                            model = model,
                            capability = ModelCapability.Realtime,
                            isSupported = true,
                        )
                    }
                }
            }
        val realtimeTemplateId = instances.resolveConnection(realtimeSelection)?.templateId
        val realtimeSupport = realtimeTemplateId?.let { voiceCapabilities.realtimeSupport(it) }
        val applicableVoice = realtimeVoice?.takeIf { it.selection == realtimeSelection }
        return DefaultModelSettingsUiState(
            assistantSelection = assistantSelection,
            fastSelection = fastSelection,
            speechSelection = speechSelection,
            ttsSelection = ttsSelection,
            ttsVoiceId = ttsVoiceId,
            ttsVoiceOptions = ttsVoiceOptions,
            isTtsVoiceLoading = isTtsVoiceLoading,
            ttsVoiceCustomInputAllowed = voiceCatalog.allowsCustomVoice(ttsTemplateId),
            ttsVoiceIdTemplate = voiceCatalog.customVoiceIdTemplate(ttsTemplateId),
            ttsVoiceDocsUrl = voiceCatalog.customVoiceListUrl(ttsTemplateId),
            chatModels = configuredInstances.rows(
                predicate = { ModelCapability.LLM in it.capabilities },
            ),
            speechModels = configuredInstances.rows(
                predicate = { it.capabilities.any { capability -> capability.isRecognition } },
                requiredCapability = { it.recognitionCapability },
            ),
            ttsModels = configuredInstances.rows(
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
