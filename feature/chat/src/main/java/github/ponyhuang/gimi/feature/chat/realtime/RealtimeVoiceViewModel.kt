package github.ponyhuang.gimi.feature.chat.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.VendorFileReference
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.ValidateChatAttachmentsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.gimi.domain.modelcatalog.model.FileIngestMode
import github.ponyhuang.gimi.domain.modelcatalog.model.isOmniLlm
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.domain.modelcatalog.model.VisionCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.isRecognition
import github.ponyhuang.gimi.domain.modelcatalog.model.recognitionCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.ApplyVoiceConfigUseCase
import github.ponyhuang.gimi.domain.voice.repository.ConversationVoiceOverrides
import github.ponyhuang.gimi.domain.voice.repository.EffectiveVoiceConfig
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigDraft
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigField
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigLookup
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceConversationStyle
import github.ponyhuang.gimi.domain.voice.repository.VoiceFieldOverride
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaults
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaultsRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceInputResult
import github.ponyhuang.gimi.domain.voice.repository.VoiceManualInput
import github.ponyhuang.gimi.domain.voice.repository.VoiceOverrideRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceApplyResult
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import github.ponyhuang.gimi.domain.voice.repository.isOverridden
import github.ponyhuang.gimi.feature.chat.AttachmentSelectionResult
import github.ponyhuang.gimi.feature.chat.ChatFileUploadCoordinator
import github.ponyhuang.gimi.feature.chat.ChatSubmissionResult
import github.ponyhuang.gimi.feature.chat.DraftUploadState
import github.ponyhuang.gimi.feature.chat.MessageData
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 语音页懒启动所需的会话绑定参数；进入页面时由宿主一次性提供。 */
private data class VoiceCallArguments(
    val conversationId: String? = null,
    val selection: ModelSelection? = null,
    val title: String = "",
    val style: VoiceConversationStyle = VoiceConversationStyle.ASSISTANT,
)

/** 通话侧输入的中间快照，供嵌套 combine 合流。 */
private data class VoiceCallSnapshot(
    val call: RealtimeVoiceCallState,
    val arguments: VoiceCallArguments,
) {
    /** 通话是否已启动（含结束清理中的最后一帧）。 */
    val active: Boolean get() = call.active || call.ending
}

/** 设置侧输入的中间快照：有效配置、候选行、音色选项。 */
private data class VoiceSettingsSnapshot(
    val settings: DefaultModelSettings,
    val effective: EffectiveVoiceConfig,
    val mutationBlocked: Boolean,
    val synthesisVoiceOptions: List<TtsVoice>,
    val isSynthesisVoiceLoading: Boolean,
)

/**
 * 链路可用性：由模型目录实例与有效配置实时计算。
 * 它属于环境能力，不随通话结束而清零，因此不放在通话快照里。
 */
private data class PipelineAvailability(
    val cascaded: Boolean = false,
    val semiCascaded: Boolean = false,
    val realtime: Boolean = false,
)


/**
 * 设置面板内的配置选择及已保存的对话覆盖。配置不完整时保留选择，供用户补齐缺项。
 */
data class VoiceConfigDraftState(
    val overrides: ConversationVoiceOverrides,
    val committed: ConversationVoiceOverrides,
    /** 当前选择缺少的必需模型，界面标记对应行。 */
    val missing: Set<VoiceConfigField> = emptySet(),
) {
    /** 面板内的选择尚未写入对话覆盖。 */
    val isDirty: Boolean get() = overrides != committed
}

/**
 * 草稿单字段展示：生效值（草稿覆盖优先，否则全局默认/记忆解析）+ 继承与未应用标记。
 *
 * @property value 生效值；音色字段为 null 表示采用厂商默认音色。
 * @property overridden 该字段在草稿中为对话覆盖。
 * @property dirty 该字段草稿值与已提交内容不一致（应用后清除）。
 * @property substituted 音色字段专用：记忆或覆盖音色失效后已替代为厂商默认。
 */
data class VoiceDraftFieldUi<out T>(
    val value: T,
    val overridden: Boolean = false,
    val dirty: Boolean = false,
    val substituted: Boolean = false,
)

/** 抽屉草稿态：对话方式、链路与各链路字段的草稿值、继承状态与缺项。 */
data class VoiceDraftUiState(
    val pipeline: VoiceDraftFieldUi<VoicePipeline> = VoiceDraftFieldUi(VoicePipeline.CASCADED),
    val cascadedRecognitionModel: VoiceDraftFieldUi<ModelSelection?> = VoiceDraftFieldUi(null),
    val cascadedSynthesisModel: VoiceDraftFieldUi<ModelSelection?> = VoiceDraftFieldUi(null),
    val cascadedVoice: VoiceDraftFieldUi<String?> = VoiceDraftFieldUi(null),
    val semiUnderstandingModel: VoiceDraftFieldUi<ModelSelection?> = VoiceDraftFieldUi(null),
    val semiSynthesisModel: VoiceDraftFieldUi<ModelSelection?> = VoiceDraftFieldUi(null),
    val semiVoice: VoiceDraftFieldUi<String?> = VoiceDraftFieldUi(null),
    val realtimeModel: VoiceDraftFieldUi<ModelSelection?> = VoiceDraftFieldUi(null),
    val realtimeVoice: VoiceDraftFieldUi<String?> = VoiceDraftFieldUi(null),
    /** 草稿相对已提交内容有未应用修改的字段；空集合表示草稿干净。 */
    val dirtyFields: Set<VoiceConfigField> = emptySet(),
    val isDirty: Boolean = false,
    /** 草稿中存在对话覆盖（含未应用修改与已提交覆盖）。 */
    val hasAnyOverride: Boolean = false,
    /** 激活链路必需但缺失或失效的字段。 */
    val missing: Set<VoiceConfigField> = emptySet(),
)

