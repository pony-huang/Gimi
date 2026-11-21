package github.ponyhuang.gimi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportRequest
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportSession
import github.ponyhuang.gimi.domain.conversation.export.ExportConversationsUseCase
import github.ponyhuang.gimi.domain.conversation.model.ChatMode
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AssistantConversationPartition
import github.ponyhuang.gimi.domain.conversation.runtime.ChatbotConversationPartition
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.conversation.runtime.blocksSessionRead
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 导出入口的操作进度、待保存请求和任务保护状态；所有临时状态由 ViewModel 持有。 */
data class SessionExportUiState(
    val isWorking: Boolean = false,
    val request: ConversationExportRequest? = null,
    val hasConversations: Boolean = false,
    val runtimeState: AgentRuntimeState = AgentRuntimeState.Idle,
    /** 待呈现的一次性导出结果；Route 呈现后经 [SessionExportViewModel.consumeNotice] 清除。 */
    val notice: SessionExportNotice? = null,
) {
    fun canExport(sessionId: String? = null): Boolean =
        !isWorking && request == null && hasConversations && !runtimeState.blocksSessionRead(sessionId)
}

/** 路由负责展示的导出结果通知。 */
enum class SessionExportNotice { Succeeded, Failed, Blocked }

/** 系统文件选择器请求；结果通知改由 UiState 的 notice 字段承载。 */
sealed interface SessionExportEffect {
    /** 携带已冻结范围及文件类型的文档创建请求。 */
    data class ChooseDestination(val request: ConversationExportRequest) : SessionExportEffect
}

/** 管理导出请求与保存进度，实际会话读取与任务互斥规则由领域工作流统一实现。 */
@HiltViewModel
class SessionExportViewModel @Inject constructor(
    private val exporter: ExportConversationsUseCase,
    @AssistantConversationPartition assistantConversations: ConversationRepository,
    @ChatbotConversationPartition chatbotConversations: ConversationRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SessionExportUiState())
    private val effectChannel = Channel<SessionExportEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    val uiState = combine(
        mutableState,
        exporter.runtimeState,
        assistantConversations.conversations,
        chatbotConversations.conversations,
    ) { state, runtime, assistant, chatbot ->
        state.copy(runtimeState = runtime, hasConversations = assistant.isNotEmpty() || chatbot.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionExportUiState())

    /** 长按导出会话；档位决定会话所在分区，分区随导出请求冻结。 */
    fun requestExport(sessionId: String, mode: ChatMode) {
        if (mutableState.value.isWorking || mutableState.value.request != null) return
        mutableState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                cancellationAwareRunCatching { exporter.prepare(session(mode, sessionId)) }.fold(
                    onSuccess = { result ->
                        when (result) {
                            AgentMutationResult.BlockedByActiveAgent -> notify(SessionExportNotice.Blocked)
                            is AgentMutationResult.Applied -> {
                                mutableState.update { it.copy(request = result.value) }
                                effectChannel.send(SessionExportEffect.ChooseDestination(result.value))
                            }
                        }
                    },
                    onFailure = { notify(SessionExportNotice.Failed) },
                )
            } finally {
                mutableState.update { it.copy(isWorking = false) }
            }
        }
    }

    fun destinationSelected(destination: String?) {
        val request = mutableState.value.request ?: return
        mutableState.update { it.copy(request = null, isWorking = destination != null) }
        if (destination == null) return
        viewModelScope.launch {
            try {
                cancellationAwareRunCatching { exporter.save(request, destination) }.fold(
                    onSuccess = { result ->
                        notify(
                            when (result) {
                                AgentMutationResult.BlockedByActiveAgent -> SessionExportNotice.Blocked
                                is AgentMutationResult.Applied -> SessionExportNotice.Succeeded
                            },
                        )
                    },
                    onFailure = { notify(SessionExportNotice.Failed) },
                )
            } finally {
                mutableState.update { it.copy(isWorking = false) }
            }
        }
    }

    fun destinationUnavailable() {
        mutableState.update { it.copy(request = null) }
        viewModelScope.launch { notify(SessionExportNotice.Failed) }
    }

    /** 一次性通知以 UiState 承载：呈现前不丢失，呈现后由 Route 经 consumeNotice 清除。 */
    private fun notify(notice: SessionExportNotice) {
        mutableState.update { it.copy(notice = notice) }
    }

    /** Route 呈现通知后调用，清空 [SessionExportUiState.notice]。 */
    fun consumeNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    private fun session(mode: ChatMode, sessionId: String) =
        ConversationExportSession(userId = mode.partitionUserId(), sessionId = sessionId)
}

/** 档位对应的会话存储分区；导出读取必须与写入分区一致，否则会话不可见。 */
private fun ChatMode.partitionUserId(): String = when (this) {
    ChatMode.ASSISTANT -> AgentSessionIdentity.DEFAULT_USER_ID
    ChatMode.CHATBOT -> AgentSessionIdentity.CHATBOT_USER_ID
}
