package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference

/**
 * 单个本地文件搜索结果页：宿主仅传入当前 session/response id 与跨 capability 打开文件
 * 回调；本地文件搜索结果从 [ChatViewModel.uiState] 中按 session 与 response id 匹配得到，
 * 不再由导航组合根完成 session 隔离与消息回查。
 */
@Composable
fun ChatSearchResultsRoute(
    sessionId: String,
    responseId: String,
    onBack: () -> Unit,
    onOpenFile: (LocalFileReference) -> Unit,
) {
    val viewModel: ChatViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val result = remember(state.messages, sessionId, responseId) {
        if (state.sessionId != sessionId) {
            null
        } else {
            state.messages
                .asSequence()
                .flatMap { it.functionResponses.asSequence() }
                .firstOrNull { it.id == responseId }
                ?.localFileSearchResult
        }
    }
    LocalFileSearchResultsScreen(
        result = result,
        onBack = onBack,
        onOpenFile = onOpenFile,
    )
}