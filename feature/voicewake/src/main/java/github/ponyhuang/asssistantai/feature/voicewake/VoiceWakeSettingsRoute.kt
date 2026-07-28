package github.ponyhuang.asssistantai.feature.voicewake

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
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

// effect 携带动态 string res id，无法在组合期用 stringResource 解析，豁免该 lint。
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun VoiceWakeSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: VoiceWakeSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.onAction(
            VoiceWakeSettingsAction.PermissionsResult(grants.values.all { it }),
        )
    }

    LaunchedEffect(state.permissionRequestId) {
        val requestId = state.permissionRequestId ?: return@LaunchedEffect
        val missing = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        viewModel.onAction(VoiceWakeSettingsAction.PermissionRequestHandled(requestId))
        if (missing.isEmpty()) {
            viewModel.onAction(VoiceWakeSettingsAction.PermissionsResult(granted = true))
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is VoiceWakeSettingsEffect.ShowToast -> Toast.makeText(
                    context,
                    context.getString(effect.messageRes),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    VoiceWakeSettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
)
