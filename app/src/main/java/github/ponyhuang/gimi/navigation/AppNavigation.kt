package github.ponyhuang.gimi.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import github.ponyhuang.gimi.BuildConfig
import github.ponyhuang.gimi.domain.assistant.model.isPresentationResultIdle
import github.ponyhuang.gimi.feature.chat.ChatDestination
import github.ponyhuang.gimi.feature.chat.ChatEntryProvider
import github.ponyhuang.gimi.feature.chat.ChatNavigationCallbacks
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.feature.assistant.AssistantSurface
import github.ponyhuang.gimi.feature.assistant.AssistantSurfaceMode
import github.ponyhuang.gimi.feature.mcp.McpDestination
import github.ponyhuang.gimi.feature.mcp.McpEntryProvider
import github.ponyhuang.gimi.feature.memory.MemoryDestination
import github.ponyhuang.gimi.feature.memory.MemoryEntryProvider
import github.ponyhuang.gimi.feature.modelsettings.ModelSettingsDestination
import github.ponyhuang.gimi.feature.modelsettings.ModelSettingsEntryProvider
import github.ponyhuang.gimi.feature.permissions.PermissionDestination
import github.ponyhuang.gimi.feature.permissions.PermissionEntryProvider
import github.ponyhuang.gimi.feature.plugin.PluginDestination
import github.ponyhuang.gimi.feature.plugin.PluginEntryProvider
import github.ponyhuang.gimi.feature.recommendation.RecommendationDestination
import github.ponyhuang.gimi.feature.recommendation.RecommendationEntryProvider
import github.ponyhuang.gimi.feature.settings.SettingsDestination
import github.ponyhuang.gimi.feature.settings.SettingsEntryProvider
import github.ponyhuang.gimi.feature.settings.SettingsNavigationCallbacks
import github.ponyhuang.gimi.feature.skills.SkillsDestination
import github.ponyhuang.gimi.feature.skills.SkillsEntryProvider
import github.ponyhuang.gimi.feature.toolauthorization.ToolAuthorizationDestination
import github.ponyhuang.gimi.feature.toolauthorization.ToolAuthorizationEntryProvider
import github.ponyhuang.gimi.feature.assistant.voicewake.VoiceWakeDestination
import github.ponyhuang.gimi.feature.assistant.voicewake.VoiceWakeEntryProvider
import github.ponyhuang.gimi.feature.workfiles.WorkFilesDestination
import github.ponyhuang.gimi.feature.workfiles.WorkFilesEntryProvider
import github.ponyhuang.gimi.voice.AssistantPanelInteractor
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** App-level composition root. Feature modules own destination dispatch and never navigate directly. */
@Composable
fun MainScreen(
    assistantSessionCoordinator: AssistantSessionCoordinator,
    assistantPanelInteractor: AssistantPanelInteractor,
    openChatRequest: Int = 0,
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(ChatDestination.Chat)
    val assistantState by assistantSessionCoordinator.state.collectAsStateWithLifecycle()
    val recording by assistantPanelInteractor.recording.collectAsStateWithLifecycle()
    val audioLevel by assistantPanelInteractor.audioLevel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micPermissionGranted = granted
        if (granted) assistantPanelInteractor.toggleMic()
    }
    val onMicToggle = {
        if (micPermissionGranted) {
            assistantPanelInteractor.toggleMic()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val goBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    val navigate: (NavKey) -> Unit = backStack::add
    val replaceCurrent: (NavKey) -> Unit = { destination ->
        if (backStack.size > 1) backStack.removeLastOrNull()
        backStack.add(destination)
    }
    val returnToChat = {
        // 不用 removeLast：minSdk 34 下 lint 会报 NewApi（API 35 的 java.util.List#removeLast）。
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    LaunchedEffect(sharedMediaUris) {
        if (sharedMediaUris.isNotEmpty()) returnToChat()
    }

    LaunchedEffect(openChatRequest) {
        if (openChatRequest > 0) returnToChat()
    }

    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        // 将 hiltViewModel() 绑定到单个 NavEntry，避免相同 Route 类型错误复用 Activity 级状态。
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { destination ->
            NavEntry(
                key = destination,
                contentKey = destination.navigationContentKey(),
            ) {
                val handled = ChatEntryProvider(
                    destination = destination,
                    callbacks = ChatNavigationCallbacks(
                        onReturnToChat = returnToChat,
                        onOpenSettings = { navigate(SettingsDestination.Settings) },
                        onConfigureModels = { navigate(ModelSettingsDestination.ServiceList) },
                        onOpenSearchResults = { sessionId, responseId ->
                            navigate(ChatDestination.SearchResults(sessionId, responseId))
                        },
                        onBack = goBack,
                        sharedMediaUris = sharedMediaUris,
                        onSharedMediaConsumed = onSharedMediaConsumed,
                    ),
                ) || SettingsEntryProvider(
                    destination = destination,
                    appVersionName = BuildConfig.VERSION_NAME,
                    callbacks = SettingsNavigationCallbacks(
                        onBack = goBack,
                        onNavigateToModelService = {
                            navigate(ModelSettingsDestination.ServiceList)
                        },
                        onNavigateToDefaultModels = {
                            navigate(ModelSettingsDestination.Defaults)
                        },
                        onNavigateToVoiceWake = { navigate(VoiceWakeDestination.Settings) },
                        onNavigateToMcpServers = { navigate(McpDestination.ServerList) },
                        onNavigateToPlugins = { navigate(PluginDestination.Settings) },
                        onNavigateToSkills = { navigate(SkillsDestination.Settings) },
                        onNavigateToWorkFiles = { navigate(WorkFilesDestination.Settings) },
                        onNavigateToPermissions = { navigate(PermissionDestination.Settings) },
                        onNavigateToToolAuthorization = {
                            navigate(ToolAuthorizationDestination.Settings)
                        },
                        onNavigateToRecommendations = {
                            navigate(RecommendationDestination.Settings)
                        },
                        onNavigateToMemory = { navigate(MemoryDestination.Settings) },
                    ),
                ) || ModelSettingsEntryProvider(destination, goBack, navigate) ||
                    McpEntryProvider(destination, goBack, navigate, replaceCurrent) ||
                    PluginEntryProvider(destination, goBack, navigate) ||
                    ToolAuthorizationEntryProvider(destination, goBack, navigate) ||
                    RecommendationEntryProvider(
                        destination = destination,
                        onBack = goBack,
                        onOpenPermissions = { navigate(PermissionDestination.Settings) },
                    ) ||
                    MemoryEntryProvider(destination, goBack, navigate) ||
                    PermissionEntryProvider(destination, goBack) ||
                    SkillsEntryProvider(destination, goBack) ||
                    VoiceWakeEntryProvider(destination, goBack) ||
                    WorkFilesEntryProvider(destination, goBack)

                check(handled) { "No feature entry provider for $destination" }
            }
        },
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
    )

    if (
        assistantState.presentationVisible
    ) {
        // 与悬浮窗/锁屏一致的空闲自动收起：结果停留 5 秒后隐藏。
        LaunchedEffect(assistantState.phase) {
            if (assistantState.phase.isPresentationResultIdle()) {
                delay(AssistantResultStayMs)
                assistantSessionCoordinator.hidePresentation()
            }
        }
        // 应用内助手层：与悬浮窗/锁屏渲染同一套胶囊/面板视觉；
        // 不再用 ModalBottomSheet 承载，避免同一状态在不同宿主呈现不同样式。
        // 非获焦 Popup：点击穿透到下层应用内容，显隐完全由助手状态驱动。
        Popup(
            alignment = Alignment.BottomStart,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Box(
                // 底部沉浸式：面板自行延伸到导航栏后面；键盘高度由 imePadding 处理。
                modifier = Modifier.fillMaxWidth().imePadding(),
            ) {
                AssistantSurface(
                    state = assistantState,
                    mode = AssistantSurfaceMode.SHEET,
                    onDismiss = {
                        assistantPanelInteractor.cancelRecording()
                        assistantSessionCoordinator.hidePresentation()
                    },
                    onOpenChat = {
                        assistantPanelInteractor.cancelRecording()
                        assistantSessionCoordinator.hidePresentation()
                        returnToChat()
                    },
                    onMicToggle = onMicToggle,
                    onTextSubmit = assistantPanelInteractor::submitText,
                    recording = recording,
                    audioLevel = audioLevel,
                )
            }
        }
    }
}

private fun NavKey.navigationContentKey(): String {
    // Navigation 3 默认使用 toString()；跨 feature 的多个 Settings data object 会因此共享 Scene key。
    return "${this::class.qualifiedName}:$this"
}

private const val AssistantResultStayMs = 5_000L
