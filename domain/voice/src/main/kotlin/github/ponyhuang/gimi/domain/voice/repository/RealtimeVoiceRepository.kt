package github.ponyhuang.gimi.domain.voice.repository

import kotlinx.coroutines.flow.StateFlow
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection

/**
 * 语音模式。
 *
 * - [Simple]：简单聊天（对话模式），级联链路 + 内存上下文，无工具、无持久化。
 * - [Chat]：完整聊天，接入完整 chat agent（工具、持久化与文字聊天一致）。
 * - [Realtime]：原生全双工实时语音（实时轻聊），常驻会话、服务端判轮，仅支持 Realtime 类型服务；
 *   无工具、无持久化，转写只进字幕。
 * - [RealtimeAssistant]：实时助手，与 [Realtime] 同引擎，另接入可执行工具桥
 *   （本地/MCP/插件/应用函数等授权工具；确认类工具在语音通话中自动批准，与 Chat 模式一致）；
 *   无持久化，转写只进字幕。
 */
enum class RealtimeVoiceMode { Simple, Chat, Realtime, RealtimeAssistant }

/** 是否由 Realtime 引擎承载（原生全双工、仅支持 Realtime 类型服务）；实时轻聊与实时助手共用。 */
val RealtimeVoiceMode.isRealtimeEngine: Boolean
    get() = this == RealtimeVoiceMode.Realtime || this == RealtimeVoiceMode.RealtimeAssistant

/**
 * 单次通话的固定配置；聊天模式必须显式绑定已有会话及其模型，不能隐式新建聊天。
 * @property conversationId 已打开的聊天 ID，简单聊天不使用它。
 * @property modelSelection 当前聊天的模型选择，通话期间保持不变。
 * @property realtimeVoice Realtime 模式的自定义会话音色；null 表示使用所选模型在该厂商下的缺省音色。
 *   音色与模型强绑定（跨代际的音色集互不相同），因此覆盖值由调用方按当前模型解析后传入。
 */
data class RealtimeVoiceConfiguration(
    val mode: RealtimeVoiceMode = RealtimeVoiceMode.Simple,
    val conversationId: String? = null,
    val modelSelection: ModelSelection? = null,
    val realtimeVoice: String? = null,
) {
    init {
        require(mode != RealtimeVoiceMode.Chat || (!conversationId.isNullOrBlank() && modelSelection != null))
        require(!mode.isRealtimeEngine || modelSelection != null) {
            "Realtime 模式必须显式选择 Realtime 服务模型"
        }
    }
}

/**
 * 创建实时语音会话。简单聊天无工具、无持久化；聊天模式直接接入完整 chat agent
 * （工具、技能与文字聊天一致），工具确认在语音通话中全部自动批准。
 */
interface RealtimeVoiceRepository {
    fun createSession(configuration: RealtimeVoiceConfiguration = RealtimeVoiceConfiguration()): RealtimeVoiceSession
}

/**
 * 一次实时语音会话的所有权。会话由进程级通话控制器持有，离开页面或进入后台都不得调用 [close]；
 * 只有用户从语音页或状态栏显式结束通话时才释放资源。
 * 录音权限由界面入口取得；实现负责取消网络、停止录放音并清空内存上下文。
 */
interface RealtimeVoiceSession {
    val state: StateFlow<RealtimeVoiceState>

    suspend fun start()
    fun setMuted(muted: Boolean)
    /** null 恢复自动路由，非空 id 仅对当前会话有效。 */
    fun selectAudioDevice(deviceId: Int?)
    suspend fun retry()
    /** 停止收音、生成及播放，并等待文字提交；失败时保留会话以供重试，禁止切换。 */
    suspend fun finishPendingTurn(): Boolean
    suspend fun close()
}

/** 播报与推理的阶段；监听状态独立保存，因为播放时仍需收音以支持打断。 */
enum class RealtimeVoicePhase { Idle, Listening, Transcribing, Thinking, Speaking, Failed, Closed }

/** 可翻译的失败分类；界面将分类映射到所属模块资源，不直接显示底层异常。 */
enum class RealtimeVoiceFailure { Configuration, Microphone, AudioProcessing, Recognition, Generation, Synthesis, Playback, Persistence, ConversationBusy }

/** 通话输出设备类别；输入设备由平台匹配，无麦耳机回退到手机麦克风。 */
enum class VoiceAudioDeviceKind { Speaker, Earpiece, Bluetooth, Wired, Usb, Other }

/** 可供当前会话选择的设备，id 仅在本次设备连接期间有效，name 为系统设备名称。 */
data class VoiceAudioDevice(val id: Int, val kind: VoiceAudioDeviceKind, val name: String)

/**
 * 通话路由快照；currentDeviceId 是平台确认的实际输出，preferredDeviceId 为手动选择（null 表示自动）。
 * @property noticeRevision 每次断开或切换失败递增，避免同类连续通知丢失。
 * @property selectionFailed 最近一次通知是否为切换失败，否则为设备断开。
 */
data class VoiceAudioRouting(
    val devices: List<VoiceAudioDevice> = emptyList(),
    val currentDeviceId: Int? = null,
    val preferredDeviceId: Int? = null,
    val switching: Boolean = false,
    val noticeRevision: Long = 0,
    val selectionFailed: Boolean = false,
)

/**
 * 当前前台语音会话的不可变快照。
 * @property listening 麦克风是否正在接收可用于检测和识别的音频。
 * @property muted 用户是否主动暂停输入；不代表结束会话或丢弃上下文。
 * @property inputText 最近一次有效识别的用户话语。
 * @property responseText 当前回答的文本，可能包含尚未播出的部分。
 * @property spokenText 已经完整播完的句子，供打断后的上下文对齐使用。
 * @property speakingText 正在实际播放的句子；不包含排队等待的片段。
 * @property inputLevel 归一化后的麦克风电平，仅用于可视反馈。
 * @property outputLevel 正在输出的 PCM 音频电平，归一化后用于播放反馈。
 * @property revision 当前话轮代次，旧代次的异步结果必须丢弃。
 * @property audioRouting 实际通话设备、用户偏好与路由提示状态。
 */
data class RealtimeVoiceState(
    val phase: RealtimeVoicePhase = RealtimeVoicePhase.Idle,
    val listening: Boolean = false,
    val muted: Boolean = false,
    val inputText: String = "",
    val responseText: String = "",
    val spokenText: String = "",
    val speakingText: String = "",
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val revision: Long = 0,
    val failure: RealtimeVoiceFailure? = null,
    val audioRouting: VoiceAudioRouting = VoiceAudioRouting(),
    /** 断线重连已发生的提示位；下一轮提交时清除，界面据此展示「已重新连接」。 */
    val reconnectNotice: Boolean = false,
    /** 正在执行的工具名（实时助手模式）；工具执行完成或新一轮开始时清除。 */
    val toolNotice: String? = null,
)
