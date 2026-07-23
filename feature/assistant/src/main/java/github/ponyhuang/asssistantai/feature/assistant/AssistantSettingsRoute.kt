package github.ponyhuang.asssistantai.feature.assistant

import android.Manifest
import android.app.role.RoleManager
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

/**
 * 助理设置路由：处理角色请求 intent、麦克风权限申请与磁贴添加（由宿主回调执行）。
 */
@Composable
fun AssistantSettingsRoute(
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssistantSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onAction(AssistantSettingsAction.RefreshStatus) }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onAction(AssistantSettingsAction.MicrophonePermissionResult(granted))
    }

    LaunchedEffect(Unit) {
        viewModel.onAction(AssistantSettingsAction.RefreshStatus)
        viewModel.onMicrophonePermissionKnown(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    AssistantSettingsScreen(
        state = state,
        onRequestRole = {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
            }
        },
        onRequestMicrophone = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onAddTile = {
            onAddTile()
            viewModel.onAction(AssistantSettingsAction.TileAddRequested)
        },
        modifier = modifier,
    )
}
