package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

@Composable
fun ModelServiceListRoute(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onAddService: () -> Unit,
    providerPicker: Boolean = false,
    serviceKind: ModelServiceKind = ModelServiceKind.Chat,
    modifier: Modifier = Modifier,
    viewModel: LLMModelServiceListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenceScaffold(
        modifier = modifier,
        style = PreferenceStyle.Compact,
        title = stringResource(if (providerPicker) R.string.modelsettings_add_service else if (serviceKind == ModelServiceKind.Speech) R.string.modelsettings_speech_list_title else R.string.modelsettings_list_title),
        onBack = onBack,
        actions = {
            if (!providerPicker) IconButton(onClick = onAddService) {
                Icon(Icons.Default.Add, stringResource(R.string.modelsettings_add_service))
            }
        },
    ) { scaffoldModifier ->
        // 按服务种类过滤厂商模板；语音服务的实时与级联模型共处同一服务，这里只按服务过滤。
        val categoryState = state.copy(items = state.items.filter {
            it.kind == serviceKind
        }.map { service ->
            service.copy(groups = service.groups.mapNotNull { group ->
                group.copy(models = group.models.filter {
                    (ModelCapability.LLM in it.capabilities) == (serviceKind == ModelServiceKind.Chat)
                }).takeIf { it.models.isNotEmpty() }
            })
        })
        LLMModelServiceListScreen(categoryState, viewModel::onAction, onNavigateToDetail,
            providerPicker, scaffoldModifier)
    }
}
