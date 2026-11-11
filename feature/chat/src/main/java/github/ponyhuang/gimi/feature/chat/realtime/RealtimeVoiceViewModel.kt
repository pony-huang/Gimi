package github.ponyhuang.gimi.feature.chat.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.usecase.ObserveConversationsUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import github.ponyhuang.gimi.domain.modelcatalog.model.isRecognition
import github.ponyhuang.gimi.domain.modelcatalog.model.recognitionCapability
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.TtsVoiceCatalog
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceMode
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceModeRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 语音页懒启动所需的会话绑定参数；进入页面时由宿主一次性提供。 */
private data class VoiceCallArguments(
    val conversationId: String? = null,
    val selection: ModelSelection? = null,
    val title: String = "",
)

/** 通话侧输入的中间快照，供嵌套 combine 合流。 */
private data class VoiceCallSnapshot(
    val call: RealtimeVoiceCallState,
    val devicePickerOpen: Boolean,
    val arguments: VoiceCallArguments,
    val pendingMode: RealtimeVoiceMode,
) {
    /** 通话是否已启动（含结束清理中的最后一帧）。 */
    val active: Boolean get() = call.active || call.ending
}

/** 设置侧输入的中间快照；写入被前台 Agent 阻塞时只读。 */
private data class VoiceSettingsSnapshot(
    val settings: DefaultModelSettings,
    val mutationBlocked: Boolean,
    val ttsVoiceOptions: List<TtsVoice>,
    val isTtsVoiceLoading: Boolean,
    val promptPresets: PromptPresetsSnapshot,
)

/** 轻聊预设侧输入的中间快照，供嵌套 combine 合流。 */
private data class PromptPresetsSnapshot(
    val userPresets: List<SimpleChatPromptPreset>,
    val selectedPresetId: String?,
)

/** 默认 Realtime 模型配置的解析结果：模式是否可用 + 抽屉里展示的模型名。 */
private data class RealtimeAvailability(val available: Boolean, val modelName: String)

/** 语音页设置抽屉所需的一揽子默认配置状态（默认模型 + 音色）。 */
data class VoiceSettingsUiState(
    val speechRows: List<VoiceModelRow> = emptyList(),
    val speechSelection: ModelSelection? = null,
    val ttsRows: List<VoiceModelRow> = emptyList(),
    val ttsSelection: ModelSelection? = null,
    val ttsVoiceId: String = "",
    val ttsVoiceOptions: List<TtsVoice> = emptyList(),
    val isTtsVoiceLoading: Boolean = false,
    /** TTS 服务是否允许填写内置音色表之外的音色 ID（声音复刻、基础音色）。 */
    val ttsVoiceCustomInputAllowed: Boolean = false,
    val realtimeRows: List<VoiceModelRow> = emptyList(),
    /** Realtime 默认模型；null 表示未配置，Realtime 模式因此不可进入。 */
    val realtimeSelection: ModelSelection? = null,
    val realtimeVoiceOptions: List<TtsVoice> = emptyList(),
    /** 所选 Realtime 模型在该厂商下的缺省会话音色 ID。 */
    val realtimeDefaultVoice: String = "",
    /** 当前生效的自定义音色 ID；null 表示使用 [realtimeDefaultVoice]。 */
    val realtimeVoiceId: String? = null,
    val realtimeCustomVoiceAllowed: Boolean = false,
    /**
     * 是否允许写入配置：通话进行中或前台 Agent 忙时只读。
     * 模型与音色在「开始通话」时快照，运行中修改本就不生效，抽屉里可看不可改。
     */
    val writable: Boolean = false,
    /** 随应用发布的官方轻聊预设；与自建预设一起在抽屉里单选。 */
    val builtInPromptPresets: List<SimpleChatPromptPreset> = emptyList(),
    /** 用户自建的轻聊预设；管理入口在个性化服务页，抽屉仅提供选择。 */
    val userPromptPresets: List<SimpleChatPromptPreset> = emptyList(),
    /** 当前选中的轻聊预设 id；null 表示不使用预设，轻聊回退内置语音人设。 */
    val selectedPromptPresetId: String? = null,
)

/**
 * 语音页唯一状态；通话未启动时 mode 为页面预选模式，switching 期间禁止再次切换或恢复收音。
 * 模式只允许在语音未开启时预选：开启后一律不可更换，需先结束通话（抽屉与 ViewModel 双保险）。
 */
