package github.ponyhuang.asssistantai.app.navigation

import android.widget.Toast
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import github.ponyhuang.asssistantai.BuildConfig
import github.ponyhuang.asssistantai.feature.chat.ChatNotice
import github.ponyhuang.asssistantai.feature.chat.ChatScaffold
import github.ponyhuang.asssistantai.feature.chat.ChatViewModel
import github.ponyhuang.asssistantai.feature.chat.ViewModelStore
import github.ponyhuang.asssistantai.feature.conversation.ChatDrawer
import github.ponyhuang.asssistantai.feature.mcp.McpServerAddOptionsScreen
import github.ponyhuang.asssistantai.feature.mcp.McpServerEditorRoute
import github.ponyhuang.asssistantai.feature.mcp.McpServerImportRoute
import github.ponyhuang.asssistantai.feature.mcp.McpServerListRoute
import github.ponyhuang.asssistantai.feature.modelsettings.defaults.DefaultModelSettingsRoute
import github.ponyhuang.asssistantai.feature.modelsettings.detail.LLMModelServiceDetailRoute
import github.ponyhuang.asssistantai.feature.modelsettings.list.ModelServiceListRoute
import github.ponyhuang.asssistantai.feature.permissions.PermissionSettingsRoute
import github.ponyhuang.asssistantai.feature.toolauthorization.ToolAuthorizationRoute
import github.ponyhuang.asssistantai.feature.voicewake.VoiceWakeSettingsRoute
import github.ponyhuang.asssistantai.feature.workfiles.WorkFilesSettingsRoute
import github.ponyhuang.asssistantai.ui.navigation.AppRoute
import github.ponyhuang.asssistantai.ui.navigation.SettingsScaffold
import github.ponyhuang.asssistantai.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/** App-level composition root. Feature modules never navigate to one another directly. */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun MainScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    requestedSessionId: String? = null,
    onRequestedSessionHandled: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSessionId = uiState.sessionId
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(AppRoute.Chat)
    val goBack: () -> Unit = { backStack.removeLastOrNull() }
    val returnToChat = {
        while (backStack.size > 1) backStack.removeLast()
    }

    LaunchedEffect(requestedSessionId) {
        viewModel.refreshConversations()
        if (requestedSessionId.isNullOrBlank()) {
            viewModel.restoreOrCreateSession()
        } else {
            viewModel.switchSession(requestedSessionId)
            onRequestedSessionHandled()
        }
    }

    LaunchedEffect(uiState.notice) {
        val message = when (val notice = uiState.notice) {
            ChatNotice.ConfigureChatModel -> "请先在模型服务中配置并启用聊天模型"
            ChatNotice.ModelSwitchBlocked -> "正在生成回复，完成后再切换模型"
            is ChatNotice.Message -> notice.text
            null -> return@LaunchedEffect
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeNotice()
    }

    ChatDrawer(
        drawerState = drawerState,
        conversations = uiState.conversations,
        currentSessionId = currentSessionId,
        isConversationSwitchEnabled = !uiState.isAgentMutationBlocked,
        onConversationClick = { conversation ->
            viewModel.switchSession(conversation.id)
            returnToChat()
            scope.launch { drawerState.close() }
        },
        onDeleteClick = { conversation -> viewModel.deleteConversation(conversation.id) },
        onSettingsClick = {
            returnToChat()
            backStack.add(AppRoute.Settings)
            scope.launch { drawerState.close() }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = goBack,
            entryProvider = { route ->
                NavEntry(route) {
                    when (route) {
                        AppRoute.Chat -> ViewModelStore(route) {
                            ChatScaffold(
                                state = uiState,
                                partChannelProvider = viewModel::partChannelFor,
                                onSend = viewModel::send,
                                onStop = viewModel::stopStreaming,
                                onTranscribeVoice = viewModel::transcribeVoice,
                                onToggleSpeechPlayback = viewModel::toggleSpeechPlayback,
                                onToolConfirmation = viewModel::respondToToolConfirmation,
                                onSelectModel = viewModel::selectModel,
                                onModelSwitchBlocked = {
                                    Toast.makeText(
                                        context,
                                        "正在生成回复，完成后再切换模型",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenSettings = { backStack.add(AppRoute.Settings) },
                                onConfigureModels = { backStack.add(AppRoute.ModelServiceList) },
                                onNewConversation = {
                                    viewModel.reset()
                                },
                            )
                        }

                        AppRoute.Settings -> SettingsScaffold("设置", goBack) { modifier ->
                            SettingsScreen(
                                appVersionName = BuildConfig.VERSION_NAME,
                                onNavigateToModelService = { backStack.add(AppRoute.ModelServiceList) },
                                onNavigateToDefaultModels = { backStack.add(AppRoute.DefaultModelSettings) },
                                onNavigateToVoiceWake = { backStack.add(AppRoute.VoiceWakeSettings) },
                                onNavigateToMcpServers = { backStack.add(AppRoute.McpServerList) },
                                onNavigateToWorkFiles = { backStack.add(AppRoute.WorkFilesSettings) },
                                onNavigateToPermissions = { backStack.add(AppRoute.PermissionSettings) },
                                onNavigateToToolAuthorization = {
                                    backStack.add(AppRoute.ToolAuthorizationSettings)
                                },
                                modifier = modifier,
                            )
                        }

                        AppRoute.DefaultModelSettings -> SettingsScaffold("默认模型", goBack) {
                            DefaultModelSettingsRoute(modifier = it)
                        }

                        AppRoute.VoiceWakeSettings -> SettingsScaffold("语音唤醒", goBack) {
                            VoiceWakeSettingsRoute(modifier = it)
                        }

                        AppRoute.WorkFilesSettings -> SettingsScaffold("工作文件", goBack) {
                            WorkFilesSettingsRoute(modifier = it)
                        }

                        AppRoute.PermissionSettings -> SettingsScaffold("权限管理", goBack) {
                            PermissionSettingsRoute(modifier = it)
                        }

                        AppRoute.ToolAuthorizationSettings -> SettingsScaffold("工具授权", goBack) {
                            ToolAuthorizationRoute(modifier = it)
                        }

                        AppRoute.McpServerList -> SettingsScaffold(
                            title = "MCP 服务器",
                            onBack = goBack,
                            actions = {
                                IconButton(onClick = { backStack.add(AppRoute.McpServerAddOptions) }) {
                                    Icon(Icons.Default.Add, contentDescription = "添加 MCP 服务器")
                                }
                            },
                        ) { modifier ->
                            McpServerListRoute(
                                onNavigateToEditor = { backStack.add(AppRoute.McpServerEditor(it)) },
                                modifier = modifier,
                            )
                        }

                        AppRoute.McpServerAddOptions -> SettingsScaffold("添加 MCP 服务", goBack) {
                            McpServerAddOptionsScreen(
                                onCreate = { backStack.add(AppRoute.McpServerEditor()) },
                                onImport = { backStack.add(AppRoute.McpServerImport) },
                                modifier = it,
                            )
                        }

                        AppRoute.McpServerImport -> SettingsScaffold("导入 MCP 配置", goBack) {
                            McpServerImportRoute(goBack, it)
                        }

                        is AppRoute.McpServerEditor -> SettingsScaffold(
                            if (route.serverId == null) "添加 MCP 服务器" else "编辑 MCP 服务器",
                            goBack,
                        ) {
                            McpServerEditorRoute(route.serverId, goBack, modifier = it)
                        }

                        AppRoute.ModelServiceList -> SettingsScaffold("模型服务", goBack) {
                            ModelServiceListRoute(
                                onNavigateToDetail = { id -> backStack.add(AppRoute.ModelServiceDetail(id)) },
                                modifier = it,
                            )
                        }

                        is AppRoute.ModelServiceDetail -> SettingsScaffold("服务详情", goBack) {
                            LLMModelServiceDetailRoute(route.serviceId, goBack, modifier = it)
                        }
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
