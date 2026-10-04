package github.ponyhuang.gimi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.core.notifications.AppNotificationManager
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.domain.permissions.model.AppPermission
import github.ponyhuang.gimi.domain.permissions.repository.PermissionRepository
import github.ponyhuang.gimi.domain.plugin.runtime.PluginLoadNotices
import github.ponyhuang.gimi.feature.chat.sharedImageUris
import github.ponyhuang.gimi.navigation.MainScreen
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.voice.AssistantPanelInteractor
import javax.inject.Inject
import kotlinx.coroutines.flow.collect

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var pluginLoadNotices: PluginLoadNotices
    @Inject lateinit var backgroundAppRepository: github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
    @Inject lateinit var backgroundAppWindowHost: github.ponyhuang.gimi.mobileuse.BackgroundAppWindowHost
    @Inject
    lateinit var appearanceRepository: AppearanceRepository
    @Inject
    lateinit var assistantSessionCoordinator: AssistantSessionCoordinator
    @Inject
    lateinit var assistantPanelInteractor: AssistantPanelInteractor
    @Inject
    lateinit var appNotificationManager: AppNotificationManager
    @Inject
    lateinit var permissionRepository: PermissionRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    private val sharedMediaUris = mutableStateOf<List<Uri>>(emptyList())
    private val openChatRequest = mutableStateOf(0)

    override fun onStart() {
        super.onStart()
        backgroundAppWindowHost.ensureService()
        appNotificationManager.cancelPendingInteractionNotifications()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionOnFreshInstall()
        sharedMediaUris.value = savedInstanceState
            ?.getStringArrayList(KEY_SHARED_MEDIA_URIS)
            ?.map(Uri::parse)
            ?: sharedImageUris(intent)
        if (intent.action == ACTION_OPEN_CURRENT_CHAT) openChatRequest.value += 1
        enableEdgeToEdge()
        setContent {
            val incompatiblePluginMessage = stringResource(R.string.plugin_incompatible_upgrade)
            LaunchedEffect(pluginLoadNotices, incompatiblePluginMessage) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    pluginLoadNotices.incompatiblePluginNames.collect { name ->
                        Toast.makeText(
                            this@MainActivity,
                            incompatiblePluginMessage.format(name),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            // 跟随系统时读取实时 uiMode，系统切深浅会自动重组；手动锁定后固定为所选模式。
            val themeMode by appearanceRepository.themeMode
                .collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AsssistantaiTheme(darkTheme = darkTheme) {
                androidx.compose.foundation.layout.Box {
                    MainScreen(
                        assistantSessionCoordinator = assistantSessionCoordinator,
                        assistantPanelInteractor = assistantPanelInteractor,
                        openChatRequest = openChatRequest.value,
                        sharedMediaUris = sharedMediaUris.value,
                        onSharedMediaConsumed = { sharedMediaUris.value = emptyList() },
                    )
                    github.ponyhuang.gimi.mobileuse.BackgroundAppInAppBubble(backgroundAppRepository, backgroundAppWindowHost)
                }
            }
        }
    }

    private fun requestNotificationPermissionOnFreshInstall() {
        val permission = AppPermission.PostNotifications
        if (!shouldRequestNotificationPermission(
                sdkInt = android.os.Build.VERSION.SDK_INT,
                granted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
                previouslyRequested = permissionRepository.wasRequested(permission),
            )
        ) return

        permissionRepository.recordRequested(setOf(permission))
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
            -> sharedMediaUris.value = sharedImageUris(intent)
            ACTION_OPEN_CURRENT_CHAT -> openChatRequest.value += 1
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(
            KEY_SHARED_MEDIA_URIS,
            ArrayList(sharedMediaUris.value.map(Uri::toString)),
        )
    }

    companion object {
        const val KEY_SHARED_MEDIA_URIS = "shared_media_uris"
        const val ACTION_OPEN_CURRENT_CHAT = "github.ponyhuang.gimi.action.OPEN_CURRENT_CHAT"
    }
}
