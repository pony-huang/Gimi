package github.ponyhuang.asssistantai.feature.modelsettings.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer
import github.ponyhuang.asssistantai.ui.settings.SettingsSectionTitle

@Composable
fun LLMModelServiceListScreen(
    state: ModelServiceListUiState,
    onAction: (ModelServiceListAction) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsSectionTitle(text = "已配置服务", modifier = Modifier.padding(top = 12.dp))
            when (state.loadState) {
                CatalogLoadState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is CatalogLoadState.Failed -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("模型目录加载失败")
                }
                CatalogLoadState.Ready -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(items = state.items, key = { it.id }) { item ->
                        ModelServiceCard(
                            item = item,
                            onClick = onNavigateToDetail,
                            onToggleEnabled = { id, enabled ->
                                onAction(ModelServiceListAction.EnabledChanged(id, enabled))
                            },
                        )
                    }
                }
            }
        }
    }
}