data class RealtimeVoiceUiState(
    val voice: RealtimeVoiceState = RealtimeVoiceState(),
    /** 通话是否已启动（含结束清理中的最后一帧）；false 时底栏主按钮为「开始通话」。 */
    val active: Boolean = false,
    /** 预选模式默认取上次使用的模式（[RealtimeVoiceModeRepository.lastMode]），首次使用回落轻聊。 */
    val mode: RealtimeVoiceMode = RealtimeVoiceMode.Simple,
    val switching: Boolean = false,
    val chatAvailable: Boolean = false,
    /** 是否已配置可用的 Realtime 默认模型（按当前配置判定，与通话状态无关）；false 时两个 Realtime 模式置灰。 */
    val realtimeAvailable: Boolean = false,
    /** 已配置的 Realtime 默认模型名；未配置时为空。 */
    val realtimeModelName: String = "",
    val conversationTitle: String = "",
    val modelName: String = "",
    val ttsModelName: String = "",
    val sttModelName: String = "",
    val devicePickerOpen: Boolean = false,
    /** 模式抽屉里的语音设置条目状态（默认模型与音色）。 */
    val settings: VoiceSettingsUiState = VoiceSettingsUiState(),
)

/**
 * 页面只映射进程级通话状态并保存设备选择器这类临时 UI 状态；
 * 会话所有权在控制器内，ViewModel 销毁或页面离开都不会结束通话。
 * 通话采用懒启动：进入页面只预选模式，用户点「开始通话」才真正占用麦克风。
 *
 * 另外合并默认模型配置（Observe/UpdateDefaultModelSettingsUseCase），
 * 供模式抽屉的语音设置条目（STT/TTS/Realtime 模型与音色）就地查看与修改。
 */
