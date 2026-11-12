package github.ponyhuang.gimi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.core.notifications.AppNotificationManager
import github.ponyhuang.gimi.data.voice.runtime.RealtimeVoiceCallService
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.domain.permissions.model.AppPermission
import github.ponyhuang.gimi.domain.permissions.repository.PermissionRepository
import github.ponyhuang.gimi.domain.plugin.runtime.PluginLoadNotices
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import github.ponyhuang.gimi.feature.chat.sharedImageUris
import github.ponyhuang.gimi.navigation.MainScreen
import github.ponyhuang.gimi.ui.theme.GimiTheme
import github.ponyhuang.gimi.voice.AssistantPanelInteractor
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    @Inject
    lateinit var voiceCallController: RealtimeVoiceCallController

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { requestNotificationPermissionInFlight = false }

    // 系统权限框还在等待作答时，通话前台提示不能叠 Toast 或再次申请；回调返回后复位。
    private var requestNotificationPermissionInFlight = false

    private val sharedMediaUris = mutableStateOf<List<Uri>>(emptyList())
    private val openChatRequest = mutableStateOf(0)
    private val openPermissionsRequest = mutableStateOf(0)

    override fun onStart() {
        super.onStart()
        backgroundAppWindowHost.ensureService()
        appNotificationManager.cancelPendingInteractionNotifications()
        // 后台启动受限或系统回收过服务时，应用重新可见即可补齐通话的前台优先级。
        voiceCallController.ensureForeground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionOnFreshInstall()
        consumeReturnToVoiceCall(intent)
        sharedMediaUris.value = savedInstanceState
            ?.getStringArrayList(KEY_SHARED_MEDIA_URIS)
            ?.map(Uri::parse)
            ?: sharedImageUris(intent)
        if (savedInstanceState == null && intent.action == ACTION_OPEN_PERMISSIONS) openPermissionsRequest.value += 1
        if (intent.action == ACTION_OPEN_CURRENT_CHAT) openChatRequest.value += 1
        observeVoiceCallNotificationPermission()
        enableEdgeToEdge()
        setContent {
            val incompatiblePluginMessage = stringResource(R.string.plugin_incompatible_upgrade)
            val incompatiblePluginsMessage = stringResource(R.string.plugins_incompatible_upgrade)
            val pluginNameSeparator = stringResource(R.string.plugin_name_separator)
            LaunchedEffect(pluginLoadNotices, incompatiblePluginMessage, incompatiblePluginsMessage, pluginNameSeparator) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    pluginLoadNotices.incompatiblePluginNames.collect { names ->
                        Toast.makeText(
                            this@MainActivity,
                            if (names.size == 1) {
                                incompatiblePluginMessage.format(names.single())
                            } else {
                                incompatiblePluginsMessage.format(
                                    pluginUpgradeNameSummary(names, pluginNameSeparator),
                                    names.size,
                                )
                            },
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
            // 系统栏图标深浅必须跟随应用内所选主题，而不是系统 uiMode：
            // onCreate 的 enableEdgeToEdge() 只按系统深浅决定一次，手动锁定夜间
            // 而系统处于白天时状态栏仍是深色图标，在深色背景上不可见。
            // 主题模式变化时重新应用，深浅判断被 lambda 覆盖为 darkTheme。
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = DEFAULT_NAV_LIGHT_SCRIM,
                        darkScrim = DEFAULT_NAV_DARK_SCRIM,
                    ) { darkTheme },
                )
                onDispose {}
            }
            GimiTheme(darkTheme = darkTheme) {
                androidx.compose.foundation.layout.Box {
                    MainScreen(
                        assistantSessionCoordinator = assistantSessionCoordinator,
                        assistantPanelInteractor = assistantPanelInteractor,
                        openChatRequest = openChatRequest.value,
                        openPermissionsRequest = openPermissionsRequest.value,
                        sharedMediaUris = sharedMediaUris.value,
                        onSharedMediaConsumed = ::consumeSharedMedia,
                    )
                    github.ponyhuang.gimi.mobileuse.BackgroundAppInAppBubble(backgroundAppRepository, backgroundAppWindowHost)
                }
            }
        }
    }

    /** 通话启动的同时确保状态栏通知可见；首次安装已申请过，这里覆盖升级安装与被拒后的引导。 */
    private fun observeVoiceCallNotificationPermission() {
        // 与插件通知一致以 RESUMED 门控：避免后台期间弹权限对话框或 Toast；
        // 通话期间每次回到前台最多提示一次。
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var promptedWhileActive = false
                voiceCallController.state
                    .map { it.active }
                    .distinctUntilChanged()
                    .collect { active ->
                        if (!active) {
                            promptedWhileActive = false
                        } else if (!promptedWhileActive) {
                            promptedWhileActive = true
                            ensureCallNotificationPermission()
                        }
                    }
            }
        }
    }

    private fun ensureCallNotificationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        // onCreate 的首次申请可能还没作答；此时既不能叠 Toast，也不能重复 launch。
        if (requestNotificationPermissionInFlight) return
        val permission = AppPermission.PostNotifications
        if (shouldRequestNotificationPermission(
                sdkInt = android.os.Build.VERSION.SDK_INT,
                granted = false,
                previouslyRequested = permissionRepository.wasRequested(permission),
            )
        ) {
            permissionRepository.recordRequested(setOf(permission))
            launchNotificationPermissionRequest()
            return
        }
        // 曾申请过但未授予：可能是普通拒绝（系统仍可再弹），也可能是永久拒绝或
        // 申请框还挂着时进程被杀。前者补弹一次系统框，其余只能引导去设置。
        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            launchNotificationPermissionRequest()
            return
        }
        // 已被拒绝：系统不会再弹权限框，也不能静默失败，否则用户会以为后台通话丢了通知。
        Toast.makeText(this, R.string.voice_call_notification_permission, Toast.LENGTH_LONG).show()
    }

    private fun launchNotificationPermissionRequest() {
        requestNotificationPermissionInFlight = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        launchNotificationPermissionRequest()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReturnToVoiceCall(intent)
        when (intent.action) {
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
            -> sharedMediaUris.value = sharedImageUris(intent)
            ACTION_OPEN_PERMISSIONS -> openPermissionsRequest.value += 1
            ACTION_OPEN_CURRENT_CHAT -> openChatRequest.value += 1
        }
    }

    /** 常驻通知的点击只请求返回语音页；请求驻留在通话状态里，等页面可见时再消费。 */
    private fun consumeReturnToVoiceCall(intent: Intent) {
        if (!intent.getBooleanExtra(RealtimeVoiceCallService.EXTRA_RETURN_TO_VOICE_CALL, false)) return
        voiceCallController.requestReturnToCall()
        intent.removeExtra(RealtimeVoiceCallService.EXTRA_RETURN_TO_VOICE_CALL)
    }

    private fun consumeSharedMedia() {
        sharedMediaUris.value = emptyList()
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            // 同时清理启动来源，Activity 重新创建时也不能从旧 intent 再次导入附件。
            setIntent(Intent(intent).apply {
                action = Intent.ACTION_MAIN
                removeExtra(Intent.EXTRA_STREAM)
                clipData = null
            })
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
        const val ACTION_OPEN_PERMISSIONS = "github.ponyhuang.gimi.action.OPEN_PERMISSIONS"
        const val ACTION_OPEN_CURRENT_CHAT = "github.ponyhuang.gimi.action.OPEN_CURRENT_CHAT"

        // 与 androidx.activity EdgeToEdge 的导航栏默认 scrim 保持一致，
        // 仅把深浅判断从系统 uiMode 覆盖为应用内主题。
        private val DEFAULT_NAV_LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DEFAULT_NAV_DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
