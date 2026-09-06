package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.UserInputKind
import github.ponyhuang.gimi.domain.conversation.model.UserInputRequest
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.mcp.model.McpServer

/**
 * The current UI state of the chat conversation.
 *
 * 由 [ChatViewModel] 持有并以 `StateFlow<ChatUiState>` 暴露给 UI；Composable
 * 一次 collect 后用字段访问即可获得全部子状态，避免多个 `StateFlow` 各自订阅
 * 造成的重复 collect。所有字段均有默认值，既方便作为 `stateIn` 的初始值，
 * 也方便 `@Preview` 桩与测试 override。
 *
 * @param messages 已渲染的消息列表（含 partial / 工具调用 / 错误消息）
 * @param sessionId 当前激活的会话 id；空串表示还没建立会话
 * @param isAgentRunning Agent turn 是否仍在进行（包括思考、流式输出和工具执行）。
 *                       用于显示思考/停止状态并锁定会话级操作。
 * @param turnComplete 当前 turn 是否收到过 `event.turnComplete = true` 的收尾事件。
 *                     与 [isAgentRunning] 的差异：`isAgentRunning` 在收到工具调用响应、
 *                     仍可能继续产流的事件时会保持 true；而 [turnComplete] 仅在
 *                     `Event.turnComplete = true` 的最终事件到达时翻为 true。
 *                     当前没有 UI 消费方，预留给 "Turn complete" 提示 chip / 状态徽章。
 * @param conversations 会话列表 — 直接转发自 `ConversationRepository.conversations`，
 *                     仅供 [HistoryDrawer] 渲染。
 * @param isInitializing 当前是否处于会话/历史初始化阶段 — 启动期
 *                     [ChatViewModel.restoreOrCreateSession] 或用户切换会话触发的
 *                     [ChatViewModel.switchSession] 在异步读 Room 期间为 true，
 *                     commit 完成或兜底分支结束置 false。驱动 [ChatRoute] 中
 *                     `AnimatedContent` 的中央 spinner —— 让用户在历史"灌入"前
 *                     看到一个加载态，避免旧内容残留闪烁。
 *
 *                     与 [isAgentRunning] 正交：[isAgentRunning] 描述 Agent turn；
 *                     [isInitializing] 只用于中央 spinner 的可见性。两者各自承担
 *                     一段独立的语义，不要混用。
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val sessionId: String = "",
    val isAgentRunning: Boolean = false,
    val lastSendFailed: Boolean = false,
    val turnComplete: Boolean = false,
    /**
     * 可恢复的最近失败/中断发送轮；非空时在错误区域显示“编辑/重试”。
     * 一旦用户成功发送新消息或完成当前轮，该字段被清空。
     */
    val failedTurn: ChatTurn? = null,
    /** 输入框当前是否处于“编辑失败消息”状态。 */
    val editingFailedTurn: Boolean = false,
    /** 是否展示“重新发送可能重复执行工具操作”确认对话框。 */
    val toolReexecutionPending: Boolean = false,
    /** 输入框的外部草稿种子；编辑失败消息时用它回填文字与附件。 */
    val composerSeed: MessageData = MessageData(),
    val conversations: List<Conversation> = emptyList(),
    val conversationTaskStatuses: Map<String, ConversationTaskStatus> = emptyMap(),
    val isInitializing: Boolean = false,
    val availableLLMModelSettings: List<LLMModelSetting> = emptyList(),
    val modelCatalogLoadState: CatalogLoadState = CatalogLoadState.Loading,
    val currentModelSelection: ModelSelection? = null,
    val toolConfiguration: ConversationToolConfiguration? = null,
    val availableMcpServers: List<McpServer> = emptyList(),
    val officialToolDescriptors: List<OfficialToolDescriptor> = emptyList(),
    val hasToolConfigurationError: Boolean = false,
    val showToolActivity: Boolean = true,
    /** 夜间模式覆盖值；`null` 表示跟随系统。由抽屉里的开关写入，MainActivity 据此解析主题。 */
    val darkThemeOverride: Boolean? = null,
    val isSpeechRecognitionAvailable: Boolean = false,
    val pendingToolConfirmations: List<PendingToolConfirmation> = emptyList(),
    /** Agent 在用户输入类长时运行工具上挂起、等待用户答复的请求（选择/输入卡片数据源）。 */
    val pendingInputRequests: List<UserInputRequest> = emptyList(),
    /** Full access 全局开关：开启后所有需要确认的工具调用自动放行。 */
    val fullAccess: Boolean = false,
    /** 被用户拒绝确认的工具名（内存展示态）；工具 chip 据此显示 ✗ 而非永远悬在"未完成"。 */
    val rejectedToolNames: Set<String> = emptySet(),
    val speechPlaybackState: SpeechPlaybackState = SpeechPlaybackState(),
    /**
     * 自动语音播报全局开关；开启时每轮 assistant 回复完成后自动朗读。
     * 状态由 [github.ponyhuang.gimi.domain.speech.repository.SpeechSettingsRepository] 持久化，
     * 跨会话保持，不随新会话重置。
     */
    val autoSpeakEnabled: Boolean = true,
)

val ChatUiState.pendingToolConfirmation: PendingToolConfirmation?
    get() = pendingToolConfirmations.firstOrNull()

val ChatUiState.pendingInputRequest: UserInputRequest?
    get() = pendingInputRequests.firstOrNull()

/**
 * 输入栏槽位当前应显示的挂起操作（授权 / 选项 / 文本输入），`null` 为正常胶囊。
 * 授权优先于输入请求；多个请求排队时取最先到达的一个。
 */
internal val ChatUiState.pendingComposerAction: PendingComposerAction?
    get() {
        pendingToolConfirmations.firstOrNull()?.let {
            return PendingComposerAction.Confirmation(it)
        }
        val request = pendingInputRequests.firstOrNull() ?: return null
        return when (request.kind) {
            UserInputKind.CHOICE -> PendingComposerAction.Choice(request)
            UserInputKind.FREE_TEXT -> PendingComposerAction.TextInput(request)
        }
    }

sealed interface ConversationTaskStatus {
    data class Running(val phase: AgentTaskPhase) : ConversationTaskStatus
    data class WaitingForConfirmation(val count: Int) : ConversationTaskStatus
    data object WaitingForInput : ConversationTaskStatus
    data object Completed : ConversationTaskStatus
    data object Failed : ConversationTaskStatus
}

fun ChatUiState.getCurrentUserMessage(): Message? =
    messages.firstOrNull()?.takeIf { message -> message.role == MessageRole.User }

/**
 * A user-selectable official tool exposed by the active model service. The
 * function list is loaded lazily by [ChatViewModel] when the user opens the
 * sub-page; [OfficialToolDescriptor.functions] stays empty while loading.
 */
data class OfficialToolDescriptor(
    val id: String,
    val functions: List<OfficialToolFunction> = emptyList(),
    val isLoadingFunctions: Boolean = false,
    val loadError: String? = null,
)
