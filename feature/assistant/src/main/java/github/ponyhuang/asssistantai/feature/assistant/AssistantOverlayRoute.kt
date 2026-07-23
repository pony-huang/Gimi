package github.ponyhuang.asssistantai.feature.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantInvocationSource

/**
 * 助理浮层路由：持有 Android 副作用（权限申请、关闭回调），界面保持无状态。
 */
@Composable
fun AssistantOverlayRoute(
    source: AssistantInvocationSource,
    onClose: () -> Unit,
    onOpenInChat: (sessionId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssistantOverlayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onAction(AssistantOverlayAction.MicPermissionResult(granted))
    }

    LaunchedEffect(source) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onInvoked(source, granted)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AssistantOverlayEvent.CloseOverlay -> onClose()
                AssistantOverlayEvent.RequestMicrophonePermission ->
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    AssistantOverlayScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenInChat = { onOpenInChat(state.voiceSessionId) },
        modifier = modifier,
    )
}
