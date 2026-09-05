package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

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
            )
            if (state.isMutationBlocked) {
                PreferenceBanner(
                    text = stringResource(R.string.modelsettings_agent_mutation_blocked),
                    tone = PreferenceBannerTone.Error,
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
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    item {
                        // 供应商数量有限，整组渲染进同一张 One UI 卡片即可。
                        PreferenceGroupCard {
                            state.items.forEachIndexed { index, item ->
                                ModelServiceCard(
                                    item = item,
                                    mutationEnabled = !state.isMutationBlocked,
                                    showDivider = index < state.items.lastIndex,
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
    }
}

@Preview(showBackground = true)
@Composable
private fun LLMModelServiceListScreenPreview() {
    AsssistantaiTheme {
        LLMModelServiceListScreen(
            state = ModelServiceListUiState(
                loadState = CatalogLoadState.Ready,
                items = listOf(
                    LLMModelSetting(
                        id = "openai",
                        name = "OpenAI",
                        isEnabled = true,
                        apiKey = "sk-test",
                        apiBaseUrl = "https://api.openai.com/v1",
                        apiProtocol = github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.Standard,
                        anthropicBaseUrl = "https://api.anthropic.com",
                        groups = emptyList(),
                    ),
                    LLMModelSetting(
                        id = "deepseek",
                        name = "DeepSeek",
                        isEnabled = false,
                        apiKey = "",
                        apiBaseUrl = "https://api.deepseek.com/v1",
                        apiProtocol = github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.Standard,
                        anthropicBaseUrl = "https://api.anthropic.com",
                        groups = emptyList(),
                    ),
                ),
            ),
            onAction = {},
            onNavigateToDetail = {},
        )
    }
}
