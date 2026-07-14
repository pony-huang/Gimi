package github.ponyhuang.asssistantai.ui.settings.llmmodel.detail

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 详情页路由：从 Navigation 参数取 `serviceId`，调 ViewModel.loadService。
 *
 * - 找不到对应服务 → Toast + [onBack]（popBackStack）。
 * - 找到 → 渲染 [ModelServiceDetailScreen]。
 */
@Composable
fun LLMModelServiceDetailRoute(
    serviceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelServiceDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    LaunchedEffect(serviceId) {
        val ok = viewModel.loadService(serviceId)
        if (!ok) {
            Toast.makeText(context, "未找到该服务", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    ModelServiceDetailScreen(viewModel = viewModel, modifier = modifier)
}
