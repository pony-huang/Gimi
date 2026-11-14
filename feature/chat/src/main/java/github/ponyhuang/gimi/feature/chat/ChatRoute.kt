package github.ponyhuang.gimi.feature.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.core.storage.AndroidAppDirectoryResolver
import github.ponyhuang.gimi.feature.chat.realtime.RealtimeVoiceHost
import github.ponyhuang.gimi.core.storage.ShareableFileUriFactory
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import java.io.File

/**
 * 自包含的 Chat 入口。该 Composable 拥有 [ChatViewModel]、[ChatRecommendationsViewModel]、
 * 抽屉、效果监听、文档/本地文件打开与搜索结果子页面；app 只传入跨 feature 跳转回调。
 */
@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
// ChatNotice.AttachmentUnsupportedOrTooLarge 的 displayName 为运行时参数，
// 文案只能在 effect 消费时解析，豁免该 lint。
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ChatRoute(
    onReturnToChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenRuntimeEnvironment: () -> Unit,
    onConfigureModels: () -> Unit,
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
) {
    // 保持聊天状态与宿主 Activity 生命周期一致。
    val viewModel: ChatViewModel = activityScopedChatViewModel()
    val recommendationViewModel: ChatRecommendationsViewModel = hiltViewModel()
    val exportViewModel: SessionExportViewModel = hiltViewModel()
    val exportState by exportViewModel.uiState.collectAsStateWithLifecycle()
    SessionExportEffects(exportViewModel)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recommendations by recommendationViewModel.recommendations.collectAsStateWithLifecycle()
    val currentSessionId = uiState.sessionId
    val context = LocalContext.current
    val chatNoticeConfigureChatModel = stringResource(R.string.chat_notice_configure_chat_model)
    val chatNoticeModelSwitchBlocked = stringResource(R.string.chat_notice_model_switch_blocked)
    val chatNoticeParallelLimit = stringResource(R.string.chat_notice_parallel_limit)
    val chatNoticeCurrentConversationBusy =
        stringResource(R.string.chat_notice_current_conversation_busy)
    val chatNoticeDeleteFailed = stringResource(R.string.chat_notice_delete_failed)
    val chatNoticeActiveDeleteBlocked = stringResource(R.string.chat_notice_active_delete_blocked)
    val chatNoticeCurrentDeleteProtected = stringResource(R.string.chat_drawer_current_session_protected)
    val chatNoticeMixedAttachmentCategories =
        stringResource(R.string.chat_notice_mixed_attachment_categories)
    val chatNoticeChatModelUnavailable = stringResource(R.string.chat_notice_chat_model_unavailable)
    val chatNoticeAttachmentCategoryUnsupported =
        stringResource(R.string.chat_notice_attachment_category_unsupported)
    val chatNoticeDocumentTotalSizeLimit =
        stringResource(R.string.chat_notice_document_total_size_limit)
    val chatNoticeMemorySearchFailed = stringResource(R.string.chat_notice_memory_search_failed)
    val chatNoticeMemoryWriteFailed = stringResource(R.string.chat_notice_memory_write_failed)
    // 抽屉目标开关状态：手势提交（预测性返回、拖拽、遮罩点击）经 onOpenChange 回写，
    // 程序化开关（入口点击、返回收尾）直接赋值；动画收尾由 PredictiveBackDrawer 统一处理。
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var searchHadFocus by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    // 语音页外部打开请求计数：抽屉"语音通话"入口递增，RealtimeVoiceHost 消费后清零。
    var voiceOpenRequest by rememberSaveable { mutableStateOf(0) }
    // 语音页是全屏覆盖层：其可见期间关闭抽屉手势，避免边缘滑动被覆盖层误捕获；
    // 文字聊天页保持默认手势行为（边缘右滑打开抽屉）。
    var voicePageVisible by remember { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(ChatAction.ResumeChat)
        onPauseOrDispose {}
    }

    LaunchedEffect(viewModel) {
        viewModel.onAction(ChatAction.RefreshConversations)
        viewModel.onAction(ChatAction.RestoreOrCreateSession)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is ChatEffect.OpenA2uiUrl) {
                val uri = Uri.parse(effect.url)
                if (uri.scheme in setOf("http", "https")) {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    catch (_: android.content.ActivityNotFoundException) {
                        Toast.makeText(context, context.getString(R.string.chat_a2ui_link_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                return@collect
            }
            val message = when (effect) {
                is ChatEffect.OpenA2uiUrl -> error("URL effect already handled")
                is ChatEffect.ShowNotice -> when (val notice = effect.notice) {
                    ChatNotice.ConfigureChatModel -> chatNoticeConfigureChatModel
                    ChatNotice.ModelSwitchBlocked -> chatNoticeModelSwitchBlocked
                    ChatNotice.ParallelTaskLimitReached -> chatNoticeParallelLimit
                    ChatNotice.CurrentConversationBusy -> chatNoticeCurrentConversationBusy
                    ChatNotice.ActiveConversationDeleteBlocked -> chatNoticeActiveDeleteBlocked
                    ChatNotice.CurrentConversationDeleteProtected -> chatNoticeCurrentDeleteProtected
                    ChatNotice.ConversationDeleteFailed -> chatNoticeDeleteFailed
                    ChatNotice.MixedAttachmentCategories -> chatNoticeMixedAttachmentCategories
                    ChatNotice.ChatModelUnavailable -> chatNoticeChatModelUnavailable
                    ChatNotice.AttachmentCategoryUnsupported -> chatNoticeAttachmentCategoryUnsupported
                    is ChatNotice.AttachmentUnsupportedOrTooLarge -> context.getString(
                        R.string.chat_notice_attachment_unsupported_or_too_large,
                        notice.displayName,
                    )
                    ChatNotice.DocumentTotalSizeLimitExceeded -> chatNoticeDocumentTotalSizeLimit
                    is ChatNotice.McpServerSkipped -> context.getString(
                        R.string.chat_notice_mcp_server_skipped,
                        notice.serverName,
                    )
                    is ChatNotice.RemoteAgentSkipped -> context.getString(
                        R.string.chat_notice_remote_agent_skipped,
                        notice.agentName,
                    )
                    ChatNotice.MemorySearchFailed -> chatNoticeMemorySearchFailed
                    ChatNotice.MemoryWriteFailed -> chatNoticeMemoryWriteFailed
                    is ChatNotice.VoiceFailed -> context.getString(
                        voiceFailureMessageRes(notice.failure.kind, R.string.chat_voice_playback_failed),
                    )
                    is ChatNotice.Message -> notice.text
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = drawerOpen && uiState.recentConversations.isSelecting && !uiState.recentConversations.isDeleting) {
        viewModel.onAction(ChatAction.RecentConversations(RecentConversationsAction.FinishSelection))
    }
    // 输入法显示时让系统先处理返回；抽屉自身的预测性返回在搜索期间让出。
    BackHandler(
        enabled = drawerOpen && uiState.recentConversations.isSearching &&
            !uiState.recentConversations.isSelecting && !imeVisible && !uiState.recentConversations.isDeleting,
    ) {
        viewModel.onAction(ChatAction.RecentConversations(RecentConversationsAction.CloseSearch))
    }
    LaunchedEffect(drawerOpen, uiState.recentConversations.isSearching, uiState.recentConversations.isSelecting) {
        if (searchHadFocus && (!drawerOpen || !uiState.recentConversations.isSearching || uiState.recentConversations.isSelecting)) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            searchHadFocus = false
        }
    }
    LaunchedEffect(drawerOpen) {
        if (!drawerOpen) {
            viewModel.onAction(ChatAction.RecentConversations(RecentConversationsAction.DrawerClosed))
        }
    }

    ChatDrawer(
        open = drawerOpen,
        gesturesEnabled = !voicePageVisible,
        onOpenChange = { drawerOpen = it },
        searchFocusRequester = searchFocusRequester,
        onSearchExpanded = {
            if (drawerOpen && uiState.recentConversations.isSearching && !uiState.recentConversations.isSelecting) {
                searchFocusRequester.requestFocus()
                searchHadFocus = true
                keyboard?.show()
            }
        },
        onSearchSubmit = { keyboard?.hide() },
        conversations = uiState.conversations,
        currentSessionId = currentSessionId,
        conversationTaskStatuses = uiState.conversationTaskStatuses,
        onConversationClick = { conversation ->
            viewModel.onAction(ChatAction.SwitchSession(conversation.id))
            onReturnToChat()
            drawerOpen = false
        },
        recentState = uiState.recentConversations,
        onRecentAction = { viewModel.onAction(ChatAction.RecentConversations(it)) },
        onNewConversationClick = {
            viewModel.onAction(ChatAction.NewConversation)
            onReturnToChat()
            drawerOpen = false
        },
        onVoiceCallClick = {
            voiceOpenRequest += 1
            drawerOpen = false
        },
        onPersonalizeClick = {
            onReturnToChat()
            onOpenPersonalization()
            drawerOpen = false
        },
        onRuntimeEnvironmentClick = {
            onReturnToChat()
            onOpenRuntimeEnvironment()
            drawerOpen = false
        },
        onSettingsClick = {
            onReturnToChat()
            onOpenSettings()
            drawerOpen = false
        },
        showUpdateBadge = uiState.hasUpdateBadge,
        exportState = exportState,
        onExportConversation = exportViewModel::requestExport,
        onDeleteConversation = { viewModel.onAction(ChatAction.DeleteConversation(it)) },
    ) {
        RealtimeVoiceHost(
            conversationId = currentSessionId,
            selection = uiState.currentModelSelection,
            conversationTitle = uiState.conversations.firstOrNull { it.id == currentSessionId }?.title.orEmpty(),
            openVoiceRequest = voiceOpenRequest,
            onOpenVoiceRequestConsumed = { voiceOpenRequest = 0 },
            onVoiceVisibleChange = { voicePageVisible = it },
        ) { onOpenVoice ->
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
                onCancelVoiceTranscription = { viewModel.onAction(ChatAction.CancelVoiceTranscription) },
                onToggleSpeechPlayback = { messageId, markdown ->
                    viewModel.onAction(ChatAction.ToggleSpeechPlayback(messageId, markdown))
                },
                onToggleAutoSpeak = { viewModel.onAction(ChatAction.ToggleAutoSpeak) },
                onOpenVoice = onOpenVoice,
                onOpenDocument = { attachment ->
                    openDocumentAttachment(context, attachment)
                },
                onToggleTimeline = { groupId ->
                    viewModel.onAction(ChatAction.ToggleTimeline(groupId))
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
                onRespondToInputRequest = { callId, value ->
                    viewModel.onAction(ChatAction.RespondToInputRequest(callId, value))
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
                onOpenDrawer = { drawerOpen = true },
                onConfigureModels = onConfigureModels,
                onNewConversation = {
                    viewModel.onAction(ChatAction.NewConversation)
                },
                onThinkingEnabledChange = { enabled ->
                    viewModel.onAction(ChatAction.SetThinkingEnabled(enabled))
                },
                onReasoningEffortChange = { effort ->
                    viewModel.onAction(ChatAction.SetReasoningEffort(effort))
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
                onRetryA2ui = { viewModel.onAction(ChatAction.RetryA2ui(it)) },
                onRetryFailedTurn = {
                    viewModel.onAction(ChatAction.RetryFailedTurn)
                },
            )
        }
    }
}

private fun openDocumentAttachment(context: Context, attachment: FileAttachment) {
    runCatching {
        val shareableRoot = AndroidAppDirectoryResolver(context).resolve(
            chatShareableDirectorySpec,
            create = true,
        )
        val directory = File(shareableRoot, "previews").apply { mkdirs() }
        val safeName = attachment.displayName.replace(Regex("""[^\w.\-]"""), "_")
        val file = File(directory, "${attachment.id}-$safeName")
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
        val uri = ShareableFileUriFactory.uriFor(context, file, chatShareableDirectorySpec)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, attachment.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.chat_attachment_open_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
