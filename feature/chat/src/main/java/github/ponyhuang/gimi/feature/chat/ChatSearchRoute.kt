package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.conversation.model.LocalFileSearchResult
import github.ponyhuang.gimi.domain.conversation.model.Message

/**
 * 单个本地文件搜索结果页：宿主只传入 session/response id 与返回回调；本地文件搜索
 * 结果从 [ChatViewModel.uiState] 中按 id 匹配，文件打开也由当前 Route 处理，不再由导航
 * 组合根完成 session 隔离、消息回查或 Android Intent 副作用。
 */
@Composable
fun ChatSearchResultsRoute(
    sessionId: String,
    responseId: String,
    onBack: () -> Unit,
) {
    // 与 Chat 主界面共享同一 Activity 作用域实例，否则读不到会话消息。
    val viewModel: ChatViewModel = activityScopedChatViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val result = remember(state.messages, sessionId, responseId) {
        if (state.sessionId != sessionId) {
            null
        } else {
            findLocalFileSearchResult(state.messages, responseId)
        }
    }
    LocalFileSearchResultsScreen(
        result = result,
        onBack = onBack,
        onOpenFile = { file -> openLocalFile(context, file) },
    )
}

/**
 * 按 response id 回查本地文件结果。同一 call id 在确认流程下会先出现文件列表为空的
 * 占位响应、后出现真实结果，因此优先返回带非空文件列表的命中，找不到再回退任意命中。
 */
internal fun findLocalFileSearchResult(
    messages: List<Message>,
    responseId: String,
): LocalFileSearchResult? {
    val matches = messages
        .asSequence()
        .flatMap { it.functionResponses.asSequence() }
        .filter { it.id == responseId }
        .mapNotNull { it.localFileSearchResult }
        .toList()
    return matches.firstOrNull { it.files.isNotEmpty() } ?: matches.firstOrNull()
}
