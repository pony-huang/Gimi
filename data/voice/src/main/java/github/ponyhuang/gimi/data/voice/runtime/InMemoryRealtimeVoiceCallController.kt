package github.ponyhuang.gimi.data.voice.runtime

import github.ponyhuang.gimi.data.voice.repository.resolveVoiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallForeground
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceConfiguration
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceMode
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSession
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.domain.voice.repository.isRealtimeEngine
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
 */
class InMemoryRealtimeVoiceCallController @Inject constructor(
    private val repository: RealtimeVoiceRepository,
    private val catalog: ModelCatalogRepository,
    private val capabilities: VoiceCapabilityResolver,
    private val foreground: RealtimeVoiceCallForeground,
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
    private var realtimeModelSelection: ModelSelection? = null
    private var realtimeVoiceId: String? = null
    private var generation = 0L

    /**
     * Realtime 可用性随默认选择与目录变化实时刷新：用户在设置页改完默认后回到通话页，
     * 模式菜单的可用状态必须跟着变，不能停留在通话开始那一刻的快照。
     */
    init {
        scope.launch {
            combine(catalog.observeInstances(), catalog.observeRealtimeSelection()) { _, selection -> selection }
                .collect {
                    val resolved = resolveRealtimeDefault()
                    mutableState.update { state ->
                        state.copy(
                            realtimeAvailable = resolved != null,
                            realtimeModelName = resolved?.first?.let { modelName(it) }.orEmpty(),
                        )
                    }
                }
        }
    }

    /** Realtime 引擎模式使用默认 Realtime 模型;级联模式沿用聊天模型选择。 */
    private fun selectionFor(mode: RealtimeVoiceMode): ModelSelection? =
        if (mode.isRealtimeEngine) realtimeModelSelection else targetModel

    override fun start(conversationId: String?, selection: ModelSelection?, title: String, mode: RealtimeVoiceMode) {
        val current = mutableState.value
        // 已有通话时只回到页面，不能再次抢占麦克风。
        if (current.active) return
        if (current.ending) {
            // 上一通仍在释放资源：排队到清理结束后再开新通话，避免新旧会话争抢麦克风，
            // 同时保证挂断时不会因为状态回写而意外重开一通。
            scope.launch {
                cleanupTask?.join()
                start(conversationId, selection, title, mode)
            }
            return
        }
        if (session != null || switchingTask?.isActive == true) return
        targetId = conversationId?.takeIf(String::isNotBlank)
        targetModel = selection
        val resolvedRealtime = resolveRealtimeDefault()
        // 懒启动：页面预选的模式在此落成配置。入口通常已引导配置；缺绑定的模式回落轻聊，
        // 避免带着坏配置起会话（RealtimeVoiceConfiguration 对 Chat/Realtime 有硬校验）。
        val startMode = when {
            mode == RealtimeVoiceMode.Chat && (targetId == null || targetModel == null) -> RealtimeVoiceMode.Simple
            mode.isRealtimeEngine && resolvedRealtime == null -> RealtimeVoiceMode.Simple
            else -> mode
        }
        if (startMode.isRealtimeEngine) {
            realtimeModelSelection = resolvedRealtime?.first
            realtimeVoiceId = resolvedRealtime?.second
        }
        mutableState.value = RealtimeVoiceCallState(
            active = true,
            chatAvailable = targetId != null && targetModel != null,
            realtimeAvailable = resolvedRealtime != null,
            realtimeModelName = resolvedRealtime?.first?.let { modelName(it) }.orEmpty(),
            conversationTitle = title,
        )
        // 界面可见时先建立前台优先级，后台阶段才能继续合法持有麦克风。
        foreground.start()
        create(startMode)
    }

    override fun ensureForeground() {
        if (mutableState.value.active) foreground.start()
    }

    private fun create(mode: RealtimeVoiceMode) {
        val token = ++generation
        val selection = selectionFor(mode)
        val active = repository.createSession(
            RealtimeVoiceConfiguration(
                mode = mode,
                conversationId = if (mode == RealtimeVoiceMode.Chat) targetId else null,
                modelSelection = selection,
                // 自定义音色随会话固定；null 表示用所选模型在该厂商下的缺省音色。
                realtimeVoice = if (mode.isRealtimeEngine) realtimeVoiceId else null,
            ),
        )
        session = active
        mutableState.update { it.copy(voice = active.state.value, mode = mode, switching = false) }
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
                ttsModelName = modelName(catalog.currentTtsSelection()),
                sttModelName = modelName(catalog.currentSpeechSelection()),
            ) }
            active.start()
        }
    }

    override fun selectMode(mode: RealtimeVoiceMode) {
        val active = session ?: return
        val current = mutableState.value
        if (mode == current.mode || current.switching ||
            (mode == RealtimeVoiceMode.Chat && !current.chatAvailable) ||
            (mode.isRealtimeEngine && !current.realtimeAvailable) ||
            current.voice.failure == RealtimeVoiceFailure.Persistence) return
        switchTo(mode)
    }

    private fun switchTo(mode: RealtimeVoiceMode) {
        val active = session ?: return
        val token = ++generation
        mutableState.update { it.copy(switching = true) }
        switchingTask = scope.launch {
            startup?.cancelAndJoin()
            if (mode.isRealtimeEngine) {
                catalog.awaitReady()
                // Realtime 模型只能来自默认模型页；未配置或已失效时不进入该模式。
                val resolved = resolveRealtimeDefault()
                if (resolved == null) {
                    if (session === active) mutableState.update { it.copy(switching = false) }
                    return@launch
                }
                realtimeModelSelection = resolved.first
                realtimeVoiceId = resolved.second
            }
            if (!active.finishPendingTurn()) {
                if (session === active) mutableState.update { it.copy(switching = false) }
                return@launch
            }
            observation?.cancel()
            active.close()
            if (generation != token || session !== active) return@launch
            session = null
            create(mode)
        }
    }

    /**
     * 解析当前生效的 Realtime 默认选择与其音色覆盖。
     *
     * 默认选择失效（服务被删、密钥被清、模型 ID 变更、服务未接入原生 Realtime 适配器）时
     * 返回 null——此时 Realtime 模式不可进入，而不是带着坏配置起会话。
     *
     * @return 模型选择与自定义音色 ID；未自定义音色时第二项为 null。
     */
    private fun resolveRealtimeDefault(): Pair<ModelSelection, String?>? {
        val selection = catalog.currentRealtimeSelection() ?: return null
        val resolved = (catalog.resolveVoiceConnection(selection) { ModelCapability.Realtime in it.capabilities })
            ?: return null
        // 未接入原生 Realtime 适配器的模板没有全双工契约，不可进入 Realtime 模式。
        val templateId = resolved.templateId ?: return null
        if (!capabilities.isSupported(templateId, ModelCapability.Realtime)) return null
        // 音色覆盖绑定在具体模型上：换模型后旧覆盖不再适用，回落到模型缺省音色。
        val voiceId = catalog.currentRealtimeVoice()?.takeIf { it.selection == selection }?.voiceId
        return selection to voiceId
    }

    override fun toggleMute() {
        val current = mutableState.value
        if (!current.active || current.switching) return
        session?.setMuted(!current.voice.muted)
    }

    override fun selectAudioDevice(deviceId: Int?) { session?.selectAudioDevice(deviceId) }

    override fun retry() {
        if (mutableState.value.switching || startup?.isActive == true) return
        val active = session ?: return
        startup = scope.launch { active.retry() }
    }

    override fun end() {
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
            voice = RealtimeVoiceState(),
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
                    mutableState.value = RealtimeVoiceCallState()
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
