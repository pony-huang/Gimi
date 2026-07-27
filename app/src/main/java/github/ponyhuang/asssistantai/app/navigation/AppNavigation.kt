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
import androidx.compose.ui.res.stringResource
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
import github.ponyhuang.asssistantai.feature.chat.R as ChatR
import github.ponyhuang.asssistantai.feature.conversation.ChatDrawer
import github.ponyhuang.asssistantai.feature.mcp.McpServerAddOptionsScreen
import github.ponyhuang.asssistantai.feature.mcp.McpServerEditorRoute
import github.ponyhuang.asssistantai.feature.mcp.McpServerImportRoute
import github.ponyhuang.asssistantai.feature.mcp.McpServerListRoute
import github.ponyhuang.asssistantai.feature.mcp.R as McpR
import github.ponyhuang.asssistantai.feature.modelsettings.defaults.DefaultModelSettingsRoute
import github.ponyhuang.asssistantai.feature.modelsettings.detail.LLMModelSettingDetailRoute
import github.ponyhuang.asssistantai.feature.modelsettings.list.ModelServiceListRoute
import github.ponyhuang.asssistantai.feature.modelsettings.R as ModelsettingsR
import github.ponyhuang.asssistantai.feature.permissions.PermissionSettingsRoute
import github.ponyhuang.asssistantai.feature.permissions.R as PermissionsR
import github.ponyhuang.asssistantai.feature.settings.R as SettingsR
import github.ponyhuang.asssistantai.feature.skills.SkillsSettingsRoute
import github.ponyhuang.asssistantai.feature.skills.R as SkillsR
import github.ponyhuang.asssistantai.feature.toolauthorization.ToolAuthorizationConfigurationRoute
import github.ponyhuang.asssistantai.feature.toolauthorization.ToolAuthorizationRoute
import github.ponyhuang.asssistantai.feature.toolauthorization.R as ToolauthR
import github.ponyhuang.asssistantai.feature.voicewake.VoiceWakeSettingsRoute
import github.ponyhuang.asssistantai.feature.voicewake.R as VoicewakeR
import github.ponyhuang.asssistantai.feature.workfiles.WorkFilesSettingsRoute
import github.ponyhuang.asssistantai.feature.workfiles.R as WorkfilesR
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
    val chatNoticeConfigureChatModel = stringResource(ChatR.string.chat_notice_configure_chat_model)
    val chatNoticeModelSwitchBlocked = stringResource(ChatR.string.chat_notice_model_switch_blocked)
    val chatNoticeParallelLimit = stringResource(ChatR.string.chat_notice_parallel_limit)
    val chatNoticeActiveDeleteBlocked = stringResource(ChatR.string.chat_notice_active_delete_blocked)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(AppRoute.Chat)
    val goBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
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
            ChatNotice.ConfigureChatModel -> chatNoticeConfigureChatModel
            ChatNotice.ModelSwitchBlocked -> chatNoticeModelSwitchBlocked
            ChatNotice.ParallelTaskLimitReached -> chatNoticeParallelLimit
            ChatNotice.ActiveConversationDeleteBlocked -> chatNoticeActiveDeleteBlocked
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
        conversationTaskStatuses = uiState.conversationTaskStatuses,
        isConversationSwitchEnabled = true,
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
                                        chatNoticeModelSwitchBlocked,
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

                        AppRoute.Settings -> SettingsScaffold(
                            stringResource(SettingsR.string.settings_title),
                            goBack,
                        ) { modifier ->
                            SettingsScreen(
                                appVersionName = BuildConfig.VERSION_NAME,
                                onNavigateToModelService = { backStack.add(AppRoute.ModelServiceList) },
                                onNavigateToDefaultModels = { backStack.add(AppRoute.DefaultModelSettings) },
                                onNavigateToVoiceWake = { backStack.add(AppRoute.VoiceWakeSettings) },
                                onNavigateToMcpServers = { backStack.add(AppRoute.McpServerList) },
                                onNavigateToSkills = { backStack.add(AppRoute.SkillsSettings) },
                                onNavigateToWorkFiles = { backStack.add(AppRoute.WorkFilesSettings) },
                                onNavigateToPermissions = { backStack.add(AppRoute.PermissionSettings) },
                                onNavigateToToolAuthorization = {
                                    backStack.add(AppRoute.ToolAuthorizationSettings)
                                },
                                modifier = modifier,
                            )
                        }

                        AppRoute.DefaultModelSettings -> SettingsScaffold(
                            stringResource(ModelsettingsR.string.modelsettings_defaults_screen_title),
                            goBack,
                        ) {
                            DefaultModelSettingsRoute(modifier = it)
                        }

                        AppRoute.VoiceWakeSettings -> SettingsScaffold(
                            stringResource(VoicewakeR.string.voicewake_screen_title),
                            goBack,
                        ) {
                            VoiceWakeSettingsRoute(modifier = it)
                        }

                        AppRoute.WorkFilesSettings -> SettingsScaffold(
                            stringResource(WorkfilesR.string.workfiles_screen_title),
                            goBack,
                        ) {
                            WorkFilesSettingsRoute(modifier = it)
                        }

                        AppRoute.PermissionSettings -> SettingsScaffold(
                            stringResource(PermissionsR.string.permissions_screen_title),
                            goBack,
                        ) {
                            PermissionSettingsRoute(modifier = it)
                        }

                        AppRoute.ToolAuthorizationSettings -> SettingsScaffold(
                            stringResource(ToolauthR.string.toolauth_screen_title),
                            goBack,
                        ) {
                            ToolAuthorizationRoute(
                                onNavigateToConfiguration = {
                                    backStack.add(AppRoute.ToolAuthorizationConfiguration)
                                },
                                modifier = it,
                            )
                        }

                        AppRoute.ToolAuthorizationConfiguration -> SettingsScaffold(
                            stringResource(ToolauthR.string.toolauth_configuration_title),
                            goBack,
                        ) {
                            ToolAuthorizationConfigurationRoute(modifier = it)
                        }

                        AppRoute.SkillsSettings -> SettingsScaffold(
                            stringResource(SkillsR.string.skills_screen_title),
                            goBack,
                        ) {
                            SkillsSettingsRoute(modifier = it)
                        }

                        AppRoute.McpServerList -> SettingsScaffold(
                            title = stringResource(McpR.string.mcp_list_title),
                            onBack = goBack,
                            actions = {
                                IconButton(onClick = { backStack.add(AppRoute.McpServerAddOptions) }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(McpR.string.mcp_add_server),
                                    )
                                }
                            },
                        ) { modifier ->
                            McpServerListRoute(
                                onNavigateToEditor = { backStack.add(AppRoute.McpServerEditor(it)) },
                                onCreateServer = { backStack.add(AppRoute.McpServerEditor()) },
                                onImportServers = { backStack.add(AppRoute.McpServerImport) },
                                modifier = modifier,
                            )
                        }

                        AppRoute.McpServerAddOptions -> SettingsScaffold(
                            stringResource(McpR.string.mcp_add_options_title),
                            goBack,
                        ) {
                            McpServerAddOptionsScreen(
                                onCreate = {
                                    backStack.removeLastOrNull()
                                    backStack.add(AppRoute.McpServerEditor())
                                },
                                onImport = {
                                    backStack.removeLastOrNull()
                                    backStack.add(AppRoute.McpServerImport)
                                },
                                modifier = it,
                            )
                        }

                        AppRoute.McpServerImport -> SettingsScaffold(
                            stringResource(McpR.string.mcp_import_title),
                            goBack,
                        ) {
                            McpServerImportRoute(goBack, it)
                        }

                        is AppRoute.McpServerEditor -> SettingsScaffold(
                            stringResource(
                                if (route.serverId == null) McpR.string.mcp_add_server
                                else McpR.string.mcp_edit_server,
                            ),
                            goBack,
                        ) {
                            McpServerEditorRoute(route.serverId, goBack, modifier = it)
                        }

                        AppRoute.ModelServiceList -> SettingsScaffold(
                            stringResource(ModelsettingsR.string.modelsettings_list_title),
                            goBack,
                        ) {
                            ModelServiceListRoute(
                                onNavigateToDetail = { id -> backStack.add(AppRoute.ModelServiceDetail(id)) },
                                modifier = it,
                            )
                        }

                        is AppRoute.ModelServiceDetail -> SettingsScaffold(
                            stringResource(ModelsettingsR.string.modelsettings_detail_title),
                            goBack,
                        ) {
                            LLMModelSettingDetailRoute(route.serviceId, goBack, modifier = it)
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
