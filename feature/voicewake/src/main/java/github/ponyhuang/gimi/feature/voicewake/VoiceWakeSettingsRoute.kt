package github.ponyhuang.gimi.feature.voicewake

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun VoiceWakeSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceWakeSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    BackHandler { viewModel.onAction(VoiceWakeSettingsAction.RequestBack) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.onAction(
            VoiceWakeSettingsAction.PermissionsResult(
                REQUIRED_PERMISSIONS.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                },
            ),
        )
    }

    LaunchedEffect(state.permissionRequestId) {
        val requestId = state.permissionRequestId ?: return@LaunchedEffect
        val missing = REQUESTED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        viewModel.onAction(VoiceWakeSettingsAction.PermissionRequestHandled(requestId))
        if (missing.isEmpty()) {
            viewModel.onAction(VoiceWakeSettingsAction.PermissionsResult(granted = true))
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        overlayPermissionGranted = Settings.canDrawOverlays(context)
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is VoiceWakeSettingsEffect.KeywordSaved -> {
                    keyboardController?.hide()
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.voicewake_keyword_saved, effect.wakeWord),
                    )
                }
                VoiceWakeSettingsEffect.NavigateBack -> onBack()
            }
        }
    }

    PreferenceScaffold(
        title = stringResource(R.string.voicewake_screen_title),
        onBack = { viewModel.onAction(VoiceWakeSettingsAction.RequestBack) },
    ) { scaffoldModifier ->
        Box(modifier = scaffoldModifier.fillMaxSize()) {
            VoiceWakeSettingsScreen(
                state = state,
                onAction = viewModel::onAction,
                modifier = Modifier.fillMaxSize(),
                overlayPermissionGranted = overlayPermissionGranted,
                onOpenOverlaySettings = {
                    overlaySettingsLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri(),
                        ),
                    )
                },
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.POST_NOTIFICATIONS,
)

private val REQUESTED_PERMISSIONS = REQUIRED_PERMISSIONS + Manifest.permission.BLUETOOTH_CONNECT
