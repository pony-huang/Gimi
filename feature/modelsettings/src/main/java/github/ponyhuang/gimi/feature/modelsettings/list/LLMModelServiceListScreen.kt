package github.ponyhuang.gimi.feature.modelsettings.list

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.feature.modelsettings.detail.modelServicePreviewFixture
import github.ponyhuang.gimi.ui.components.GimiSearchField
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun LLMModelServiceListScreen(
    state: ModelServiceListUiState,
    onAction: (ModelServiceListAction) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    providerPicker: Boolean = false,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier, style = PreferenceStyle.Compact) {
        when (state.loadState) {
            CatalogLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is CatalogLoadState.Failed -> Text(stringResource(R.string.modelsettings_list_load_failed), Modifier.padding(24.dp))
            CatalogLoadState.Ready -> {
                val services = if (providerPicker) state.items.filter {
                    state.query.isBlank() || it.id.contains(state.query.trim(), true) || it.name.contains(state.query.trim(), true)
                } else state.items.filter { it.isAdded }
                if (!providerPicker && services.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.CloudQueue, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.modelsettings_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.modelsettings_empty_summary), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    if (state.isMutationBlocked) item {
                        PreferenceBanner(stringResource(R.string.modelsettings_agent_mutation_blocked))
                    }
                    state.errorRes?.let { error -> item {
                        PreferenceBanner(stringResource(error), tone = PreferenceBannerTone.Error)
                    } }
                    if (providerPicker) item {
                        GimiSearchField(state.query, { onAction(ModelServiceListAction.QueryChanged(it)) },
                            placeholder = { Text(stringResource(R.string.modelsettings_search_provider)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                    item {
                        PreferenceSectionTitle(stringResource(if (providerPicker) R.string.modelsettings_choose_provider
                            else R.string.modelsettings_list_section_configured))
                        if (services.isEmpty()) {
                            PreferenceSectionNote(stringResource(R.string.modelsettings_no_matches))
                        } else PreferenceGroupCard {
                            services.forEachIndexed { index, service ->
                                ModelServiceCard(service, onNavigateToDetail,
                                    showDivider = index < services.lastIndex, providerPicker = providerPicker,
                                    onEnabledChange = { id, enabled -> onAction(ModelServiceListAction.EnabledChanged(id, enabled)) },
                                    isSwitchEnabled = !state.isMutationBlocked && service.id !in state.pendingServiceIds)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "服务列表", showBackground = true, widthDp = 390)
@Preview(name = "服务列表 · 深色", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ServiceListPreview() {
    AsssistantaiTheme {
        LLMModelServiceListScreen(ModelServiceListUiState(loadState = CatalogLoadState.Ready,
            items = listOf(modelServicePreviewFixture().copy(id = "deepseek", name = "DeepSeek"),
                modelServicePreviewFixture().copy(isEnabled = false))), {}, {})
    }
}

@Preview(name = "空列表", showBackground = true, widthDp = 390)
@Preview(name = "空列表 · 深色", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyServiceListPreview() {
    AsssistantaiTheme { LLMModelServiceListScreen(ModelServiceListUiState(loadState = CatalogLoadState.Ready), {}, {}) }
}

@Preview(name = "厂商选择", showBackground = true, widthDp = 390)
@Preview(name = "厂商选择 · 深色", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProviderPickerPreview() {
    AsssistantaiTheme {
        LLMModelServiceListScreen(ModelServiceListUiState(loadState = CatalogLoadState.Ready,
            items = listOf(modelServicePreviewFixture())), {}, {}, providerPicker = true)
    }
}
