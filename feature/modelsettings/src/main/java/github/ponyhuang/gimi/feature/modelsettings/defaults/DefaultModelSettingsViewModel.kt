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
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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

    // 一次性反馈以状态承载：晚于发送开始的收集也不会丢失，呈现后经 ConsumeNotice 清除。
    private val mutableNotice = MutableStateFlow<DefaultModelSettingsNotice?>(null)

    init {
        viewModelScope.launch {
            observeSettings()
                .collectLatest { settings ->
                    val selection = settings.ttsSelection
                    if (selection == null || settings.resolveTtsConnection() == null) {
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
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // 出错由 Repository / Provider 内部降级，若仍然异常则置空由兜底策略生效
                        ttsVoiceOptions.value = emptyList()
                    } finally {
                        isTtsVoiceLoading.value = false
                    }
                }
        }
    }

    // 独立于 uiState 订阅：用户动作不能依赖界面是否已开始收集状态。
    private val settingsSnapshot = observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DefaultModelSettings(
            instances = emptyList(),
            assistantSelection = null,
            fastSelection = null,
            speechSelection = null,
            ttsSelection = null,
            ttsVoiceId = "",
        ),
    )

    val uiState = combine(
        settingsSnapshot,
        dialog,
        runWhenAgentIdle.state,
        combine(ttsVoiceOptions, isTtsVoiceLoading) { options, loading -> options to loading },
        mutableNotice,
    ) { settings, currentDialog, runtimeState, voiceOptions, notice ->
        val ttsTemplateId = settings.resolveTtsConnection()?.templateId
        settings.toUiState(
            dialog = currentDialog,
            isMutationBlocked = runtimeState.isBusy,
            ttsVoiceOptions = if (ttsTemplateId == null) emptyList()
                else voiceOptions.first.ifEmpty { voiceCatalog.builtInVoices(ttsTemplateId) },
            isTtsVoiceLoading = ttsTemplateId != null && voiceOptions.second,
            ttsTemplateId = ttsTemplateId,
        ).copy(notice = notice)
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
            DefaultModelSettingsAction.ConsumeNotice -> mutableNotice.value = null
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

    /** 执行一次配置写入；被前台 Agent 阻塞或写盘失败都用 Toast 反馈，成功则关闭当前弹窗。 */
    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                when (runWhenAgentIdle { block() }) {
                    is AgentMutationResult.Applied -> dialog.value = null
                    AgentMutationResult.BlockedByActiveAgent -> mutableNotice.value =
                        DefaultModelSettingsNotice.ShowToast(
                            github.ponyhuang.gimi.feature.modelsettings.R.string.modelsettings_agent_mutation_blocked,
                        )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 语音偏好写入失败会从仓库上抛：反馈保存失败并保留弹窗供重试，
                // 不能让异常穿出 viewModelScope 导致进程崩溃。
                mutableNotice.value = DefaultModelSettingsNotice.ShowToast(
                    github.ponyhuang.gimi.feature.modelsettings.R.string.modelsettings_write_failed,
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
            // Realtime 模型有清除语义，走专用动作。
            DefaultModelDialog.RealtimeModel -> Unit
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

    private fun DefaultModelSettings.resolveTtsConnection() =
        instances.resolveConnection(ttsSelection)?.takeIf { connection ->
            ModelCapability.TTS in connection.model.capabilities &&
                connection.templateId?.let { voiceCapabilities.isSupported(it, ModelCapability.TTS) } == true
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
        val realtimeRows = configuredInstances.rows(
            predicate = { ModelCapability.Realtime in it.capabilities },
            requiredCapability = { ModelCapability.Realtime },
        ).filter { it.isSupported && instances.resolveConnection(it.selection()) != null }
        val ttsRows = configuredInstances.rows(
            predicate = { ModelCapability.TTS in it.capabilities },
            requiredCapability = { ModelCapability.TTS },
        ).filter { instances.resolveConnection(it.selection()) != null }
        return DefaultModelSettingsUiState(
            assistantSelection = assistantSelection,
            fastSelection = fastSelection,
            speechSelection = speechSelection,
            ttsSelection = ttsSelection.takeIf { ttsTemplateId != null },
            ttsVoiceId = ttsVoiceId.takeIf { ttsTemplateId != null }.orEmpty(),
            ttsVoiceOptions = ttsVoiceOptions,
            isTtsVoiceLoading = isTtsVoiceLoading,
            ttsVoiceCustomInputAllowed = voiceCatalog.allowsCustomVoice(ttsTemplateId),
            chatModels = configuredInstances.rows(
                predicate = { ModelCapability.LLM in it.capabilities },
            ),
            speechModels = configuredInstances.rows(
                predicate = { it.capabilities.any { capability -> capability.isRecognition } },
                requiredCapability = { it.recognitionCapability },
            ),
            ttsModels = ttsRows,
            realtimeSelection = realtimeSelection.takeIf { selection ->
                realtimeRows.any { it.selection() == selection }
            },
            realtimeModels = realtimeRows,
            dialog = dialog,
            isMutationBlocked = isMutationBlocked,
        )
    }
}
