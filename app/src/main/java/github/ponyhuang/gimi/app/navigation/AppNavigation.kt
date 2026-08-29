package github.ponyhuang.gimi.app.navigation

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import github.ponyhuang.gimi.BuildConfig
import github.ponyhuang.gimi.feature.chat.ChatAction
import github.ponyhuang.gimi.feature.chat.ChatRoute
import github.ponyhuang.gimi.feature.chat.ChatSearchResultsRoute
import github.ponyhuang.gimi.feature.chat.ChatViewModel
import github.ponyhuang.gimi.feature.chat.ViewModelStore
import github.ponyhuang.gimi.feature.chat.R as ChatR
import github.ponyhuang.gimi.feature.chat.ChatDrawer
import github.ponyhuang.gimi.feature.mcp.McpServerAddOptionsRoute
import github.ponyhuang.gimi.feature.mcp.McpServerEditorRoute
import github.ponyhuang.gimi.feature.mcp.McpServerImportRoute
import github.ponyhuang.gimi.feature.mcp.McpServerListRoute
import github.ponyhuang.gimi.feature.modelsettings.defaults.DefaultModelSettingsRoute
import github.ponyhuang.gimi.feature.modelsettings.detail.LLMModelSettingDetailRoute
import github.ponyhuang.gimi.feature.modelsettings.list.ModelServiceListRoute
import github.ponyhuang.gimi.feature.permissions.PermissionSettingsRoute
import github.ponyhuang.gimi.feature.recommendation.RecommendationSettingsRoute
import github.ponyhuang.gimi.feature.memory.MemorySettingsRoute
import github.ponyhuang.gimi.feature.settings.SettingsRoute
import github.ponyhuang.gimi.feature.skills.SkillsSettingsRoute
import github.ponyhuang.gimi.feature.toolauthorization.ToolAuthorizationConfigurationRoute
import github.ponyhuang.gimi.feature.toolauthorization.ToolAuthorizationRoute
import github.ponyhuang.gimi.feature.plugin.PluginConfigRoute
import github.ponyhuang.gimi.feature.plugin.PluginSettingsRoute
import github.ponyhuang.gimi.feature.voicewake.VoiceWakeSettingsRoute
import github.ponyhuang.gimi.feature.workfiles.WorkFilesSettingsRoute
import github.ponyhuang.gimi.ui.navigation.AppRoute
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import kotlinx.coroutines.launch