/** 语音页设置抽屉所需的候选与摘要状态（候选模型行、音色选项、Chatbot 预设）。 */
data class VoiceSettingsUiState(
    /** 级联识别模型候选（含半级联合成候选复用同表）。 */
    val recognitionRows: List<VoiceModelRow> = emptyList(),
    /** 级联／半级联合成模型候选。 */
    val synthesisRows: List<VoiceModelRow> = emptyList(),
    /** 半级联音频理解模型候选；暂未接入具体业务，仅保留配置入口。 */
    val understandingRows: List<VoiceModelRow> = emptyList(),
    /** 原生实时模型候选：只列已接入原生全双工适配器的模板。 */
    val realtimeRows: List<VoiceModelRow> = emptyList(),
    /** 级联／半级联合成音色选项；随草稿合成模型变化。 */
    val synthesisVoiceOptions: List<TtsVoice> = emptyList(),
    val isSynthesisVoiceLoading: Boolean = false,
    /** 合成服务是否允许填写内置音色表之外的音色 ID（声音复刻、基础音色）。 */
    val synthesisVoiceCustomInputAllowed: Boolean = false,
    /** 开放自由填写厂商（千问）的音色 ID 命名模板；null 表示不展示。 */
    val synthesisVoiceIdTemplate: String? = null,
    /** 开放自由填写厂商的官方音色列表页；null 表示不展示。 */
    val synthesisVoiceDocsUrl: String? = null,
    /** 所选实时模型在该厂商下的可选会话音色。 */
    val realtimeVoiceOptions: List<TtsVoice> = emptyList(),
    val realtimeCustomVoiceAllowed: Boolean = false,
    /** 所选实时模型的厂商缺省会话音色 ID。 */
    val realtimeDefaultVoice: String = "",
)

/**
 * 语音页唯一状态。对话方式与语音处理链路两个正交维度分别表达：
 * 通话中取通话快照，待启动时取草稿生效值。配置编辑以草稿承载，
 * 应用经 [ApplyVoiceConfigUseCase] 原子提交并按需重建通话。
 */
data class RealtimeVoiceUiState(
    val voice: RealtimeVoiceState = RealtimeVoiceState(),
    /** 通话是否已启动（含结束清理中的最后一帧）；false 时底栏主按钮为「开始通话」。 */
    val active: Boolean = false,
    /** 展示用对话方式：通话中取通话快照，否则取草稿生效值。 */
    val style: VoiceConversationStyle = VoiceConversationStyle.CHATBOT,
    /** 展示用语音处理链路：通话中取通话快照，否则取草稿生效值。 */
    val pipeline: VoicePipeline = VoicePipeline.CASCADED,
    val switching: Boolean = false,
    /** 级联链路可用：识别与合成默认模型有效。 */
    val cascadedAvailable: Boolean = false,
    val semiCascadedAvailable: Boolean = false,
    /** 原生实时链路可用：已配置接入适配器的有效实时默认模型。 */
    val realtimeAvailable: Boolean = false,
    val conversationTitle: String = "",
    val modelName: String = "",
    val chatModelName: String = "",
    /** 模型服务模板 ID，用于显示厂商品牌图标。 */
    val modelTemplateId: String = "",
    val chatModelTemplateId: String = "",
    val ttsModelName: String = "",
    val sttModelName: String = "",
    /** 从连接开始到通话清理完成期间锁定设置。 */
    val settingsLocked: Boolean = false,
    /** 通话正在释放音频与会话资源。 */
    val ending: Boolean = false,
    /** 语音配置正在保存，启动通话须等写入结束。 */
    val settingsSaving: Boolean = false,
    /** 当前聊天模型支持半级联所需的全模态输入。 */
    val semiCascadedSupported: Boolean = false,
    /** 设置面板内尚未保存的选择和缺项。 */
    val draft: VoiceDraftUiState = VoiceDraftUiState(),
    val settings: VoiceSettingsUiState = VoiceSettingsUiState(),
    val composer: VoiceComposerUiState = VoiceComposerUiState(),
)

