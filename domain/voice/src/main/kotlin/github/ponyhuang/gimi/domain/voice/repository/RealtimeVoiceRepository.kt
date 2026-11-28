package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.flow.StateFlow

/**
 * 单次通话的固定配置；对话方式与语音处理链路两个正交维度分别表达。
 * 级联助手必须显式绑定已有会话及其模型，不能隐式新建聊天；原生实时必须
 * 显式选择 Realtime 服务模型；半级联已经接入 Chatbot 模式，必须显式绑定
 * 全模态理解模型，并在通话中由配置补齐合成模型/音色。
 * @property style 对话方式（聊天／助手）。
 * @property pipeline 语音处理链路（级联／半级联／原生实时）。
 * @property conversationId 已打开的聊天 ID，级联链路共用它，Chatbot 为空时解析当前会话。
 * @property modelSelection 级联链路的对话模型（当前聊天的模型选择），通话期间保持不变；
 *   原生实时链路为其实时模型。
 * @property realtimeVoice 原生实时链路的自定义会话音色；null 表示使用所选模型在该厂商下的缺省音色。
 *   音色与模型强绑定（跨代际的音色集互不相同），因此覆盖值由调用方按当前模型解析后传入。
 * @property synthesisVoice 会话合成音色；null 表示使用所选模型适用的全局音色或厂商默认。
 * @property synthesisModel 级联与半级联使用的合成模型；null 表示使用全局合成模型。
 * @property recognitionModel 级联使用的识别模型；null 表示使用全局识别模型。
 */
data class RealtimeVoiceConfiguration(
    val style: VoiceConversationStyle = VoiceConversationStyle.CHATBOT,
    val pipeline: VoicePipeline = VoicePipeline.CASCADED,
    val conversationId: String? = null,
    val modelSelection: ModelSelection? = null,
    val realtimeVoice: String? = null,
    val synthesisVoice: String? = null,
    val synthesisModel: ModelSelection? = null,
    val recognitionModel: ModelSelection? = null,
) {
    init {
        require(style != VoiceConversationStyle.ASSISTANT || pipeline != VoicePipeline.CASCADED ||
            (!conversationId.isNullOrBlank() && modelSelection != null)) {
            "级联助手必须显式绑定会话与模型"
        }
        require(pipeline != VoicePipeline.NATIVE_REALTIME || modelSelection != null) {
            "原生实时必须显式选择 Realtime 服务模型"
        }
        require(pipeline != VoicePipeline.SEMI_CASCADED || (modelSelection != null && style == VoiceConversationStyle.CHATBOT)) {
            "半级联必须显式绑定模型且仅支持 Chatbot 模式"
        }
    }
}

/**
 * 创建实时语音会话。级联聊天保存当前会话历史，无工具；级联助手直接接入完整
 * chat agent（工具、技能与文字聊天一致），工具确认在语音通话中全部自动批准；
 * 原生实时链路由全双工会话承载；半级联由理解模型生成文本、客户端合成播放。
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
    /** 编辑暂停与用户静音独立，恢复编辑不会覆盖用户静音选择。 */
    fun setInputPaused(paused: Boolean)
    suspend fun submit(input: VoiceManualInput): VoiceInputResult
    suspend fun retry()
    /** 停止收音、生成及播放，并等待文字提交；失败时保留会话以供重试，禁止切换。 */
    suspend fun finishPendingTurn(): Boolean
    suspend fun close()
}

/** 播报与推理的阶段；监听状态独立保存，因为播放时仍需收音以支持打断。 */
enum class RealtimeVoicePhase { Idle, Listening, Transcribing, Thinking, Speaking, Failed, Closed }

/** 可翻译的失败分类；界面将分类映射到所属模块资源。 */
enum class RealtimeVoiceFailure { Configuration, Microphone, AudioProcessing, Recognition, Generation, Synthesis, Playback, Persistence, ConversationBusy }

/** 一次通话失败的提示实例；相同错误在显式重试后也可再次提示。 */
class RealtimeVoiceFailureNotice(val requestError: VoiceRequestError? = null)

/** 通话输出设备类别；输入设备由平台匹配，无麦耳机回退到手机麦克风。 */
enum class VoiceAudioDeviceKind { Speaker, Earpiece, Bluetooth, Wired, Usb, Other }

/** 系统枚举出的通话输出设备，id 仅在本次设备连接期间有效，name 为系统设备名称。 */
data class VoiceAudioDevice(val id: Int, val kind: VoiceAudioDeviceKind, val name: String)

/**
 * 通话路由快照；通话设备固定自动路由，currentDeviceId 是平台确认的实际输出。
 * @property noticeRevision 每次断开或切换失败递增，避免同类连续通知丢失。
 * @property selectionFailed 最近一次通知是否为切换失败，否则为设备断开。
 */
data class VoiceAudioRouting(
    val devices: List<VoiceAudioDevice> = emptyList(),
    val currentDeviceId: Int? = null,
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
 * @property audioRouting 实际通话设备与路由提示状态。
 */
data class RealtimeVoiceState(
    val phase: RealtimeVoicePhase = RealtimeVoicePhase.Idle,
    val listening: Boolean = false,
    val muted: Boolean = false,
    /** 编辑或选择附件期间临时阻止上行，保持当前播报。 */
    val inputPaused: Boolean = false,
    /** 最近一轮用户输入携带的已归档附件。 */
    val inputAttachments: List<FileAttachment> = emptyList(),
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
    val failureNotice: RealtimeVoiceFailureNotice? = null,
)
