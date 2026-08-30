package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    val viewModel: ChatViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
        onOpenFile = { file -> openLocalFile(context, file) },
    )
}