/** App-level composition root. Feature modules never navigate to one another directly. */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
// ChatNotice.AttachmentUnsupportedOrTooLarge 的 displayName 为运行时参数，
// 文案只能在 effect 消费时解析，豁免该 lint。
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun MainScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    requestedSessionId: String? = null,
    onRequestedSessionHandled: () -> Unit = {},
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSessionId = uiState.sessionId
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(AppRoute.Chat)
    val goBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
    val returnToChat = {
        // 不用 removeLast：minSdk 34 下 lint 会报 NewApi（API 35 的 java.util.List#removeLast）。
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    LaunchedEffect(sharedMediaUris) {
        if (sharedMediaUris.isNotEmpty()) returnToChat()
    }

    LaunchedEffect(requestedSessionId) {
        viewModel.onAction(ChatAction.RefreshConversations)
        if (requestedSessionId.isNullOrBlank()) {
            viewModel.onAction(ChatAction.RestoreOrCreateSession)
        } else {
            viewModel.onAction(ChatAction.SwitchSession(requestedSessionId))
            onRequestedSessionHandled()
        }
    }

    ChatDrawer(
        drawerState = drawerState,
        conversations = uiState.conversations,
        currentSessionId = currentSessionId,
        conversationTaskStatuses = uiState.conversationTaskStatuses,
        isConversationSwitchEnabled = true,
        onConversationClick = { conversation ->
            viewModel.onAction(ChatAction.SwitchSession(conversation.id))
            returnToChat()
            scope.launch { drawerState.close() }
        },
        onDeleteClick = { conversation ->
            viewModel.onAction(ChatAction.DeleteConversation(conversation.id))
        },
        onSettingsClick = {
            returnToChat()
            backStack.add(AppRoute.Settings)
            scope.launch { drawerState.close() }
        },
        // 未显式切换时跟随系统，与 MainActivity 的主题解析规则保持一致。
        darkTheme = uiState.darkThemeOverride ?: isSystemInDarkTheme(),
        onDarkThemeChange = { enabled ->
            viewModel.onAction(ChatAction.SetDarkTheme(enabled))
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = goBack,
            // 将 hiltViewModel() 绑定到单个 NavEntry。否则插件配置页会回退到 Activity
            // 级 store，切换插件时复用同一 ViewModel，可能把当前表单保存到错误插件。
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { route ->
                NavEntry(route) {
                    when (route) {
                        AppRoute.Chat -> ViewModelStore(route) {
                            ChatRoute(
                                onReturnToChat = returnToChat,
                                onOpenSettings = { backStack.add(AppRoute.Settings) },
                                onConfigureModels = { backStack.add(AppRoute.ModelServiceList) },
                                onShowAllLocalFiles = { sessionId, responseId ->
                                    backStack.add(
                                        AppRoute.ChatSearchResults(
                                            sessionId = sessionId,
                                            responseId = responseId,
                                        ),
                                    )
                                },
                                sharedMediaUris = sharedMediaUris,
                                onSharedMediaConsumed = onSharedMediaConsumed,
                            )
                        }

                        is AppRoute.ChatSearchResults -> ChatSearchResultsRoute(
                            sessionId = route.sessionId,
                            responseId = route.responseId,
                            onBack = goBack,
                            onOpenFile = { file -> openLocalFile(context, file) },
                        )

                        AppRoute.Settings -> SettingsRoute(
                            appVersionName = BuildConfig.VERSION_NAME,
                            onBack = goBack,
                            onNavigateToModelService = { backStack.add(AppRoute.ModelServiceList) },
                            onNavigateToDefaultModels = { backStack.add(AppRoute.DefaultModelSettings) },
                            onNavigateToVoiceWake = { backStack.add(AppRoute.VoiceWakeSettings) },
                            onNavigateToMcpServers = { backStack.add(AppRoute.McpServerList) },
                            onNavigateToPlugins = { backStack.add(AppRoute.PluginSettings) },
                            onNavigateToSkills = { backStack.add(AppRoute.SkillsSettings) },
                            onNavigateToWorkFiles = { backStack.add(AppRoute.WorkFilesSettings) },
                            onNavigateToPermissions = { backStack.add(AppRoute.PermissionSettings) },
                            onNavigateToToolAuthorization = {
                                backStack.add(AppRoute.ToolAuthorizationSettings)
                            },
                            onNavigateToRecommendations = {
                                backStack.add(AppRoute.RecommendationSettings)
                            },
                            onNavigateToMemory = { backStack.add(AppRoute.MemorySettings) },
                        )

                        AppRoute.MemorySettings -> MemorySettingsRoute(
                            onBack = goBack,
                        )

                        AppRoute.RecommendationSettings -> RecommendationSettingsRoute(
                            onBack = goBack,
                            onOpenPermissions = { backStack.add(AppRoute.PermissionSettings) },
                        )

                        AppRoute.DefaultModelSettings -> DefaultModelSettingsRoute(onBack = goBack)

                        AppRoute.VoiceWakeSettings -> VoiceWakeSettingsRoute(onBack = goBack)

                        AppRoute.WorkFilesSettings -> WorkFilesSettingsRoute(onBack = goBack)

                        AppRoute.PermissionSettings -> PermissionSettingsRoute(onBack = goBack)

                        AppRoute.ToolAuthorizationSettings -> ToolAuthorizationRoute(
                            onBack = goBack,
                            onNavigateToConfiguration = {
                                backStack.add(AppRoute.ToolAuthorizationConfiguration)
                            },
                        )

                        AppRoute.ToolAuthorizationConfiguration -> ToolAuthorizationConfigurationRoute(
                            onBack = goBack,
                        )

                        AppRoute.PluginSettings -> PluginSettingsRoute(
                            onBack = goBack,
                            onNavigateToConfig = { pluginId ->
                                backStack.add(AppRoute.PluginConfig(pluginId))
                            },
                        )

                        is AppRoute.PluginConfig -> PluginConfigRoute(
                            pluginId = route.pluginId,
                            onBack = goBack,
                        )

                        AppRoute.SkillsSettings -> SkillsSettingsRoute(onBack = goBack)

                        AppRoute.McpServerList -> McpServerListRoute(
                            onBack = goBack,
                            onAddServer = { backStack.add(AppRoute.McpServerAddOptions) },
                            onNavigateToEditor = { backStack.add(AppRoute.McpServerEditor(it)) },
                            onCreateServer = { backStack.add(AppRoute.McpServerEditor()) },
                            onImportServers = { backStack.add(AppRoute.McpServerImport) },
                        )

                        AppRoute.McpServerAddOptions -> McpServerAddOptionsRoute(
                            onBack = goBack,
                            onCreate = {
                                backStack.removeLastOrNull()
                                backStack.add(AppRoute.McpServerEditor())
                            },
                            onImport = {
                                backStack.removeLastOrNull()
                                backStack.add(AppRoute.McpServerImport)
                            },
                        )

                        AppRoute.McpServerImport -> McpServerImportRoute(onBack = goBack)

                        is AppRoute.McpServerEditor -> McpServerEditorRoute(
                            serverId = route.serverId,
                            onBack = goBack,
                        )

                        AppRoute.ModelServiceList -> ModelServiceListRoute(
                            onBack = goBack,
                            onNavigateToDetail = { id -> backStack.add(AppRoute.ModelServiceDetail(id)) },
                        )

                        is AppRoute.ModelServiceDetail -> LLMModelSettingDetailRoute(
                            serviceId = route.serviceId,
                            onBack = goBack,
                        )
                    }
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
    }
}

private fun openLocalFile(context: android.content.Context, file: LocalFileReference) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(android.net.Uri.parse(file.contentUri), file.mimeType)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(ChatR.string.chat_attachment_open_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}