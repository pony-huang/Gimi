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
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

@Composable
fun ModelServiceListRoute(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onAddService: () -> Unit,
    providerPicker: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: LLMModelServiceListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PreferenceScaffold(
        modifier = modifier,
        style = PreferenceStyle.Compact,
        title = stringResource(if (providerPicker) R.string.modelsettings_add_service else R.string.modelsettings_list_title),
        onBack = onBack,
        actions = {
            if (!providerPicker) IconButton(onClick = onAddService) {
                Icon(Icons.Default.Add, stringResource(R.string.modelsettings_add_service))
            }
        },
    ) { scaffoldModifier ->
        LLMModelServiceListScreen(state, viewModel::onAction, onNavigateToDetail,
            providerPicker, scaffoldModifier)
    }
}
