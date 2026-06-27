package github.ponyhuang.asssistantai.ui.model.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 列表页路由入口。注入 ViewModel + 接收跳转回调。
 */
@Composable
fun ModelServiceListRoute(
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelServiceListViewModel = hiltViewModel(),
) {
    ModelServiceListScreen(
        viewModel = viewModel,
        onNavigateToDetail = onNavigateToDetail,
        modifier = modifier,
    )
}
