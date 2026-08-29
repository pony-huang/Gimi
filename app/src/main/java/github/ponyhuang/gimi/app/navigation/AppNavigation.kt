package github.ponyhuang.gimi.app.navigation

import android.content.Context
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.core.content.FileProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import github.ponyhuang.gimi.BuildConfig
import github.ponyhuang.gimi.feature.chat.ChatAction
import github.ponyhuang.gimi.feature.chat.ChatEffect
import github.ponyhuang.gimi.feature.chat.ChatNotice
import github.ponyhuang.gimi.feature.chat.ChatScaffold
import github.ponyhuang.gimi.feature.chat.ChatViewModel
import github.ponyhuang.gimi.feature.chat.ChatRecommendationsViewModel
import github.ponyhuang.gimi.feature.chat.LocalFileSearchResultsScreen
import github.ponyhuang.gimi.feature.chat.ViewModelStore
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import github.ponyhuang.gimi.feature.chat.R as ChatR
import github.ponyhuang.gimi.feature.conversation.ChatDrawer
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
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
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
    recommendationViewModel: ChatRecommendationsViewModel = hiltViewModel(),
    requestedSessionId: String? = null,
    onRequestedSessionHandled: () -> Unit = {},
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recommendations by recommendationViewModel.recommendations.collectAsStateWithLifecycle()
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
    val chatNoticeMemorySearchFailed = stringResource(ChatR.string.chat_notice_memory_search_failed)
    val chatNoticeMemoryWriteFailed = stringResource(ChatR.string.chat_notice_memory_write_failed)
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
                    is ChatNotice.McpServerSkipped -> context.getString(
                        ChatR.string.chat_notice_mcp_server_skipped,
                        notice.serverName,
                    )
                    ChatNotice.MemorySearchFailed -> chatNoticeMemorySearchFailed
                    ChatNotice.MemoryWriteFailed -> chatNoticeMemoryWriteFailed
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
                            ChatScaffold(
                                state = uiState,
                                recommendations = recommendations,
                                onRecommendationClick = { prompt ->
                                    viewModel.send(prompt, emptyList())
                                },
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
                                onOpenLocalFile = { file ->
                                    openLocalFile(context, file)
                                },
                                onShowAllLocalFiles = { responseId ->
                                    backStack.add(
                                        AppRoute.ChatSearchResults(
                                            sessionId = currentSessionId,
                                            responseId = responseId,
                                        ),
                                    )
                                },
                                onToolConfirmation = { confirmed ->
                                    viewModel.onAction(ChatAction.RespondToToolConfirmation(confirmed))
                                },
                                onToolConfirmationAlwaysAllow = {
                                    viewModel.onAction(
                                        ChatAction.RespondToToolConfirmation(
                                            confirmed = true,
                                            alwaysAllow = true,
                                        ),
                                    )
                                },
                                onFullAccessChange = { enabled ->
                                    viewModel.onAction(ChatAction.SetFullAccess(enabled))
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
                                sharedMediaUris = sharedMediaUris.takeIf {
                                    currentSessionId.isNotBlank() && !uiState.isInitializing
                                }.orEmpty(),
                                onSharedMediaConsumed = onSharedMediaConsumed,
                            )
                        }

                        is AppRoute.ChatSearchResults -> {
                            val result = uiState.messages
                                .asSequence()
                                .flatMap { message -> message.functionResponses.asSequence() }
                                .firstOrNull { response -> response.id == route.responseId }
                                ?.localFileSearchResult
                                .takeIf { currentSessionId == route.sessionId }
                            LocalFileSearchResultsScreen(
                                result = result,
                                onBack = goBack,
                                onOpenFile = { file -> openLocalFile(context, file) },
                            )
                        }

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

private fun openDocumentAttachment(context: Context, attachment: FileAttachment) {
    runCatching {
        val directory = File(context.cacheDir, "message-attachments").apply { mkdirs() }
        val safeName = attachment.displayName.replace(Regex("""[^\w.\-]"""), "_")
        val file = File(directory, "${attachment.id}-$safeName")
        // Copied under the original name so external viewers still see a real extension. The
        // bytes stream from the persisted payload rather than being held in memory.
        if (!file.exists()) {
            val source = attachment.payloadReference?.let(::File)
            if (source != null && source.isFile) {
                source.copyTo(file, overwrite = true)
            } else {
                file.writeBytes(
                    requireNotNull(attachment.inlineData) { "Attachment payload is unavailable" },
                )
            }
        }
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

private fun openLocalFile(context: Context, file: LocalFileReference) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(android.net.Uri.parse(file.contentUri), file.mimeType)
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