@HiltViewModel
class RealtimeVoiceViewModel @Inject constructor(
    private val controller: RealtimeVoiceCallController,
    private val sessionResolver: ConversationSessionResolver,
    private val observeConversations: ObserveConversationsUseCase,
    private val updateSettings: UpdateDefaultModelSettingsUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val ttsVoiceRepository: TtsVoiceRepository,
    private val promptPresets: SimpleChatPromptRepository,
    private val modeRepository: RealtimeVoiceModeRepository,
    private val voiceCapabilities: VoiceCapabilityResolver,
    observeSettings: ObserveDefaultModelSettingsUseCase,
) : ViewModel() {
    private val devicePickerOpen = MutableStateFlow(false)
    private val startArguments = MutableStateFlow(VoiceCallArguments())
    // 预选模式以上次使用的模式为初值：每次进入语音页都恢复用户最近的选择。
    private val pendingMode = MutableStateFlow(modeRepository.lastMode.value)
    private val newSessionFailed = MutableStateFlow(0L)

    /** 当前选中的 Realtime 默认模型；音色覆盖要绑定到它上面。 */
    private val realtimeSelection = MutableStateFlow<ModelSelection?>(null)
    private val ttsVoiceOptions = MutableStateFlow<List<TtsVoice>>(emptyList())
    private val isTtsVoiceLoading = MutableStateFlow(false)

    /** 设置写入被通话/Agent 阻断的提示代次；Route 观察递增后弹 Toast。 */
    private val settingsWriteBlocked = MutableStateFlow(0L)
    val settingsWriteBlockedRevision: StateFlow<Long> = settingsWriteBlocked

    /** 助手模式新建会话失败的提示代次；Route 观察递增后弹 Toast。 */
    val newSessionFailedRevision: StateFlow<Long> = newSessionFailed

    private val settingsFlow = observeSettings()

    /** 轻聊预设的自建列表与选中项合流；官方预设是静态目录，直接读仓库属性。 */
    private val promptPresetsFlow = combine(
        promptPresets.presets,
        promptPresets.selectedPresetId,
    ) { presets, selectedId -> PromptPresetsSnapshot(presets, selectedId) }

    val callState: StateFlow<RealtimeVoiceCallState> = controller.state

    init {
        // 无论从页面还是状态栏结束，通话代次变化都要关掉上次遗留的设备选择器。
        viewModelScope.launch {
            controller.state.map { it.active }.distinctUntilChanged().collect { active ->
                if (!active) {
                    devicePickerOpen.value = false
                }
            }
        }
        // 音色与 TTS 模型绑定，按完整选择取音色；这里刻意不改写已选音色：
        // 音色集并不封闭（基础音色与声音复刻音色都不在内置表里），按目录判定「失效」
        // 会误伤合法配置，用户填的值也会被静默覆盖。
        viewModelScope.launch {
            settingsFlow.collectLatest { settings ->
                realtimeSelection.value = settings.realtimeSelection
                val selection = settings.ttsSelection
                if (selection == null) {
                    ttsVoiceOptions.value = emptyList()
                    return@collectLatest
                }
                isTtsVoiceLoading.value = true
                try {
                    ttsVoiceOptions.value = ttsVoiceRepository.getVoices(selection)
                } catch (cancelled: CancellationException) {
                    // collectLatest 用取消打断上一次取表；取消必须传播，不得当作取表失败吞掉。
                    throw cancelled
                } catch (_: Exception) {
                    // 出错由 Repository / Provider 内部降级，若仍然异常则置空由兜底策略生效
                } finally {
                    isTtsVoiceLoading.value = false
                }
            }
        }
    }

    val uiState: StateFlow<RealtimeVoiceUiState> =
        combine(
            combine(controller.state, devicePickerOpen, startArguments, pendingMode) { call, picker, arguments, pending ->
                VoiceCallSnapshot(call, picker, arguments, pending)
            },
            combine(settingsFlow, runWhenAgentIdle.state, ttsVoiceOptions, isTtsVoiceLoading, promptPresetsFlow) { settings, runtime, voices, loading, presets ->
                VoiceSettingsSnapshot(settings, runtime.isBusy, voices, loading, presets)
            },
        ) { callSnapshot, settingsSnapshot ->
            val call = callSnapshot.call
            // Realtime 可用性只由默认模型配置决定：通话状态快照在待启动时是默认值、挂断后还会整包复位，
            // 拿它判断会把「已配置」误显示成「请先在默认模型中配置 Realtime 模型」。
            val realtime = realtimeAvailability(settingsSnapshot.settings)
            RealtimeVoiceUiState(
                voice = call.voice,
                active = callSnapshot.active,
                mode = if (callSnapshot.active) call.mode else callSnapshot.pendingMode,
                switching = call.switching || call.ending,
                // 未启动时用宿主传入的绑定判断助手模式可用性；启动后以控制器快照为准。
                chatAvailable = if (callSnapshot.active) call.chatAvailable
                else callSnapshot.arguments.conversationId != null && callSnapshot.arguments.selection != null,
                realtimeAvailable = realtime.available,
                realtimeModelName = realtime.modelName,
                conversationTitle = if (callSnapshot.active) call.conversationTitle else callSnapshot.arguments.title,
                modelName = call.modelName,
                ttsModelName = call.ttsModelName,
                sttModelName = call.sttModelName,
                devicePickerOpen = callSnapshot.devicePickerOpen && call.active,
                settings = voiceSettingsState(
                    settings = settingsSnapshot.settings,
                    mutationBlocked = settingsSnapshot.mutationBlocked,
                    callActive = callSnapshot.active,
                    ttsVoiceOptions = settingsSnapshot.ttsVoiceOptions,
                    isTtsVoiceLoading = settingsSnapshot.isTtsVoiceLoading,
                    promptPresets = settingsSnapshot.promptPresets,
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), RealtimeVoiceUiState())

    /** 进入页面时登记会话绑定参数；供模式预选可用性判断与「开始通话」使用。 */
    fun prepare(conversationId: String?, selection: ModelSelection?, title: String) {
        startArguments.value = VoiceCallArguments(
            conversationId = conversationId?.takeIf(String::isNotBlank),
            selection = selection,
            title = title,
        )
    }

    /** 用户点「开始通话」：按预选模式启动；权限由 Route 在调用前确认。 */
    fun startCall() {
        if (uiState.value.switching) return
        val arguments = startArguments.value
        controller.start(arguments.conversationId, arguments.selection, arguments.title, pendingMode.value)
    }

    /**
     * 只在语音未开启时预选模式，真正生效发生在 [startCall]。
     *
     * 语音开启后不允许更换任何模式：切换会重建会话并打断采集/播放，需先结束通话再选。
     * 抽屉入口与模式行在 UI 层同步禁用，这里是绕过 UI 时的兜底。
     * 预选即写穿到 [modeRepository]：下次进入语音页按此次选择恢复预选。
     */
    fun selectMode(mode: RealtimeVoiceMode) {
        val call = controller.state.value
        if (call.active || call.ending || call.switching || uiState.value.switching ||
            call.voice.failure == RealtimeVoiceFailure.Persistence) return
        pendingMode.value = mode
        modeRepository.setLastMode(mode)
    }

    /**
     * 开启新的会话：助手模式复用聊天页新建会话逻辑（默认模型与工具）并换绑；
     * 轻聊与实时模式无持久化，直接按当前模式重开内存会话。创建失败时保持原会话并提示。
     */
    fun newSession() {
        val call = controller.state.value
        if (!call.active || call.switching || call.ending) return
        if (call.mode != RealtimeVoiceMode.Chat) {
            controller.restart(null, "")
            return
        }
        viewModelScope.launch {
            try {
                val snapshot = sessionResolver.createAndActivate()
                val title = observeConversations().value
                    .firstOrNull { it.id == snapshot.sessionId }?.title.orEmpty()
                controller.restart(snapshot.sessionId, title)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                newSessionFailed.value += 1
            }
        }
    }

    fun selectSpeechModel(selection: ModelSelection) = mutate { updateSettings.speech(selection) }
    fun selectTtsModel(selection: ModelSelection) = mutate { updateSettings.tts(selection) }
    fun selectTtsVoice(voiceId: String) = mutate { updateSettings.voice(voiceId) }

    /** 选择 Realtime 默认模型；null 表示取消配置（Realtime 模式随之不可进入）。 */
    fun selectRealtimeModel(selection: ModelSelection?) = mutate { updateSettings.realtimeModel(selection) }

    /** 设置 Realtime 会话音色；null 表示回到所选模型的缺省音色。 */
    fun selectRealtimeVoice(voiceId: String?) =
        mutate { updateSettings.realtimeVoice(voiceId?.let(::realtimeOverride)) }

    /**
     * 选择轻聊预设；null 表示不使用预设，轻聊回退内置语音人设。
     * 预设按轮重建本可逐轮生效，但为与其它抽屉设置一致，通话/Agent 忙时同样拒绝写入。
     */
    fun selectPromptPreset(presetId: String?) = mutate { promptPresets.selectPreset(presetId) }

    fun toggleMute() = controller.toggleMute()
    fun openDevicePicker() { if (!uiState.value.switching) devicePickerOpen.value = true }
    fun dismissDevicePicker() { devicePickerOpen.value = false }
    fun selectAudioDevice(deviceId: Int?) { controller.selectAudioDevice(deviceId); dismissDevicePicker() }

    fun retry() = controller.retry()

    /** 页面挂断是唯一的页面结束入口，与状态栏结束汇合到同一个控制器清理链。 */
    fun end() = controller.end()
    fun consumeReturnToCall() = controller.consumeReturnToCall()

    /**
     * 把音色覆盖绑定到当前的 Realtime 模型。
     *
     * 音色与模型强绑定（同一厂商不同代际的合法音色集互不相同），因此覆盖值必须记住所属模型，
     * 换模型后旧覆盖自动失效并回落到新模型的缺省音色。
     */
    private fun realtimeOverride(voiceId: String): RealtimeVoiceOverride? {
        val selection = realtimeSelection.value ?: return null
        return RealtimeVoiceOverride(selection = selection, voiceId = voiceId)
    }

    /**
     * 解析当前默认模型配置下 Realtime 模式是否可用，以及抽屉里要展示的模型名。
     *
     * 与语音设置抽屉的 Realtime 候选行同源，并与控制器的 Realtime 默认解析同样校验
     * 服务已添加/启用/有密钥、已接入原生 Realtime 适配器、默认选择仍指向目录中带
     * Realtime 能力的模型。通话状态不参与判断——懒启动下待启动或挂断后的通话快照
     * 并不携带这份配置；判定与控制器保持一致也避免「菜单可选、开始通话却回落轻聊」。
     */
    private fun realtimeAvailability(settings: DefaultModelSettings): RealtimeAvailability {
        val selection = settings.realtimeSelection ?: return RealtimeAvailability(false, "")
        val service = settings.services.firstOrNull { it.id == selection.serviceId }
            ?.takeIf { it.isAdded && it.isEnabled && it.apiKey.isNotBlank() }
            ?: return RealtimeAvailability(false, "")
        if (!voiceCapabilities.isSupported(service.id, ModelCapability.Realtime)) {
            return RealtimeAvailability(false, "")
        }
        val model = service.groups.firstOrNull { it.id == selection.groupId }?.models
            ?.firstOrNull { it.id == selection.modelId && ModelCapability.Realtime in it.capabilities }
            ?: return RealtimeAvailability(false, "")
        return RealtimeAvailability(true, model.name.ifBlank { model.id })
    }

    /**
     * 执行一次配置写入。
     *
     * 通话进行中或前台 Agent 忙时写入被拒并提示（抽屉条目在 UI 层已置灰，这里是兜底）。
     */
    private fun mutate(block: () -> Unit) {
        viewModelScope.launch {
            if (controller.state.value.active) {
                settingsWriteBlocked.value += 1
                return@launch
            }
            when (runWhenAgentIdle { block() }) {
                is AgentMutationResult.Applied -> Unit
                AgentMutationResult.BlockedByActiveAgent ->
                    settingsWriteBlocked.value += 1
            }
        }
    }

    private fun voiceSettingsState(
        settings: DefaultModelSettings,
        mutationBlocked: Boolean,
        callActive: Boolean,
        ttsVoiceOptions: List<TtsVoice>,
        isTtsVoiceLoading: Boolean,
        promptPresets: PromptPresetsSnapshot,
    ): VoiceSettingsUiState {
        val configuredServices = settings.services.filter { it.isEnabled && it.apiKey.isNotBlank() }
        // Realtime 候选只列已接入原生全双工适配器的服务：带 Realtime 标记但没有适配器的模型
        // 会静默回落到级联链路（而级联要求 STT/TTS 能力），选它只会得到起不来的会话。
        val realtimeRows = configuredServices
            .filter { voiceCapabilities.isSupported(it.id, ModelCapability.Realtime) }
            .flatMap { service ->
                service.groups.flatMap { group ->
                    group.models.filter { ModelCapability.Realtime in it.capabilities }.map { model ->
                        VoiceModelRow(
                            service = service,
                            group = group,
                            model = model,
                            capability = ModelCapability.Realtime,
                        )
                    }
                }
            }
        val realtimeSupport = settings.realtimeSelection?.let { voiceCapabilities.realtimeSupport(it.serviceId) }
        val applicableVoice = settings.realtimeVoice?.takeIf { it.selection == settings.realtimeSelection }
        return VoiceSettingsUiState(
            speechRows = configuredServices.voiceModelRows(
                voiceCapabilities,
                predicate = { it.capabilities.any { capability -> capability.isRecognition } },
                requiredCapability = { it.recognitionCapability },
            ),
            speechSelection = settings.speechSelection,
            ttsRows = configuredServices.voiceModelRows(
                voiceCapabilities,
                predicate = { ModelCapability.TTS in it.capabilities },
                requiredCapability = { ModelCapability.TTS },
            ),
            ttsSelection = settings.ttsSelection,
            ttsVoiceId = settings.ttsVoiceId,
            ttsVoiceOptions = ttsVoiceOptions.ifEmpty {
                TtsVoiceCatalog.forService(settings.ttsSelection?.serviceId)
            },
            isTtsVoiceLoading = isTtsVoiceLoading,
            ttsVoiceCustomInputAllowed = TtsVoiceCatalog.allowsCustomVoice(settings.ttsSelection?.serviceId),
            realtimeRows = realtimeRows,
            realtimeSelection = settings.realtimeSelection,
            realtimeVoiceOptions = settings.realtimeSelection
                ?.let { selection -> realtimeSupport?.voicesFor(selection.modelId) }
                .orEmpty(),
            realtimeDefaultVoice = settings.realtimeSelection
                ?.let { selection -> realtimeSupport?.defaultVoiceFor(selection.modelId) }
                .orEmpty(),
            realtimeVoiceId = applicableVoice?.voiceId,
            realtimeCustomVoiceAllowed = realtimeSupport?.customVoiceAllowed == true,
            writable = !callActive && !mutationBlocked,
            builtInPromptPresets = this.promptPresets.builtInPresets,
            userPromptPresets = promptPresets.userPresets,
            selectedPromptPresetId = promptPresets.selectedPresetId,
        )
    }
}
