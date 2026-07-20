package github.ponyhuang.asssistantai.feature.workfiles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WorkFilesSettingsRoute(
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

    WorkFilesSettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
