package github.ponyhuang.gimi.feature.skills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.skills.model.SkillImportFailure
import github.ponyhuang.gimi.feature.skills.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun SkillsSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SkillsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val archivePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.onAction(SkillsSettingsAction.LocalArchiveSelected(it.toString()))
        }
    }

    LaunchedEffect(state.filePickerRequestId) {
        val requestId = state.filePickerRequestId ?: return@LaunchedEffect
        viewModel.onAction(SkillsSettingsAction.FilePickerHandled(requestId))
        archivePicker.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    val noticeMessage = state.notice?.let { notice ->
        when (notice) {
            is SkillsNotice.Installed ->
                stringResource(R.string.skills_notice_installed, notice.name)
            is SkillsNotice.Removed ->
                stringResource(R.string.skills_notice_removed, notice.name)
            is SkillsNotice.Failed -> stringResource(notice.reason.messageResource())
        }
    }
    LaunchedEffect(state.notice, noticeMessage) {
        val message = noticeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onAction(SkillsSettingsAction.ConsumeNotice)
    }

    PreferenceScaffold(
        title = stringResource(R.string.skills_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        Box(modifier = scaffoldModifier.fillMaxSize()) {
            SkillsSettingsScreen(
                state = state,
                onAction = viewModel::onAction,
                modifier = Modifier.fillMaxSize(),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun SkillImportFailure.Reason.messageResource(): Int = when (this) {
    SkillImportFailure.Reason.InvalidSource,
    SkillImportFailure.Reason.InvalidSkillName,
    SkillImportFailure.Reason.PreparedImportNotFound,
    SkillImportFailure.Reason.ReplacementNotAllowed,
    -> R.string.skills_error_invalid_source
    SkillImportFailure.Reason.DownloadFailed -> R.string.skills_error_download
    SkillImportFailure.Reason.ArchiveTooLarge -> R.string.skills_error_too_large
    SkillImportFailure.Reason.TooManyEntries -> R.string.skills_error_too_many_entries
    SkillImportFailure.Reason.InvalidStructure -> R.string.skills_error_invalid_structure
    SkillImportFailure.Reason.UnsafeArchive -> R.string.skills_error_unsafe_archive
    SkillImportFailure.Reason.InvalidManifest -> R.string.skills_error_invalid_manifest
    SkillImportFailure.Reason.StorageFailure -> R.string.skills_error_storage
}