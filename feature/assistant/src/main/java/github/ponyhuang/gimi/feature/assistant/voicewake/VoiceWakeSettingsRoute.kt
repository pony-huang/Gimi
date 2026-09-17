package github.ponyhuang.gimi.feature.assistant.voicewake

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.assistant.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun VoiceWakeSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceWakeSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onAction(VoiceWakeSettingsAction.PermissionResult(granted))
    }

    LaunchedEffect(state.permissionRequestId) {
        val requestId = state.permissionRequestId ?: return@LaunchedEffect
        viewModel.onAction(VoiceWakeSettingsAction.PermissionRequestHandled(requestId))
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onAction(VoiceWakeSettingsAction.PermissionResult(granted = true))
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    PreferenceScaffold(
        title = stringResource(R.string.voicewake_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        VoiceWakeSettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier.then(modifier),
        )
    }
}
