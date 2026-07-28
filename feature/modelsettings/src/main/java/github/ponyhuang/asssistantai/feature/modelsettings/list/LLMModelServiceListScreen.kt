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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.asssistantai.feature.modelsettings.R
import github.ponyhuang.asssistantai.ui.preference.PreferencePageContainer
import github.ponyhuang.asssistantai.ui.preference.PreferenceSectionTitle

@Composable
fun LLMModelServiceListScreen(
    state: ModelServiceListUiState,
    onAction: (ModelServiceListAction) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            PreferenceSectionTitle(
                text = stringResource(R.string.modelsettings_list_section_configured),
                modifier = Modifier.padding(top = 12.dp),
            )
            if (state.isMutationBlocked) {
                Text(
                    text = stringResource(R.string.modelsettings_agent_mutation_blocked),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
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
                    Text(stringResource(R.string.modelsettings_list_load_failed))
                }
                CatalogLoadState.Ready -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(items = state.items, key = { it.id }) { item ->
                        ModelServiceCard(
                            item = item,
                            mutationEnabled = !state.isMutationBlocked,
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
