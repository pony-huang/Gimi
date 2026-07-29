package github.ponyhuang.asssistantai.app.navigation

import android.content.Context
import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.core.content.FileProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import github.ponyhuang.asssistantai.BuildConfig
import github.ponyhuang.asssistantai.feature.chat.ChatAction
import github.ponyhuang.asssistantai.feature.chat.ChatEffect
import github.ponyhuang.asssistantai.feature.chat.ChatNotice
import github.ponyhuang.asssistantai.feature.chat.ChatScaffold
import github.ponyhuang.asssistantai.feature.chat.ChatViewModel
import github.ponyhuang.asssistantai.feature.chat.ViewModelStore
import github.ponyhuang.asssistantai.domain.conversation.model.FileAttachment
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
import github.ponyhuang.asssistantai.feature.settings.SettingsRoute
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
import github.ponyhuang.asssistantai.ui.preference.PreferenceScaffold
import kotlinx.coroutines.launch
import java.io.File

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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSessionId = uiState.sessionId
    val context = LocalContext.current
    val chatNoticeConfigureChatModel = stringResource(ChatR.string.chat_notice_configure_chat_model)
    val chatNoticeModelSwitchBlocked = stringResource(ChatR.string.chat_notice_model_switch_blocked)
    val chatNoticeParallelLimit = stringResource(ChatR.string.chat_notice_parallel_limit)
    val chatNoticeActiveDeleteBlocked = stringResource(ChatR.string.chat_notice_active_delete_blocked)
    val chatNoticeMixedAttachmentCategories =
        stringResource(ChatR.string.chat_notice_mixed_attachment_categories)
    val chatNoticeChatModelUnavailable = stringResource(ChatR.string.chat_notice_chat_model_unavailable)
    val chatNoticeAttachmentCategoryUnsupported =
        stringResource(ChatR.string.chat_notice_attachment_category_unsupported)
    val chatNoticeDocumentTotalSizeLimit =
        stringResource(ChatR.string.chat_notice_document_total_size_limit)
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
        viewModel.onAction(ChatAction.RefreshConversations)
        if (requestedSessionId.isNullOrBlank()) {
            viewModel.onAction(ChatAction.RestoreOrCreateSession)
        } else {
            viewModel.onAction(ChatAction.SwitchSession(requestedSessionId))
            onRequestedSessionHandled()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is ChatEffect.ShowNotice -> when (val notice = effect.notice) {
                    ChatNotice.ConfigureChatModel -> chatNoticeConfigureChatModel
                    ChatNotice.ModelSwitchBlocked -> chatNoticeModelSwitchBlocked
                    ChatNotice.ParallelTaskLimitReached -> chatNoticeParallelLimit
                    ChatNotice.ActiveConversationDeleteBlocked -> chatNoticeActiveDeleteBlocked
                    ChatNotice.MixedAttachmentCategories -> chatNoticeMixedAttachmentCategories
                    ChatNotice.ChatModelUnavailable -> chatNoticeChatModelUnavailable
                    ChatNotice.AttachmentCategoryUnsupported -> chatNoticeAttachmentCategoryUnsupported
                    is ChatNotice.AttachmentUnsupportedOrTooLarge -> context.getString(
                        ChatR.string.chat_notice_attachment_unsupported_or_too_large,
                        notice.displayName,
                    )
                    ChatNotice.DocumentTotalSizeLimitExceeded -> chatNoticeDocumentTotalSizeLimit
                    is ChatNotice.Message -> notice.text
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                                onStop = { viewModel.onAction(ChatAction.StopStreaming) },
                                onTranscribeVoice = viewModel::transcribeVoice,
                                onToggleSpeechPlayback = { messageId, markdown ->
                                    viewModel.onAction(ChatAction.ToggleSpeechPlayback(messageId, markdown))
                                },
                                onOpenDocument = { attachment ->
                                    openDocumentAttachment(context, attachment)
                                },
                                onToolConfirmation = { confirmed ->
                                    viewModel.onAction(ChatAction.RespondToToolConfirmation(confirmed))
                                },
                                onSelectModel = { selection ->
                                    viewModel.onAction(ChatAction.SelectModel(selection))
                                },
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
                                    viewModel.onAction(ChatAction.NewConversation)
                                },
                                onLocalToolEnabledChange = { toolId, enabled ->
                                    viewModel.onAction(ChatAction.SetLocalToolEnabled(toolId, enabled))
                                },
                                onToolAccessModeChange = { mode ->
                                    viewModel.onAction(ChatAction.SetToolAccessMode(mode))
                                },
                                onMcpServerEnabledChange = { serverId, enabled ->
                                    viewModel.onAction(ChatAction.SetMcpServerEnabled(serverId, enabled))
                                },
                                onOfficialToolOpened = { toolId ->
                                    viewModel.onAction(ChatAction.LoadOfficialToolFunctions(toolId))
                                },
                                onOfficialToolFunctionEnabledChange = { toolId, functionId, enabled ->
                                    val descriptors = viewModel.uiState.value.officialToolDescriptors
                                    val supportedIds = descriptors
                                        .firstOrNull { it.id == toolId }
                                        ?.functions
                                        ?.mapTo(hashSetOf()) { it.id }
                                        .orEmpty()
                                    viewModel.onAction(
                                        ChatAction.SetOfficialFunctionEnabled(
                                            toolId = toolId,
                                            functionId = functionId,
                                            enabled = enabled,
                                            supportedFunctionIds = supportedIds,
                                        ),
                                    )
                                },
                                onOfficialToolFunctionsRetry = { toolId ->
                                    viewModel.onAction(ChatAction.LoadOfficialToolFunctions(toolId))
                                },
                            )
                        }

                        AppRoute.Settings -> PreferenceScaffold(
                            stringResource(SettingsR.string.settings_title),
                            goBack,
                        ) { modifier ->
                            SettingsRoute(
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

                        AppRoute.DefaultModelSettings -> PreferenceScaffold(
                            stringResource(ModelsettingsR.string.modelsettings_defaults_screen_title),
                            goBack,
                        ) {
                            DefaultModelSettingsRoute(modifier = it)
                        }

                        AppRoute.VoiceWakeSettings -> PreferenceScaffold(
                            stringResource(VoicewakeR.string.voicewake_screen_title),
                            goBack,
                        ) {
                            VoiceWakeSettingsRoute(modifier = it)
                        }

                        AppRoute.WorkFilesSettings -> PreferenceScaffold(
                            stringResource(WorkfilesR.string.workfiles_screen_title),
                            goBack,
                        ) {
                            WorkFilesSettingsRoute(modifier = it)
                        }

                        AppRoute.PermissionSettings -> PreferenceScaffold(
                            stringResource(PermissionsR.string.permissions_screen_title),
                            goBack,
                        ) {
                            PermissionSettingsRoute(modifier = it)
                        }

                        AppRoute.ToolAuthorizationSettings -> PreferenceScaffold(
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

                        AppRoute.ToolAuthorizationConfiguration -> PreferenceScaffold(
                            stringResource(ToolauthR.string.toolauth_configuration_title),
                            goBack,
                        ) {
                            ToolAuthorizationConfigurationRoute(modifier = it)
                        }

                        AppRoute.SkillsSettings -> PreferenceScaffold(
                            stringResource(SkillsR.string.skills_screen_title),
                            goBack,
                        ) {
                            SkillsSettingsRoute(modifier = it)
                        }

                        AppRoute.McpServerList -> PreferenceScaffold(
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

                        AppRoute.McpServerAddOptions -> PreferenceScaffold(
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

                        AppRoute.McpServerImport -> PreferenceScaffold(
                            stringResource(McpR.string.mcp_import_title),
                            goBack,
                        ) {
                            McpServerImportRoute(goBack, it)
                        }

                        is AppRoute.McpServerEditor -> PreferenceScaffold(
                            stringResource(
                                if (route.serverId == null) McpR.string.mcp_add_server
                                else McpR.string.mcp_edit_server,
                            ),
                            goBack,
                        ) {
                            McpServerEditorRoute(route.serverId, goBack, modifier = it)
                        }

                        AppRoute.ModelServiceList -> PreferenceScaffold(
                            stringResource(ModelsettingsR.string.modelsettings_list_title),
                            goBack,
                        ) {
                            ModelServiceListRoute(
                                onNavigateToDetail = { id -> backStack.add(AppRoute.ModelServiceDetail(id)) },
                                modifier = it,
                            )
                        }

                        is AppRoute.ModelServiceDetail -> PreferenceScaffold(
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

private fun openDocumentAttachment(context: Context, attachment: FileAttachment) {
    runCatching {
        val directory = File(context.cacheDir, "message-attachments").apply { mkdirs() }
        val safeName = attachment.displayName.replace(Regex("""[^\w.\-]"""), "_")
        val file = File(directory, "${attachment.id}-$safeName")
        if (!file.exists()) file.writeBytes(attachment.data)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, attachment.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(ChatR.string.chat_attachment_open_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
