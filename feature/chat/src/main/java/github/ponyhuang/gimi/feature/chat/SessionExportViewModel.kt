package github.ponyhuang.gimi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportRequest
import github.ponyhuang.gimi.domain.conversation.export.ExportConversationsUseCase
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
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
) {
    fun canExport(sessionId: String? = null): Boolean =
        !isWorking && request == null && hasConversations && !runtimeState.blocksSessionRead(sessionId)
}

/** 路由负责展示的导出结果通知。 */
enum class SessionExportNotice { Succeeded, Failed, Blocked, Empty }

/** 系统文件选择器与结果通知，由 Route 消费。 */
sealed interface SessionExportEffect {
    /** 携带已冻结范围及文件类型的文档创建请求。 */
    data class ChooseDestination(val request: ConversationExportRequest) : SessionExportEffect
    /** 不暴露底层异常或历史内容的用户通知。 */
    data class ShowNotice(val notice: SessionExportNotice) : SessionExportEffect
}

/** 管理导出请求与保存进度，实际会话读取与任务互斥规则由领域工作流统一实现。 */
@HiltViewModel
class SessionExportViewModel @Inject constructor(
    private val exporter: ExportConversationsUseCase,
    conversations: ConversationRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SessionExportUiState())
    private val effectChannel = Channel<SessionExportEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    val uiState = combine(mutableState, exporter.runtimeState, conversations.conversations) { state, runtime, sessions ->
        state.copy(runtimeState = runtime, hasConversations = sessions.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionExportUiState())

    fun requestExport(sessionId: String? = null) {
        if (mutableState.value.isWorking || mutableState.value.request != null) return
        mutableState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                cancellationAwareRunCatching { exporter.prepare(sessionId) }.fold(
                    onSuccess = { result ->
                        when (result) {
                            AgentMutationResult.BlockedByActiveAgent -> notify(SessionExportNotice.Blocked)
                            is AgentMutationResult.Applied -> {
                                val request = result.value
                                if (request == null) {
                                    notify(SessionExportNotice.Empty)
                                } else {
                                    mutableState.update { it.copy(request = request) }
                                    effectChannel.send(SessionExportEffect.ChooseDestination(request))
                                }
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

    private suspend fun notify(notice: SessionExportNotice) {
        effectChannel.send(SessionExportEffect.ShowNotice(notice))
    }
}
