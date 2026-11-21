package github.ponyhuang.gimi.data.voice.runtime

import github.ponyhuang.gimi.data.voice.repository.resolveVoiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.isOmniLlm
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.ConversationVoiceOverrides
import github.ponyhuang.gimi.domain.voice.repository.EffectiveVoiceConfig
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallForeground
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceConfiguration
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSession
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigLookup
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceConversationStyle
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaultsRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceInputResult
import github.ponyhuang.gimi.domain.voice.repository.VoiceManualInput
import github.ponyhuang.gimi.domain.voice.repository.VoiceOverrideRepository
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 进程级通话所有者：会话、状态收集与清理链都挂在应用级协程上，
 * 因此页面销毁、前后台切换和导航跳转都不会取消录音、生成或播放。
 * 对话方式与语音处理链路两个维度按有效配置解析；级联助手缺会话绑定、
 * 原生实时缺有效模型时回落级联 Chatbot。
 */
class InMemoryRealtimeVoiceCallController @Inject constructor(
    private val repository: RealtimeVoiceRepository,
    private val catalog: ModelCatalogRepository,
    private val capabilities: VoiceCapabilityResolver,
    private val foreground: RealtimeVoiceCallForeground,
    private val defaultsRepository: VoiceGlobalDefaultsRepository,
    private val overridesRepository: VoiceOverrideRepository,
    private val lookup: VoiceConfigLookup,
) : RealtimeVoiceCallController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(RealtimeVoiceCallState())
    override val state: StateFlow<RealtimeVoiceCallState> = mutableState.asStateFlow()
    private var session: RealtimeVoiceSession? = null
    private var observation: Job? = null
    private var startup: Job? = null
    private var switchingTask: Job? = null
    private var cleanupTask: Job? = null
    private var targetId: String? = null
    private var targetModel: ModelSelection? = null
    private var generation = 0L
    private var submitting = false

    /**
     * 可用性随全局默认与目录变化实时刷新：用户在设置页改完默认后回到通话页，
     * 链路菜单的可用状态必须跟着变，不能停留在通话开始那一刻的快照。
     */
    init {
        scope.launch {
            combine(defaultsRepository.defaults, catalog.observeInstances()) { _, _ -> }
                .collect {
                    val config = resolveEffective()
                    mutableState.update { state ->
                        state.copy(
                            cascadedAvailable = cascadedAvailableFor(config),
                            semiCascadedAvailable = semiCascadedAvailableFor(config),
                            realtimeAvailable = realtimeAvailableFor(config),
                            realtimeModelName = config.realtimeModel?.let { modelName(it) }.orEmpty(),
                        )
                    }
                }
        }
    }

    /** 解析当前目标会话的有效语音配置；无会话绑定（Chatbot 直达）时不读对话覆盖。 */
    private fun resolveEffective(): EffectiveVoiceConfig {
        val overrides = targetId?.let { overridesRepository.current(it) } ?: ConversationVoiceOverrides("")
        return VoiceConfigResolver.resolve(defaultsRepository.defaults.value, overrides, lookup)
    }

    private fun isModelValid(selection: ModelSelection?): Boolean =
        selection != null && lookup.isModelValid(selection)

    /** 级联合成链路的识别与合成默认模型是否都有效。 */
    private fun cascadedAvailableFor(config: EffectiveVoiceConfig): Boolean =
        isModelValid(config.cascadedRecognitionModel) && isModelValid(config.cascadedSynthesisModel)

    /** 半级联链路可用：合成模型有效，且当前选中的对话模型是全模态 LLM。 */
    private fun semiCascadedAvailableFor(
        config: EffectiveVoiceConfig,
        modelSelection: ModelSelection? = targetModel,
    ): Boolean {
        if (!isModelValid(config.semiCascadedSynthesisModel)) return false
        val chatModel = modelSelection ?: catalog.currentAssistantSelection() ?: return false
        val resolved = catalog.resolveConnection(chatModel) ?: return false
        return resolved.model.isOmniLlm
    }

    /** 只有已配置并具备双工适配器的 Realtime 模型才能启动原生实时。 */
    private fun realtimeAvailableFor(config: EffectiveVoiceConfig): Boolean {
        val selection = config.realtimeModel ?: return false
        val resolved = catalog.resolveVoiceConnection(selection) { ModelCapability.Realtime in it.capabilities }
            ?: return false
        val templateId = resolved.templateId ?: return false
        return capabilities.isSupported(templateId, ModelCapability.Realtime)
    }

    override fun start(
        conversationId: String?,
        selection: ModelSelection?,
        title: String,
        style: VoiceConversationStyle,
        pipeline: VoicePipeline,
    ) {
        if (style != VoiceConversationStyle.CHATBOT) return
        val current = mutableState.value
        // 已有通话时只回到页面，不能再次抢占麦克风。
        if (current.active) return
        if (current.ending) {
            // 上一通仍在释放资源：排队到清理结束后再开新通话，避免新旧会话争抢麦克风，
            // 同时保证挂断时不会因为状态回写而意外重开一通。
            scope.launch {
                cleanupTask?.join()
                start(conversationId, selection, title, style, pipeline)
            }
            return
        }
        if (session != null || switchingTask?.isActive == true) return
        targetId = conversationId?.takeIf(String::isNotBlank)
        targetModel = selection
        val config = resolveEffective()
        val semiAvailable = semiCascadedAvailableFor(config, selection)
        // 懒启动：页面预选的配置在此落成会话。入口通常已引导配置；缺绑定或缺模型的
        // 组合回落级联 Chatbot，避免带着坏配置起会话（RealtimeVoiceConfiguration 有硬校验）。
        val startPipeline = when (pipeline) {
            VoicePipeline.SEMI_CASCADED -> if (semiAvailable) pipeline else VoicePipeline.CASCADED
            VoicePipeline.NATIVE_REALTIME -> if (realtimeAvailableFor(config)) pipeline else VoicePipeline.CASCADED
            else -> pipeline
        }
        mutableState.value = RealtimeVoiceCallState(
            active = true,
            style = VoiceConversationStyle.CHATBOT,
            pipeline = startPipeline,
            cascadedAvailable = cascadedAvailableFor(config),
            semiCascadedAvailable = semiAvailable,
            realtimeAvailable = realtimeAvailableFor(config),
            realtimeModelName = config.realtimeModel?.let { modelName(it) }.orEmpty(),
            conversationTitle = title,
        )
        // 界面可见时先建立前台优先级，后台阶段才能继续合法持有麦克风。
        foreground.start()
        create(startPipeline, config)
    }

    override fun ensureForeground() {
        if (mutableState.value.active) foreground.start()
    }

    /**
     * 用调用方已经解析好的有效配置建会话，避免二次解析引入 TOCTOU：
     * 两次解析之间模型失效时不再抛异常，而是退回可用的级联链路。
     */
    private fun create(
        pipeline: VoicePipeline,
        config: EffectiveVoiceConfig,
    ) {
        val token = ++generation
        val startPipeline = when {
            pipeline == VoicePipeline.NATIVE_REALTIME && config.realtimeModel == null -> VoicePipeline.CASCADED
            pipeline == VoicePipeline.SEMI_CASCADED && !semiCascadedAvailableFor(config) -> VoicePipeline.CASCADED
            else -> pipeline
        }
        val configuration = when (startPipeline) {
            VoicePipeline.NATIVE_REALTIME -> {
                val realtimeModel = requireNotNull(config.realtimeModel) { "原生实时缺少有效模型" }
                RealtimeVoiceConfiguration(
                    style = VoiceConversationStyle.CHATBOT,
                    pipeline = startPipeline,
                    conversationId = targetId,
                    modelSelection = realtimeModel,
                    // 自定义音色随会话固定；null 表示用所选模型在该厂商下的缺省音色。
                    realtimeVoice = config.realtimeVoice.voiceId,
                )
            }
            VoicePipeline.SEMI_CASCADED -> {
                val cascadedModel = targetModel ?: catalog.currentAssistantSelection()
                RealtimeVoiceConfiguration(
                    style = VoiceConversationStyle.CHATBOT,
                    pipeline = startPipeline,
                    conversationId = targetId,
                    modelSelection = cascadedModel,
                    synthesisVoice = config.semiCascadedVoice.voiceId,
                    synthesisModel = config.semiCascadedSynthesisModel,
                )
            }
            else -> {
                // 级联：对话模型来自会话绑定；Chatbot 直达时回退到当前助手默认模型。
                val cascadedModel = targetModel ?: catalog.currentAssistantSelection()
                RealtimeVoiceConfiguration(
                    style = VoiceConversationStyle.CHATBOT,
                    pipeline = startPipeline,
                    conversationId = targetId,
                    modelSelection = cascadedModel,
                    // 级联合成音色的对话覆盖；null 表示沿用全局合成音色或厂商默认。
                    synthesisVoice = config.cascadedVoice.voiceId,
                )
            }
        }
        val selection = configuration.modelSelection
        val active = repository.createSession(configuration)
        session = active
        mutableState.update {
            it.copy(
                callId = token,
                voice = active.state.value,
                style = VoiceConversationStyle.CHATBOT,
                pipeline = startPipeline,
                modelSelection = selection,
                switching = false,
            )
        }
        observation = scope.launch {
            active.state.collect { voice ->
                if (session === active) mutableState.update { it.copy(voice = voice) }
            }
        }
        val previousCleanup = cleanupTask
        startup = scope.launch {
            // 快速往返页面时，旧会话释放完毕前不得让新会话抢到麦克风。
            previousCleanup?.join()
            catalog.awaitReady()
            if (generation != token || session !== active) return@launch
            mutableState.update { it.copy(
                modelName = modelName(selection).ifBlank { selection?.modelId.orEmpty() },
                ttsModelName = modelName(configuration.synthesisModel ?: catalog.currentTtsSelection()),
                sttModelName = modelName(catalog.currentSpeechSelection()),
            ) }
            active.start()
            if (session === active && active.state.value.phase == RealtimeVoicePhase.Failed) {
                // 启动失败仍须释放麦克风和路由；清理后回到待机，并保留失败原因供页面提示。
                end(active.state.value.failure)
            }
        }
    }

    override fun selectConfiguration(style: VoiceConversationStyle, pipeline: VoicePipeline) {
        if (style != VoiceConversationStyle.CHATBOT || session == null) return
        val current = mutableState.value
        val config = resolveEffective()
        if (pipeline == current.pipeline || current.switching ||
            (pipeline == VoicePipeline.SEMI_CASCADED && !semiCascadedAvailableFor(config)) ||
            (pipeline == VoicePipeline.NATIVE_REALTIME && !realtimeAvailableFor(config)) ||
            current.voice.failure == RealtimeVoiceFailure.Persistence
        ) return
        switchTo(pipeline)
    }

    override fun rebuild() {
        val current = mutableState.value
        if (!current.active || current.switching || current.voice.failure == RealtimeVoiceFailure.Persistence) return
        val config = resolveEffective()
        // 重建与手动切换使用同一套守卫：通话期间配置失效时保持原会话，
        // 不构造会被 RealtimeVoiceConfiguration 的 require 拒绝的组合。
        if ((current.pipeline == VoicePipeline.SEMI_CASCADED && !semiCascadedAvailableFor(config)) ||
            (current.pipeline == VoicePipeline.NATIVE_REALTIME &&
                (!realtimeAvailableFor(config) || config.realtimeModel == null)) ||
            (current.pipeline == VoicePipeline.CASCADED && !cascadedAvailableFor(config))
        ) return
        switchTo(current.pipeline)
    }

    private fun switchTo(pipeline: VoicePipeline) {
        val active = session ?: return
        val token = ++generation
        mutableState.update { it.copy(switching = true) }
        switchingTask = scope.launch {
            startup?.cancelAndJoin()
            // 切换时重新解析有效配置：全局默认可能刚在设置页更新。
            val config = resolveEffective()
            if (pipeline == VoicePipeline.NATIVE_REALTIME && !realtimeAvailableFor(config)) {
                // 原生实时模型只能来自语音设置；未配置或已失效时不进入该链路。
                if (session === active) mutableState.update { it.copy(switching = false) }
                return@launch
            }
            if (pipeline == VoicePipeline.SEMI_CASCADED && !semiCascadedAvailableFor(config)) {
                if (session === active) mutableState.update { it.copy(switching = false) }
                return@launch
            }
            if (!active.finishPendingTurn()) {
                if (session === active) mutableState.update { it.copy(switching = false) }
                return@launch
            }
            observation?.cancel()
            active.close()
            if (generation != token || session !== active) return@launch
            session = null
            create(pipeline, config)
        }
    }

    override fun toggleMute() {
        val current = mutableState.value
        if (!current.active || current.switching) return
        session?.setMuted(!current.voice.muted)
    }

    override fun setInputPaused(paused: Boolean) { session?.setInputPaused(paused) }

    override suspend fun submit(input: VoiceManualInput): VoiceInputResult =
        withContext(Dispatchers.Main.immediate) {
            val active = session
            if (active == null || submitting || !mutableState.value.active || mutableState.value.ending || mutableState.value.switching) {
                return@withContext VoiceInputResult.Unavailable
            }
            submitting = true
            try {
                active.submit(input)
            } finally {
                submitting = false
            }
        }

    override fun retry() {
        if (mutableState.value.switching || startup?.isActive == true) return
        val active = session ?: return
        startup = scope.launch { active.retry() }
    }

    override fun end() = end(startupFailure = null)

    private fun end(startupFailure: RealtimeVoiceFailure?) {
        val current = mutableState.value
        // 幂等：页面挂断与状态栏结束可能同时到达，只允许第一条清理链执行。
        if (!current.active || current.ending) return
        generation++
        val active = session
        session = null
        startup?.cancel()
        observation?.cancel()
        val transition = switchingTask
        transition?.cancel()
        switchingTask = null
        mutableState.value = current.copy(
            active = false,
            ending = true,
            voice = RealtimeVoiceState(failure = startupFailure),
            switching = false,
            returnToCallRequested = false,
        )
        // ending 一直保持到 close() 返回：前台服务与 WakeLock 必须覆盖整个资源释放过程，
        // 否则从状态栏结束时进程可能在清理完成前失去前台优先级。
        val previousCleanup = cleanupTask
        cleanupTask = scope.launch {
            // 清理内容不可取消：退出页面或新会话启动都不能截断旧麦克风所有权的释放。
            withContext(NonCancellable) {
                try {
                    previousCleanup?.join()
                    transition?.join()
                    // 收尾异常也不能阻断状态同步；页面与通知必须一致地回到「无通话」。
                    active?.let { runCatching { it.close() } }
                } finally {
                    // 可用性属于环境能力而非通话状态：挂断复位时保留它，
                    // 否则待启动页面会在清理结束后把全部链路误显示为不可用。
                    val availability = mutableState.value
                    mutableState.value = RealtimeVoiceCallState(
                        voice = RealtimeVoiceState(failure = startupFailure),
                        cascadedAvailable = availability.cascadedAvailable,
                        semiCascadedAvailable = availability.semiCascadedAvailable,
                        realtimeAvailable = availability.realtimeAvailable,
                        realtimeModelName = availability.realtimeModelName,
                    )
                }
            }
        }
    }

    override fun requestReturnToCall() {
        if (mutableState.value.active) mutableState.update { it.copy(returnToCallRequested = true) }
    }

    override fun consumeReturnToCall() {
        mutableState.update { it.copy(returnToCallRequested = false) }
    }

    private fun modelName(selection: ModelSelection?): String {
        selection ?: return ""
        val model = catalog.resolveConnection(selection)?.model ?: return ""
        return model.name.ifBlank { model.id }
    }
}
