package github.ponyhuang.gimi.feature.workfiles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun WorkFilesSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkFilesSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val documentTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            viewModel.onAction(WorkFilesSettingsAction.DirectorySelected(it.toString()))
        }
    }

    LaunchedEffect(state.directoryPickerRequestId) {
        val requestId = state.directoryPickerRequestId ?: return@LaunchedEffect
        viewModel.onAction(WorkFilesSettingsAction.DirectoryPickerHandled(requestId))
        documentTreeLauncher.launch(null)
    }

    PreferenceScaffold(
        title = stringResource(R.string.workfiles_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        WorkFilesSettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
}