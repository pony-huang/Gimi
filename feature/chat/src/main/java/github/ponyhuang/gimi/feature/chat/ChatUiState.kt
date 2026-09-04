package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunction
import github.ponyhuang.gimi.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor

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
 *                     commit 完成或兜底分支结束置 false。驱动 [MainScreen] 中
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
    val conversations: List<Conversation> = emptyList(),
    val conversationTaskStatuses: Map<String, ConversationTaskStatus> = emptyMap(),
    val isInitializing: Boolean = false,
    val availableLLMModelSettings: List<LLMModelSetting> = emptyList(),
    val modelCatalogLoadState: CatalogLoadState = CatalogLoadState.Loading,
    val currentModelSelection: ModelSelection? = null,
    val toolConfiguration: ConversationToolConfiguration? = null,
    val availableLocalTools: List<ToolDescriptor> = emptyList(),
    val availableMcpServers: List<McpServer> = emptyList(),
    val officialToolDescriptors: List<OfficialToolDescriptor> = emptyList(),
    val hasToolConfigurationError: Boolean = false,
    val showToolActivity: Boolean = true,
    /** 夜间模式覆盖值；`null` 表示跟随系统。由抽屉里的开关写入，MainActivity 据此解析主题。 */
    val darkThemeOverride: Boolean? = null,
    val isSpeechRecognitionAvailable: Boolean = false,
    val pendingToolConfirmations: List<PendingToolConfirmation> = emptyList(),
    /** Full access 全局开关：开启后所有需要确认的工具调用自动放行。 */
    val fullAccess: Boolean = false,
    /** 被用户拒绝确认的工具名（内存展示态）；工具 chip 据此显示 ✗ 而非永远悬在"未完成"。 */
    val rejectedToolNames: Set<String> = emptySet(),
    val speechPlaybackState: SpeechPlaybackState = SpeechPlaybackState(),
)

val ChatUiState.pendingToolConfirmation: PendingToolConfirmation?
    get() = pendingToolConfirmations.firstOrNull()

sealed interface ConversationTaskStatus {
    data class Running(val phase: AgentTaskPhase) : ConversationTaskStatus
    data class WaitingForConfirmation(val count: Int) : ConversationTaskStatus
    data object Completed : ConversationTaskStatus
    data object Failed : ConversationTaskStatus
}

fun ChatUiState.getCurrentUserMessage(): Message? =
    messages.firstOrNull()?.takeIf { message -> message.role == MessageRole.User }

fun ChatUiState.getCurrentAssistantMessage(): Message? =
    messages.firstOrNull()?.takeIf { message -> message.role == MessageRole.Assistant }

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
