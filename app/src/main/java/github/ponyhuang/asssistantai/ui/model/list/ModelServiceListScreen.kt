package github.ponyhuang.asssistantai.ui.model.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.data.ModelProvider
import github.ponyhuang.asssistantai.data.ModelCatalogLoadState
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer
import github.ponyhuang.asssistantai.ui.settings.SettingsSectionTitle

/**
 * 模型服务列表页，仅展示已配置且可实际编辑的服务。
 *
 * 数据来源：[ModelServiceListViewModel.filtered]。
 * 点击列表 → [onNavigateToDetail]。
 */
@Composable
fun ModelServiceListScreen(
    viewModel: ModelServiceListViewModel,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.filtered.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()

    SettingsPageContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsSectionTitle(text = "已配置服务", modifier = Modifier.padding(top = 12.dp))
            when (loadState) {
                ModelCatalogLoadState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is ModelCatalogLoadState.Failed -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("模型目录加载失败")
                }

                ModelCatalogLoadState.Ready -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = items, key = { it.serviceId }) { item: ModelProvider ->
                        ModelServiceCard(
                            item = item,
                            onClick = onNavigateToDetail,
                            onToggleEnabled = viewModel::onToggleEnabled,
                        )
                    }
                }
            }
        }
    }
}
