package github.ponyhuang.asssistantai.feature.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.domain.permissions.model.AppPermission

@Composable
fun PermissionSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: PermissionSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    var requestedPermissions by remember {
        mutableStateOf<Map<String, AppPermission>>(emptyMap())
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onAction(PermissionSettingsAction.Refresh)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val permanentlyDenied = grants
            .filterValues { granted -> !granted }
            .keys
            .mapNotNullTo(mutableSetOf()) { name ->
                requestedPermissions[name]?.takeIf {
                    activity == null || !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        name,
                    )
                }
            }
        viewModel.onAction(
            PermissionSettingsAction.RuntimePermissionsResult(permanentlyDenied),
        )
        requestedPermissions = emptyMap()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAction(PermissionSettingsAction.Refresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.runtimeRequest) {
        val request = state.runtimeRequest ?: return@LaunchedEffect
        requestedPermissions = request.permissions.associateBy(AppPermission::androidName)
        viewModel.onAction(PermissionSettingsAction.RuntimeRequestHandled(request.id))
        permissionLauncher.launch(requestedPermissions.keys.toTypedArray())
    }

    LaunchedEffect(state.settingsRequest) {
        val request = state.settingsRequest ?: return@LaunchedEffect
        viewModel.onAction(PermissionSettingsAction.SettingsRequestHandled(request.id))
        val intent = when (request.destination) {
            PermissionSettingsDestination.ApplicationDetails -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            )
            PermissionSettingsDestination.WriteSystemSettings -> Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                "package:${context.packageName}".toUri(),
            )
            PermissionSettingsDestination.NotificationListener ->
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        settingsLauncher.launch(intent)
    }

    PermissionSettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

private fun AppPermission.androidName(): String = when (this) {
    AppPermission.FineLocation -> Manifest.permission.ACCESS_FINE_LOCATION
    AppPermission.CoarseLocation -> Manifest.permission.ACCESS_COARSE_LOCATION
    AppPermission.ReadCalendar -> Manifest.permission.READ_CALENDAR
    AppPermission.WriteCalendar -> Manifest.permission.WRITE_CALENDAR
    AppPermission.ReadMediaImages -> Manifest.permission.READ_MEDIA_IMAGES
    AppPermission.ReadMediaVideo -> Manifest.permission.READ_MEDIA_VIDEO
    AppPermission.ReadMediaAudio -> Manifest.permission.READ_MEDIA_AUDIO
    AppPermission.RecordAudio -> Manifest.permission.RECORD_AUDIO
    AppPermission.BluetoothConnect -> Manifest.permission.BLUETOOTH_CONNECT
    AppPermission.PostNotifications -> Manifest.permission.POST_NOTIFICATIONS
    AppPermission.WriteSystemSettings,
    AppPermission.NotificationListener,
    -> error("Special permissions cannot be requested at runtime.")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
