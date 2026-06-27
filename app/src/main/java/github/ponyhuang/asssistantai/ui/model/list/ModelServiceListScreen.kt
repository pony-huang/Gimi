package github.ponyhuang.asssistantai.ui.model.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.data.ModelProvider
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

    SettingsPageContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsSectionTitle(text = "已配置服务", modifier = Modifier.padding(top = 12.dp))
            LazyColumn(
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