/**
 * 页面只映射进程级通话状态并保存抽屉草稿这类临时 UI 状态；
 * 会话所有权在控制器内，ViewModel 销毁或页面离开都不会结束通话。
 * 通话采用懒启动：进入页面预选草稿配置，用户点「开始通话」才真正占用麦克风。
 *
 * 设置编辑仅在待机期间可用。变更在有效链路完整后自动原子保存，缺项在面板内补齐。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RealtimeVoiceViewModel @Inject constructor(
    private val controller: RealtimeVoiceCallController,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val ttsVoiceRepository: TtsVoiceRepository,
    private val voiceCapabilities: VoiceCapabilityResolver,
    private val voiceCatalog: VoiceCatalogResolver,
    private val defaultsRepository: VoiceGlobalDefaultsRepository,
    private val overridesRepository: VoiceOverrideRepository,
    private val applyVoiceConfig: ApplyVoiceConfigUseCase,
    private val lookup: VoiceConfigLookup,
    observeSettings: ObserveDefaultModelSettingsUseCase,
    private val attachmentRepository: ChatAttachmentRepository,
    private val fileGateway: VendorFileGateway,
    private val uploadedFileRegistry: UploadedFileRegistry,
    private val validateAttachments: ValidateChatAttachmentsUseCase,
) : ViewModel() {
    private var navigationJob: Job? = null
    private val startArguments = MutableStateFlow(VoiceCallArguments())

    /** 当前会话绑定；对话覆盖按它观察与提交。 */
    private val conversationId = MutableStateFlow<String?>(null)

    /** 设置抽屉草稿；null 表示抽屉未打开或草稿已放弃。 */
    private val draft = MutableStateFlow<VoiceConfigDraftState?>(null)

    private val settingsSaving = MutableStateFlow(false)
    private var settingsOpen = false

    /** 应用结果通知（冲突／缺项）；Route 观察变化后弹 Toast 并消费。 */
    private val configNotice = MutableStateFlow(0L to null as ConfigNotice?)
    val configNotices: StateFlow<Pair<Long, ConfigNotice?>> = configNotice

    /** 无法解析的已存配置被重建的代次；Route 观察递增后提示「语音配置已重建」。 */
    val configRebuiltRevisions: StateFlow<Int> = overridesRepository.rebuildCount

    /** 配置通知分类；文案与 Toast 生命周期由 Route 负责。 */
    sealed interface ConfigNotice {
        enum class Problem : ConfigNotice { Conflict, MissingRequired, VoiceModelMissing, NoConversation }

        /** 未能保存的设置字段，供界面提示用户重新选择。 */
        data class SaveFailed(val fields: Set<VoiceConfigField>) : ConfigNotice
    }

    /** 提示过重建后清零计数，重进语音页不会重复弹 Toast。 */
    fun acknowledgeConfigRebuilt() {
        viewModelScope.launch { overridesRepository.acknowledgeRebuilds() }
    }

    private val settingsFlow = observeSettings()
    private val composer = MutableStateFlow(VoiceComposerUiState())

    /** 草稿合成音色选项与加载标记；与合成模型解耦以便按模型重新取表。 */
    private val composerSynthesisVoiceOptions = MutableStateFlow<List<TtsVoice>>(emptyList())
    private val isSynthesisVoiceLoading = MutableStateFlow(false)
    private var latestSettings: DefaultModelSettings? = null
    private val preparedAttachments = mutableMapOf<DraftAttachment, FileAttachment>()
    private val fileUploads = ChatFileUploadCoordinator(
        scope = viewModelScope,
        gateway = fileGateway,
        registry = uploadedFileRegistry,
        resolveConnection = { selection -> latestSettings?.instances?.resolveConnection(selection) },
        isFileUploadEnabled = { composer.value.fileUploadEnabled },
    )

    /** 已提交对话覆盖；会话绑定变化时切换观察目标。 */
    private val committedOverridesFlow: StateFlow<ConversationVoiceOverrides> =
        conversationId.flatMapLatest { id ->
            if (id == null) flowOf(ConversationVoiceOverrides(""))
            else overridesRepository.observe(id)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            ConversationVoiceOverrides(""),
        )

    /** 草稿与已提交覆盖一起参与展示：抽屉未打开时也要显示正确的继承/覆盖状态。 */
    private val draftPresentation = combine(draft, committedOverridesFlow, settingsSaving) { state, committed, saving ->
        Triple(state, committed, saving)
    }

    /** 草稿覆盖优先于已提交覆盖：抽屉内的字段展示与解析一律看草稿。 */
    private val effectiveOverrides = combine(
        draft.map { it?.overrides }.distinctUntilChanged(),
        committedOverridesFlow,
    ) { pending, committed -> pending ?: committed }

    /** 草稿态的有效语音配置：字段展示、缺项定位与音色解析统一由此出发。 */
    private val effectiveConfig: StateFlow<EffectiveVoiceConfig> = combine(
        defaultsRepository.defaults,
        effectiveOverrides,
    ) { defaults, overrides ->
        VoiceConfigResolver.resolve(defaults, overrides, lookup)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), resolveInitialConfig())

    /**
     * 链路可用性由目录实例与有效配置直接计算：通话快照在挂断时整体复位，
     * 若从快照读取，待启动页面会误显示全部链路不可用。
     */
    private fun pipelineAvailability(
        effective: EffectiveVoiceConfig,
        settings: DefaultModelSettings,
        currentChatSelection: ModelSelection?,
    ): PipelineAvailability {
        val realtimeSelection = effective.realtimeModel
        val realtimeConnection = settings.instances.resolveConnection(realtimeSelection)
        val currentChatConnection = settings.instances.resolveConnection(currentChatSelection)
        val isOmni = currentChatConnection?.model?.isOmniLlm == true
        return PipelineAvailability(
            cascaded = effective.cascadedRecognitionModel?.let(lookup::isModelValid) == true &&
                effective.cascadedSynthesisModel?.let(lookup::isModelValid) == true,
            semiCascaded = isOmni && effective.semiCascadedSynthesisModel?.let(lookup::isModelValid) == true,
            realtime = realtimeSelection != null && lookup.isModelValid(realtimeSelection) &&
                ModelCapability.Realtime in (realtimeConnection?.model?.capabilities.orEmpty()) &&
                realtimeConnection?.templateId?.let {
                    voiceCapabilities.isSupported(it, ModelCapability.Realtime)
                } == true,
        )
    }

    val callState: StateFlow<RealtimeVoiceCallState> = controller.state

    init {
        viewModelScope.launch {
            combine(
                settingsFlow,
                controller.state,
                startArguments,
                effectiveConfig.map { it.pipeline },
            ) { settings, call, arguments, pipeline ->
                // 缓存给上传/提交等命令式路径取用：coordinator 的连接解析、发送校验与
                // inputSelection 回退都读这份快照；漏赋值会让上传连接解析恒为 null（静默失败）。
                latestSettings = settings
                val selection = call.modelSelection ?: if (pipeline == VoicePipeline.NATIVE_REALTIME) {
                    settings.realtimeSelection
                } else {
                    arguments.selection ?: settings.assistantSelection
                }
                val resolved = settings.instances.resolveConnection(selection)
                val capabilities = if (pipeline == VoicePipeline.NATIVE_REALTIME) {
                    MultimodalCapabilities(
                        vision = if (resolved?.templateId == "openai") VisionCapability() else null,
                        audioInput = null, documentInput = null,
                    )
                } else resolved?.model?.multimodal ?: MultimodalCapabilities(null, null, null)
                composer.update { it.copy(capabilities = capabilities,
                    vendorTemplateId = resolved?.templateId,
                    fileUploadEnabled = capabilities.documentInput?.ingestMode == FileIngestMode.FILE_REFERENCE) }
                resolved?.templateId
            }.distinctUntilChanged().collectLatest { vendor ->
                if (vendor == null) composer.update { it.copy(uploadedFiles = emptyList()) }
                else uploadedFileRegistry.entriesFor(vendor).collect { entries ->
                    composer.update { it.copy(uploadedFiles = entries) }
                }
            }
        }
        viewModelScope.launch {
            fileUploads.states.collect { uploads ->
                composer.update { state -> state.copy(uploads = uploads, draft = state.draft.copy(
                    attachments = state.draft.attachments.map { draft ->
                        val ready = uploads[draft.reference] as? DraftUploadState.Ready
                        if (ready == null) draft else draft.copy(fileId = ready.fileId, fileVendor = ready.vendor)
                    },
                )) }
            }
        }

        // 无论从页面还是状态栏结束，通话代次变化都要收起输入编辑态。
        viewModelScope.launch {
            controller.state.map { it.active }.distinctUntilChanged().collect { active ->
                if (!active) {
                    composer.update { it.copy(editing = false) }
                }
            }
        }
        // 合成音色与草稿合成模型绑定：模型变化时按完整选择重新取音色表。
        // 这里刻意不改写已选音色：音色集并不封闭（基础音色与声音复刻音色都不在
        // 内置表里），按目录判定「失效」会误伤合法配置，用户填的值也会被静默覆盖。
        viewModelScope.launch {
            effectiveConfig.map {
                if (it.pipeline == VoicePipeline.SEMI_CASCADED) it.semiCascadedSynthesisModel
                else it.cascadedSynthesisModel
            }.distinctUntilChanged().collectLatest { selection ->
                if (selection == null) {
                    composerSynthesisVoiceOptions.value = emptyList()
                    return@collectLatest
                }
                isSynthesisVoiceLoading.value = true
                try {
                    composerSynthesisVoiceOptions.value = ttsVoiceRepository.getVoices(selection)
                } catch (cancelled: CancellationException) {
                    // collectLatest 用取消打断上一次取表；取消必须传播，不得当作取表失败吞掉。
                    throw cancelled
                } catch (_: Exception) {
                    // 出错由 Repository / Provider 内部降级，若仍然异常则置空由兜底策略生效
                } finally {
                    isSynthesisVoiceLoading.value = false
                }
            }
        }
        // 会话覆盖被外部更新（应用成功落库）时刷新草稿的已提交基线；
        // 草稿尚无编辑（与旧已提交一致）时一并同步覆盖内容，避免加载竞态误报未应用。
        viewModelScope.launch {
            committedOverridesFlow.collect { committed ->
                draft.update { state ->
                    when {
                        state == null || state.overrides.conversationId != committed.conversationId -> state
                        state.overrides == state.committed ->
                            state.copy(overrides = committed, committed = committed)
                        else -> state.copy(committed = committed)
                    }
                }
            }
        }
    }

    private fun resolveInitialConfig(): EffectiveVoiceConfig = VoiceConfigResolver.resolve(
        defaultsRepository.defaults.value,
        ConversationVoiceOverrides(""),
        lookup,
    )

    /** Route 逐条消费通知，消息文案与 Toast 生命周期由 UI 入口负责。 */
    val inputNotices = composer.map { it.noticeRevision to it.error }.distinctUntilChanged()

    private fun notifyInput(error: VoiceComposerError) {
        composer.update { it.copy(error = error, noticeRevision = it.noticeRevision + 1) }
    }

    fun consumeInputNotice(revision: Long) {
        composer.update { if (it.noticeRevision == revision) it.copy(error = null) else it }
    }

    fun consumeConfigNotice(revision: Long) {
        configNotice.update { if (it.first == revision) it.first to null else it }
    }

    private fun notifyConfig(notice: ConfigNotice) {
        configNotice.value = configNotice.value.first + 1 to notice
    }

    private fun inputSelection(): ModelSelection? {
        val call = controller.state.value
        return call.modelSelection ?: if (effectiveConfig.value.pipeline == VoicePipeline.NATIVE_REALTIME) {
            latestSettings?.realtimeSelection
        } else {
            startArguments.value.selection ?: latestSettings?.assistantSelection
        }
    }

    /** 编辑状态只控制临时输入门，不改变用户静音选择。 */
    fun editInput() {
        val call = controller.state.value
        if (!call.active || call.ending || call.switching) {
            notifyInput(VoiceComposerError.StartCall)
            return
        }
        controller.setInputPaused(true)
        composer.update { it.copy(editing = true, error = null) }
    }

    fun closeInput() {
        if (composer.value.submitting) return
        composer.update { it.copy(editing = false) }
        controller.setInputPaused(false)
    }

    fun updateDraft(draft: MessageData) {
        if (composer.value.submitting) return
        val removed = composer.value.draft.attachments.filterNot { it in draft.attachments }
        fileUploads.clear(removed.map { it.reference })
        composer.update { it.copy(draft = draft, error = null) }
        preparedAttachments.keys.retainAll(draft.attachments.toSet())
    }

    fun uploadDocument(draft: DraftAttachment) {
        inputSelection()?.let { fileUploads.upload(draft, it) }
    }

    fun attachUploadedFile(entry: UploadedFileEntry) {
        val draft = DraftAttachment(
            reference = VendorFileReference.uri(entry.vendor, entry.fileId),
            displayName = entry.displayName, mimeType = entry.mimeType, sizeBytes = entry.bytes,
            category = AttachmentCategory.DOCUMENT,
            fileId = entry.fileId, fileVendor = entry.vendor,
        )
        val result = github.ponyhuang.gimi.feature.chat.mergeAttachmentSelection(composer.value.draft.attachments, listOf(draft))
        if (result is AttachmentSelectionResult.Accepted) {
            updateDraft(composer.value.draft.copy(attachments = result.attachments))
        }
    }

    fun submitInput(message: MessageData, complete: (ChatSubmissionResult) -> Unit) {
        val call = controller.state.value
        val selection = inputSelection()
        if (composer.value.submitting || !call.active || call.ending || selection == null ||
            (message.text.isBlank() && message.attachments.isEmpty())) {
            complete(ChatSubmissionResult.REJECTED)
            return
        }
        val instances = latestSettings?.instances.orEmpty()
        val vendor = instances.firstOrNull { it.id == selection.instanceId }?.templateId
        val invalid = validateAttachments(selection, instances, message.attachments) != null ||
            message.attachments.any { it.fileVendor != null && it.fileVendor != vendor } ||
            (composer.value.fileUploadEnabled && message.attachments.any {
                it.category == AttachmentCategory.DOCUMENT && !it.isVendorUploaded
            })
        if (invalid) {
            notifyInput(VoiceComposerError.Attachment)
            complete(ChatSubmissionResult.REJECTED)
            return
        }
        composer.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            var accepted = false
            try {
                val attachments = message.attachments.map { draft ->
                    // 逐个缓存已归档结果：后续文件失败也不能丢失已移动文档的引用。
                    preparedAttachments[draft] ?: attachmentRepository.read("voice", listOf(draft)).single().also {
                        preparedAttachments[draft] = it
                    }
                }
                if (!controller.state.value.active || controller.state.value.callId != call.callId) return@launch
                val result = controller.submit(VoiceManualInput(message.text, attachments))
                accepted = result == VoiceInputResult.Accepted
                if (accepted) {
                    fileUploads.clear(message.attachments.map { it.reference })
                    composer.update { it.copy(draft = MessageData(), editing = false, error = null) }
                    controller.setInputPaused(false)
                    preparedAttachments.clear()
                    try {
                        attachmentRepository.deleteDrafts(message.attachments)
                    } catch (_: java.io.IOException) {
                        // 已接管消息不能因草稿文件清理失败回滚。
                    }
                } else notifyInput(when (result) {
                    VoiceInputResult.Unsupported -> VoiceComposerError.Unsupported
                    VoiceInputResult.Unavailable -> VoiceComposerError.Unavailable
                    else -> VoiceComposerError.Submit
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notifyInput(VoiceComposerError.Submit)
            } finally {
                composer.update { it.copy(submitting = false) }
                complete(if (accepted) ChatSubmissionResult.ACCEPTED else ChatSubmissionResult.REJECTED)
            }
        }
    }

    override fun onCleared() {
        controller.setInputPaused(false)
        super.onCleared()
    }

    val uiState: StateFlow<RealtimeVoiceUiState> =
        combine(
            combine(controller.state, startArguments) { call, arguments ->
                VoiceCallSnapshot(call, arguments)
            },
            combine(
                settingsFlow,
                effectiveConfig,
                runWhenAgentIdle.state,
                combine(composerSynthesisVoiceOptions, isSynthesisVoiceLoading) { options, loading ->
                    options to loading
                },
            ) { settings, effective, runtime, voiceOptions ->
                VoiceSettingsSnapshot(
                    settings = settings,
                    effective = effective,
                    mutationBlocked = runtime.isBusy,
                    synthesisVoiceOptions = voiceOptions.first,
                    isSynthesisVoiceLoading = voiceOptions.second,
                )
            },
            composer,
            draftPresentation,
        ) { callSnapshot, settingsSnapshot, composerState, presentation ->
            val (draftState, committedOverrides, saving) = presentation
            val call = callSnapshot.call
            val draftUi = voiceDraftState(settingsSnapshot.effective, draftState, committedOverrides)
            val style = if (callSnapshot.active) call.style else callSnapshot.arguments.style
            val modelSettings = settingsSnapshot.settings
            val chatSelection = callSnapshot.arguments.selection ?: modelSettings.assistantSelection
            val availability = pipelineAvailability(settingsSnapshot.effective, modelSettings, chatSelection)
            val committedConfig = VoiceConfigResolver.resolve(defaultsRepository.defaults.value, committedOverrides, lookup)
            val primarySelection = if (committedConfig.pipeline == VoicePipeline.NATIVE_REALTIME) {
                committedConfig.realtimeModel
            } else {
                chatSelection
            }
            val displayedSelection = if (callSnapshot.active) call.modelSelection else primarySelection
            RealtimeVoiceUiState(
                voice = call.voice,
                composer = composerState,
                active = callSnapshot.active,
                style = style,
                pipeline = if (callSnapshot.active) call.pipeline else committedConfig.pipeline,
                switching = call.switching || call.ending,
                cascadedAvailable = availability.cascaded,
                semiCascadedAvailable = availability.semiCascaded,
                realtimeAvailable = availability.realtime,
                conversationTitle = if (callSnapshot.active) call.conversationTitle else callSnapshot.arguments.title,
                modelName = modelSettings.voiceModelName(displayedSelection).ifBlank { call.modelName },
                modelTemplateId = modelSettings.instances.resolveConnection(displayedSelection)?.templateId.orEmpty(),
                chatModelTemplateId = modelSettings.instances.resolveConnection(chatSelection)?.templateId.orEmpty(),
                chatModelName = modelSettings.voiceModelName(chatSelection),
                ttsModelName = call.ttsModelName,
                sttModelName = call.sttModelName,
                settingsLocked = call.configurationLocked,
                ending = call.ending,
                settingsSaving = saving,
                semiCascadedSupported = modelSettings.instances.resolveConnection(chatSelection)?.model?.isOmniLlm == true,
                draft = draftUi,
                settings = voiceSettingsState(settingsSnapshot),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), RealtimeVoiceUiState())

    /** 进入页面时登记会话绑定参数；对话覆盖按它观察，启动时按它绑定。 */
    fun prepare(conversationId: String?, selection: ModelSelection?, title: String, style: VoiceConversationStyle = VoiceConversationStyle.ASSISTANT) {
        startArguments.value = VoiceCallArguments(
            conversationId = conversationId?.takeIf(String::isNotBlank),
            selection = selection,
            title = title,
            style = style,
        )
        if (this.conversationId.value != conversationId) closeVoiceSettings()
        this.conversationId.value = conversationId?.takeIf(String::isNotBlank)
    }

    fun openVoiceSettings() {
        if (controller.state.value.configurationLocked) return
        val id = conversationId.value ?: return
        settingsOpen = true
        if (draft.value?.overrides?.conversationId == id) return
        val committed = overridesRepository.current(id)
        draft.value = VoiceConfigDraftState(committed, committed)
    }

    fun closeVoiceSettings() {
        settingsOpen = false
        // 已开始的保存应完成；尚缺模型的配置在收起后自然丢弃。
        if (!settingsSaving.value) draft.value = null
    }

    private fun editDraft(block: (ConversationVoiceOverrides) -> ConversationVoiceOverrides) {
        if (controller.state.value.configurationLocked || !settingsOpen) return
        val state = draft.value ?: return
        if (state.overrides.conversationId != conversationId.value) return
        val updated = block(state.overrides)
        if (updated == state.overrides) return
        draft.value = state.copy(overrides = updated, missing = emptySet())
        saveVoiceSettings()
    }

    /** 选择语音处理链路。 */
    fun selectPipeline(pipeline: VoicePipeline) {
        editDraft { it.copy(pipeline = VoiceFieldOverride.Value(pipeline)) }
    }

    /**
     * 选择链路模型；null 表示恢复继承（移除对话覆盖）。
     * 若目标链路仍缺必需模型，则和其余选择一起等到补齐再保存。
     */
    fun selectFieldModel(field: VoiceConfigField, selection: ModelSelection?) {
        val override = if (selection == null) VoiceFieldOverride.Inherit else VoiceFieldOverride.Value(selection)
        editDraft { overrides ->
            when (field) {
                VoiceConfigField.CASCADED_RECOGNITION_MODEL ->
                    overrides.copy(cascadedRecognitionModel = override)
                VoiceConfigField.CASCADED_SYNTHESIS_MODEL ->
                    overrides.copy(cascadedSynthesisModel = override)
                VoiceConfigField.SEMI_CASCADED_UNDERSTANDING_MODEL ->
                    overrides.copy(semiCascadedUnderstandingModel = override)
                VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL ->
                    overrides.copy(semiCascadedSynthesisModel = override)
                VoiceConfigField.REALTIME_MODEL -> overrides.copy(realtimeModel = override)
                else -> overrides
            }
        }
    }

    /** 快捷入口和语音设置共用同一音色保存流程；空白恢复继承。 */
    fun selectPipelineVoice(pipeline: VoicePipeline, voiceId: String?) {
        if (controller.state.value.configurationLocked) return
        val state = draft.value ?: return
        val config = VoiceConfigResolver.resolve(defaultsRepository.defaults.value, state.overrides, lookup)
        if (config.voiceModel(pipeline) == null) {
            notifyConfig(ConfigNotice.Problem.VoiceModelMissing)
            return
        }
        val override = if (voiceId.isNullOrBlank()) VoiceFieldOverride.Inherit else VoiceFieldOverride.Value(voiceId)
        editDraft {
            when (pipeline) {
                VoicePipeline.CASCADED -> it.copy(cascadedVoice = override)
                VoicePipeline.SEMI_CASCADED -> it.copy(semiCascadedVoice = override)
                VoicePipeline.NATIVE_REALTIME -> it.copy(realtimeVoice = override)
            }
        }
    }

    fun resetVoiceSettings() {
        editDraft { ConversationVoiceOverrides(it.conversationId) }
    }

    private fun saveVoiceSettings() {
        if (settingsSaving.value) return
        settingsSaving.value = true
        viewModelScope.launch {
            try {
                while (!controller.state.value.configurationLocked) {
                    val pending = draft.value?.takeIf { it.isDirty && it.overrides.conversationId == conversationId.value }
                        ?: break
                    val defaults = defaultsRepository.defaults.value
                    val result = try {
                        applyVoiceConfig(VoiceConfigDraft(pending.overrides, defaults))
                    } catch (_: IOException) {
                        notifyConfig(ConfigNotice.SaveFailed(pending.overrides.changedFields(pending.committed)))
                        restoreAfterSaveFailure(pending)
                        continue
                    }
                    val current = draft.value?.takeIf { it.overrides.conversationId == pending.overrides.conversationId }
                    when (result) {
                        VoiceApplyResult.Applied, VoiceApplyResult.Unchanged -> {
                            // IO 期间的新选择留给下一轮，已提交基线只推进到这次保存的快照。
                            if (current != null) {
                                draft.value = current.copy(committed = pending.overrides, missing = emptySet())
                            }
                        }
                        is VoiceApplyResult.Invalid -> if (current?.overrides == pending.overrides) {
                            draft.value = current.copy(missing = result.missing)
                            break
                        }
                        VoiceApplyResult.Conflict -> {
                            notifyConfig(ConfigNotice.Problem.Conflict)
                            restoreAfterSaveFailure(pending)
                        }
                    }
                }
            } finally {
                settingsSaving.value = false
                if (!settingsOpen) draft.value = null
            }
        }
    }

    private fun restoreAfterSaveFailure(pending: VoiceConfigDraftState) {
        val current = draft.value ?: return
        if (current.overrides == pending.overrides) {
            val saved = overridesRepository.current(pending.overrides.conversationId)
            draft.value = current.copy(overrides = saved, committed = saved, missing = emptySet())
        }
    }

    private fun EffectiveVoiceConfig.voiceModel(pipeline: VoicePipeline) = when (pipeline) {
        VoicePipeline.CASCADED -> cascadedSynthesisModel
        VoicePipeline.SEMI_CASCADED -> semiCascadedSynthesisModel
        VoicePipeline.NATIVE_REALTIME -> realtimeModel
    }

    /** 只在已保存配置完整时开始通话，避免保存与运行时配置读取交错。 */
    fun startCall() {
        if (controller.state.value.configurationLocked || settingsSaving.value || settingsOpen) return
        val arguments = startArguments.value
        val id = arguments.conversationId
        if (id == null || (arguments.style == VoiceConversationStyle.ASSISTANT && arguments.selection == null)) {
            notifyConfig(ConfigNotice.Problem.NoConversation)
            return
        }
        val config = VoiceConfigResolver.resolve(defaultsRepository.defaults.value, overridesRepository.current(id), lookup)
        if (config.missingRequired.isNotEmpty()) {
            notifyConfig(ConfigNotice.Problem.MissingRequired)
            return
        }
        composer.update { it.copy(error = null) }
        controller.start(id, arguments.selection, arguments.title, arguments.style, config.pipeline)
    }

    /** 换会话前等待控制器完整释放采集、生成与播放，避免新旧会话交错。 */
    fun stopForNavigation(onStopped: () -> Unit) {
        navigationJob?.cancel()
        closeVoiceSettings()
        controller.end()
        navigationJob = viewModelScope.launch {
            controller.state.first { !it.configurationLocked }
            fileUploads.clearAll()
            preparedAttachments.clear()
            composer.update { it.copy(
                draft = MessageData(), editing = false, submitting = false,
                uploads = emptyMap(), error = null,
            ) }
            settingsSaving.first { !it }
            onStopped()
        }
    }

    fun toggleMute() {
        if (!controller.state.value.active) {
            notifyInput(VoiceComposerError.StartCall)
            return
        }
        controller.toggleMute()
    }

    fun retry() = controller.retry()

    /** 页面挂断是唯一的页面结束入口，与状态栏结束汇合到同一个控制器清理链。 */
    fun end() = controller.end()
    fun consumeReturnToCall() = controller.consumeReturnToCall()

    /** 草稿展示态：字段生效值、继承/覆盖/未应用标记与缺项，全部来自草稿解析。 */
    private fun voiceDraftState(
        effective: EffectiveVoiceConfig,
        state: VoiceConfigDraftState?,
        committedOverrides: ConversationVoiceOverrides,
    ): VoiceDraftUiState {
        // 抽屉未打开时有效配置本来就是按已提交覆盖解析的，展示态必须同源，
        // 否则入口页的「对话内修改／全局默认」提示会与实际生效值不符。
        val overrides = state?.overrides ?: committedOverrides
        val committed = state?.committed ?: committedOverrides
        fun <T> field(
            pending: VoiceFieldOverride<T>,
            submitted: VoiceFieldOverride<T>,
            value: T,
            substituted: Boolean = false,
        ) = VoiceDraftFieldUi(
            value = value,
            overridden = pending.isOverridden,
            dirty = pending != submitted,
            substituted = substituted,
        )
        val dirtyFields = overrides.changedFields(committed)
        return VoiceDraftUiState(
            pipeline = field(overrides.pipeline, committed.pipeline, effective.pipeline),
            cascadedRecognitionModel = field(
                overrides.cascadedRecognitionModel, committed.cascadedRecognitionModel,
                effective.cascadedRecognitionModel,
            ),
            cascadedSynthesisModel = field(
                overrides.cascadedSynthesisModel, committed.cascadedSynthesisModel,
                effective.cascadedSynthesisModel,
            ),
            cascadedVoice = field(
                overrides.cascadedVoice, committed.cascadedVoice,
                effective.cascadedVoice.voiceId, effective.cascadedVoice.substituted,
            ),
            semiUnderstandingModel = field(
                overrides.semiCascadedUnderstandingModel, committed.semiCascadedUnderstandingModel,
                effective.semiCascadedUnderstandingModel,
            ),
            semiSynthesisModel = field(
                overrides.semiCascadedSynthesisModel, committed.semiCascadedSynthesisModel,
                effective.semiCascadedSynthesisModel,
            ),
            semiVoice = field(
                overrides.semiCascadedVoice, committed.semiCascadedVoice,
                effective.semiCascadedVoice.voiceId, effective.semiCascadedVoice.substituted,
            ),
            realtimeModel = field(
                overrides.realtimeModel, committed.realtimeModel, effective.realtimeModel,
            ),
            realtimeVoice = field(
                overrides.realtimeVoice, committed.realtimeVoice,
                effective.realtimeVoice.voiceId, effective.realtimeVoice.substituted,
            ),
            dirtyFields = dirtyFields,
            isDirty = dirtyFields.isNotEmpty(),
            hasAnyOverride = overrides.hasAnyOverride,
            // 应用被拒时的缺项优先展示，便于定位刚被拒绝的行；没有拒绝记录时
            // 回落到当前有效配置的实时缺项。
            missing = state?.missing?.takeIf { it.isNotEmpty() } ?: effective.missingRequired,
        )
    }

    /** 抽屉候选与摘要：候选模型行、随模型变化的音色选项。 */
    private fun DefaultModelSettings.voiceModelName(selection: ModelSelection?): String =
        instances.resolveConnection(selection)?.model?.name?.ifBlank { selection?.modelId.orEmpty() }
            ?: selection?.modelId.orEmpty()

    private fun ConversationVoiceOverrides.changedFields(committed: ConversationVoiceOverrides): Set<VoiceConfigField> = buildSet {
        if (pipeline != committed.pipeline) add(VoiceConfigField.PIPELINE)
        if (cascadedRecognitionModel != committed.cascadedRecognitionModel) add(VoiceConfigField.CASCADED_RECOGNITION_MODEL)
        if (cascadedSynthesisModel != committed.cascadedSynthesisModel) add(VoiceConfigField.CASCADED_SYNTHESIS_MODEL)
        if (cascadedVoice != committed.cascadedVoice) add(VoiceConfigField.CASCADED_VOICE)
        if (semiCascadedUnderstandingModel != committed.semiCascadedUnderstandingModel) add(VoiceConfigField.SEMI_CASCADED_UNDERSTANDING_MODEL)
        if (semiCascadedSynthesisModel != committed.semiCascadedSynthesisModel) add(VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL)
        if (semiCascadedVoice != committed.semiCascadedVoice) add(VoiceConfigField.SEMI_CASCADED_VOICE)
        if (realtimeModel != committed.realtimeModel) add(VoiceConfigField.REALTIME_MODEL)
        if (realtimeVoice != committed.realtimeVoice) add(VoiceConfigField.REALTIME_VOICE)
    }

    private fun voiceSettingsState(snapshot: VoiceSettingsSnapshot): VoiceSettingsUiState {
        val configuredInstances = snapshot.settings.instances.filter { it.isEnabled }
        val recognitionRows = configuredInstances.voiceModelRows(
            voiceCapabilities,
            predicate = { it.capabilities.any { capability -> capability.isRecognition } },
            requiredCapability = { it.recognitionCapability },
        )
        val synthesisRows = configuredInstances.voiceModelRows(
            voiceCapabilities,
            predicate = { ModelCapability.TTS in it.capabilities },
            requiredCapability = { ModelCapability.TTS },
        )
        // 半级联音频理解候选：模型自身声明音频输入能力；暂未接入具体业务，仅保留配置入口。
        val understandingRows = configuredInstances
            .flatMap { instance ->
                instance.connections.flatMap { connection ->
                    connection.models.filter { it.multimodal?.audioInput != null }.map { model ->
                        VoiceModelRow(instance = instance, connection = connection, model = model)
                    }
                }
            }
        // Realtime 候选只列已接入原生全双工适配器的模板：带 Realtime 标记但没有适配器的模型
        // 会静默回落到级联链路（而级联要求 STT/TTS 能力），选它只会得到起不来的会话。
        val realtimeRows = configuredInstances
            .filter { instance ->
                val templateId = instance.templateId
                templateId != null && voiceCapabilities.isSupported(templateId, ModelCapability.Realtime)
            }
            .flatMap { instance ->
                instance.connections.flatMap { connection ->
                    connection.models.filter { ModelCapability.Realtime in it.capabilities }.map { model ->
                        VoiceModelRow(
                            instance = instance,
                            connection = connection,
                            model = model,
                            capability = ModelCapability.Realtime,
                        )
                    }
                }
            }
        val realtimeSupport = snapshot.effective.realtimeModel
            ?.let { selection -> snapshot.settings.instances.resolveConnection(selection)?.templateId }
            ?.let { voiceCapabilities.realtimeSupport(it) }
        val synthesisSelection = if (snapshot.effective.pipeline == VoicePipeline.SEMI_CASCADED) {
            snapshot.effective.semiCascadedSynthesisModel
        } else {
            snapshot.effective.cascadedSynthesisModel
        }
        val synthesisTemplateId = synthesisSelection?.let { selection -> snapshot.settings.instances.resolveConnection(selection)?.templateId }
        return VoiceSettingsUiState(
            recognitionRows = recognitionRows,
            synthesisRows = synthesisRows,
            understandingRows = understandingRows,
            realtimeRows = realtimeRows,
            synthesisVoiceOptions = snapshot.synthesisVoiceOptions.ifEmpty {
                voiceCatalog.builtInVoices(synthesisTemplateId)
            },
            isSynthesisVoiceLoading = snapshot.isSynthesisVoiceLoading,
            synthesisVoiceCustomInputAllowed = voiceCatalog.allowsCustomVoice(synthesisTemplateId),
            synthesisVoiceIdTemplate = voiceCatalog.customVoiceIdTemplate(synthesisTemplateId),
            synthesisVoiceDocsUrl = voiceCatalog.customVoiceListUrl(synthesisTemplateId),
            realtimeVoiceOptions = snapshot.effective.realtimeModel
                ?.let { selection -> realtimeSupport?.voicesFor(selection.modelId) }
                .orEmpty(),
            realtimeCustomVoiceAllowed = realtimeSupport?.customVoiceAllowed == true,
            realtimeDefaultVoice = snapshot.effective.realtimeModel
                ?.let { selection -> realtimeSupport?.defaultVoiceFor(selection.modelId) }
                .orEmpty(),
        )
    }
}
