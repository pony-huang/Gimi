package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.ChatTurn
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentExecution
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import javax.inject.Inject

/**
 * 已加载的会话历史快照，不包含运行时任务所有权。
 *
 * @property messages 当前内存历史。
 * @property contentRevision 读取历史时对应的仓库内容版本。
 */
data class ChatHistorySnapshot(
    val messages: List<Message>,
    val contentRevision: Long,
)

/**
 * 准备完成但尚未由调用方接管的发送上下文。
 *
 * @property turn 已归档输入及本轮历史。
 * @property execution 本轮使用的执行上下文。
 * @property toolConfiguration 本轮重新读取的持久化工具配置。
 * @property contentRevision 准备历史时观察到的仓库内容版本。
 */
data class PreparedChatSend(
    val turn: ChatTurn,
    val execution: ChatAgentExecution,
    val toolConfiguration: ConversationToolConfiguration,
    val contentRevision: Long,
)

/** 在调用方持有会话运行锁时准备发送；不启动模型流、不发布状态或管理 lease。 */
class PrepareChatSendUseCase @Inject constructor(
    private val sessionResolver: ConversationSessionResolver,
    private val repository: ConversationRepository,
    private val prepareTurn: PrepareChatTurnUseCase,
    private val agent: ChatAgentRepository,
) {
    suspend operator fun invoke(
        sessionId: String,
        selection: ModelSelection,
        text: String,
        drafts: List<DraftAttachment>,
        cachedHistory: () -> ChatHistorySnapshot? = { null },
        retry: ChatTurn? = null,
    ): PreparedChatSend {
        // 配置失败必须终止准备，不能沿用 UI 中的旧工具配置。
        val configuration = sessionResolver.resolveToolConfiguration(sessionId, selection)
        val revision = repository.conversationContentRevisions.value[sessionId] ?: 0L
        // 在配置读取后取快照，保持原有挂起期间的最新内存历史语义。
        val cached = cachedHistory()
        val history = if (cached != null && cached.contentRevision >= revision) {
            cached.messages
        } else {
            repository.loadMessages(sessionId).orEmpty()
        }
        val turn = prepareTurn(sessionId, text, drafts, history, retry)
        val execution = agent.createExecution(sessionId, selection, configuration)
        return PreparedChatSend(turn, execution, configuration, revision)
    }
}
