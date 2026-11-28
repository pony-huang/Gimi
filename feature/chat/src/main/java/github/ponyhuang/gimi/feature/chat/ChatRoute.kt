package github.ponyhuang.gimi.feature.chat

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import github.ponyhuang.gimi.feature.chat.a2ui.ChatA2uiMusicActions
import github.ponyhuang.gimi.feature.chat.a2ui.ChatA2uiDeviceActions
import github.ponyhuang.gimi.feature.chat.a2ui.LocalA2uiDeviceActions
import github.ponyhuang.gimi.feature.chat.a2ui.LocalChatA2uiMusicActions
import github.ponyhuang.gimi.domain.a2ui.A2uiFlashlightState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.core.storage.AndroidAppDirectoryResolver
import github.ponyhuang.gimi.feature.chat.realtime.ChatbotRoleChipsRoute
import github.ponyhuang.gimi.feature.chat.realtime.RealtimeVoiceHost
import github.ponyhuang.gimi.core.storage.ShareableFileUriFactory
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.ChatMode
import github.ponyhuang.gimi.domain.conversation.repository.VendorAttachmentResolution
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileFailureReason
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.core.net.toUri

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
    onConfigureModels: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenLocalEnvironment: () -> Unit,
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
    chatLaunchRequest: github.ponyhuang.gimi.domain.conversation.model.ChatLaunchRequest? = null,
) {
    // 保持聊天状态与宿主 Activity 生命周期一致。
    val viewModel: ChatViewModel = activityScopedChatViewModel()
    val chatbotViewModel: ChatbotViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        checkNotNull(LocalContext.current.findComponentActivity()),
    )
    val chatbotState by chatbotViewModel.uiState.collectAsStateWithLifecycle()
    var chatMode by rememberSaveable { mutableStateOf(ChatMode.ASSISTANT) }
    var historyMode by rememberSaveable { mutableStateOf(ChatMode.ASSISTANT) }
    val modeStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var consumedChatRequest by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(chatLaunchRequest, chatbotState.chat.isInitializing) {
        val request = chatLaunchRequest
        if (request != null && request.requestId != consumedChatRequest && !chatbotState.chat.isInitializing) {
            chatMode = request.mode
            request.sessionId?.let { id ->
                if (request.mode == ChatMode.CHATBOT) chatbotViewModel.onAction(ChatbotAction.SwitchSession(id))
                else viewModel.onAction(ChatAction.SwitchSession(id))
            }
            consumedChatRequest = request.requestId
        }
    }
    val recommendationViewModel: ChatRecommendationsViewModel = hiltViewModel()
    val exportViewModel: SessionExportViewModel = hiltViewModel()
    val exportState by exportViewModel.uiState.collectAsStateWithLifecycle()
    SessionExportEffects(exportViewModel)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyState = if (historyMode == ChatMode.CHATBOT) chatbotState.chat else uiState
    val onRecentAction: (RecentConversationsAction) -> Unit = { action ->
        if (historyMode == ChatMode.CHATBOT) chatbotViewModel.recentAction(action)
        else viewModel.onAction(ChatAction.RecentConversations(action))
    }
    val recommendations by recommendationViewModel.recommendations.collectAsStateWithLifecycle()
    val uploadedFiles by viewModel.uploadedFiles.collectAsStateWithLifecycle()
    val uploadedFilesRefreshing by viewModel.uploadedFilesRefreshing.collectAsStateWithLifecycle()
    val currentSessionId = uiState.sessionId
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var flashlightPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val flashlightPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        flashlightPermissionDenied = !granted && context.findComponentActivity()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
        viewModel.refreshA2uiFlashlight()
    }
    val chatNoticeConfigureChatModel = stringResource(R.string.chat_notice_configure_chat_model)
    val chatNoticeAlreadyNewConversation = stringResource(R.string.chat_notice_already_new_conversation)
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
    val chatNoticeUploadedFileDeleteFailed =
        stringResource(R.string.chat_uploaded_file_delete_failed)
    val chatNoticeUploadedFilesRefreshFailed =
        stringResource(R.string.chat_uploaded_files_refresh_failed)
    val chatNoticeUploadedFilesRefreshed =
        stringResource(R.string.chat_uploaded_files_refreshed)
    val chatNoticeUploadedFileDeleted =
        stringResource(R.string.chat_uploaded_file_deleted)
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
    // 语音页是全屏覆盖层：其可见期间关闭抽屉手势，避免边缘滑动被覆盖层误捕获；
    // 文字聊天页保持默认手势行为（边缘右滑打开抽屉）。
    var voicePageVisible by remember { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshA2uiFlashlight()
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
                val uri = effect.url.toUri()
                when (uri.scheme) {
                    "http", "https" -> {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        catch (_: android.content.ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.chat_a2ui_link_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                    "orpheus" -> {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: android.content.ActivityNotFoundException) {
                            // 未安装网易云音乐客户端时，若有数字 originalId 则回退至网页版，否则打开网易云首页。
                            val id = uri.lastPathSegment?.takeIf { it.all(Char::isDigit) }
                            val fallbackWeb = when (uri.host) {
                                "song" if id != null -> "https://music.163.com/#/song?id=$id"
                                "playlist" if id != null -> "https://music.163.com/#/playlist?id=$id"
                                else -> "https://music.163.com/"
                            }.toUri()
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, fallbackWeb))
                            } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(context, context.getString(R.string.chat_a2ui_link_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    "spotify" -> {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: android.content.ActivityNotFoundException) {
                            // 未安装 Spotify 客户端时，将 spotify:<type>:<id> 转换为 https://open.spotify.com/<type>/<id> 网页回退。
                            val parts = effect.url.removePrefix("spotify:").split(':').filter(String::isNotBlank)
                            val fallbackWeb = if (parts.size >= 2 && parts[0] in setOf("track", "album", "playlist", "artist", "episode", "show")) {
                                "https://open.spotify.com/${parts[0]}/${parts[1]}"
                            } else {
                                "https://open.spotify.com/"
                            }.toUri()
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, fallbackWeb))
                            } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(context, context.getString(R.string.chat_a2ui_link_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                return@collect
            }
        }
    }

    val noticeMessage = uiState.notice?.let { notice ->
        when (notice) {
            ChatNotice.ConfigureChatModel -> chatNoticeConfigureChatModel
            ChatNotice.AlreadyNewConversation -> chatNoticeAlreadyNewConversation
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
            is ChatNotice.AttachmentVendorMismatch -> context.getString(
                R.string.chat_notice_attachment_vendor_mismatch,
                notice.displayName,
            )
            ChatNotice.DocumentTotalSizeLimitExceeded -> chatNoticeDocumentTotalSizeLimit
            ChatNotice.A2uiMusicLikeFailed -> stringResource(R.string.chat_a2ui_music_like_failed)
            ChatNotice.A2uiDeviceChangeFailed -> stringResource(R.string.chat_a2ui_device_change_failed)
            ChatNotice.UploadedFileDeleteFailed -> chatNoticeUploadedFileDeleteFailed
            ChatNotice.UploadedFilesRefreshFailed -> chatNoticeUploadedFilesRefreshFailed
            ChatNotice.UploadedFilesRefreshed -> chatNoticeUploadedFilesRefreshed
            ChatNotice.UploadedFileDeleted -> chatNoticeUploadedFileDeleted
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
    LaunchedEffect(uiState.notice, noticeMessage) {
        val message = noticeMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onAction(ChatAction.ConsumeNotice)
    }

    BackHandler(enabled = drawerOpen && historyState.recentConversations.isSelecting && !historyState.recentConversations.isDeleting) {
        // 多选在助手/Chatbot 两个档位都可进入，结束选择必须分流到当前档位的 ViewModel。
        if (historyMode == ChatMode.CHATBOT) chatbotViewModel.recentAction(RecentConversationsAction.FinishSelection)
        else viewModel.onAction(ChatAction.RecentConversations(RecentConversationsAction.FinishSelection))
    }
    // 输入法显示时让系统先处理返回；抽屉自身的预测性返回在搜索期间让出。
    BackHandler(
        enabled = drawerOpen && historyState.recentConversations.isSearching &&
            !historyState.recentConversations.isSelecting && !imeVisible && !historyState.recentConversations.isDeleting,
    ) {
        onRecentAction(RecentConversationsAction.CloseSearch)
    }
    LaunchedEffect(drawerOpen, historyState.recentConversations.isSearching, historyState.recentConversations.isSelecting) {
        if (searchHadFocus && (!drawerOpen || !historyState.recentConversations.isSearching || historyState.recentConversations.isSelecting)) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            searchHadFocus = false
        }
    }
    LaunchedEffect(drawerOpen) {
        if (drawerOpen) historyMode = chatMode
        if (!drawerOpen) {
            viewModel.onAction(ChatAction.RecentConversations(RecentConversationsAction.DrawerClosed))
            chatbotViewModel.recentAction(RecentConversationsAction.DrawerClosed)
        }
    }

    ChatDrawer(
        open = drawerOpen,
        gesturesEnabled = !voicePageVisible || drawerOpen,
        onOpenChange = { drawerOpen = it },
        searchFocusRequester = searchFocusRequester,
        onSearchExpanded = {
            if (drawerOpen && historyState.recentConversations.isSearching && !historyState.recentConversations.isSelecting) {
                searchFocusRequester.requestFocus()
                searchHadFocus = true
                keyboard?.show()
            }
        },
        onSearchSubmit = { keyboard?.hide() },
        conversations = historyState.conversations,
        currentSessionId = historyState.sessionId,
        conversationTaskStatuses = historyState.conversationTaskStatuses,
        historyMode = historyMode,
        onHistoryModeChange = { historyMode = it },
        onConversationClick = { conversation ->
            val targetMode = historyMode
            chatMode = targetMode
            if (targetMode == ChatMode.CHATBOT) chatbotViewModel.onAction(ChatbotAction.SwitchSession(conversation.id))
            else viewModel.onAction(ChatAction.SwitchSession(conversation.id))
            onReturnToChat()
            drawerOpen = false
        },
        recentState = historyState.recentConversations,
        onRecentAction = onRecentAction,
        onModelServicesClick = {
            onConfigureModels()
            drawerOpen = false
        },
        onPluginsClick = {
            onOpenPlugins()
            drawerOpen = false
        },
        onLocalEnvironmentClick = {
            onOpenLocalEnvironment()
            drawerOpen = false
        },
        onPersonalizeClick = {
            onReturnToChat()
            onOpenPersonalization()
            drawerOpen = false
        },
        onSettingsClick = {
            onReturnToChat()
            onOpenSettings()
            drawerOpen = false
        },
        autoSpeakEnabled = uiState.autoSpeakEnabled,
        onToggleAutoSpeak = { viewModel.onAction(ChatAction.ToggleAutoSpeak) },
        showUpdateBadge = uiState.hasUpdateBadge,
        exportState = exportState,
        onExportConversation = exportViewModel::requestExport,
        onDeleteConversation = {
            if (historyMode == ChatMode.CHATBOT) chatbotViewModel.onAction(ChatbotAction.DeleteSession(it))
            else viewModel.onAction(ChatAction.DeleteConversation(it))
        },
    ) {
        RealtimeVoiceHost(
            conversationId = chatbotState.sessionId,
            selection = chatbotState.selection,
            conversationTitle = chatbotState.chat.conversations
                .firstOrNull { it.id == chatbotState.sessionId }
                ?.takeIf { it.lastMessage.isNotBlank() || chatbotState.chat.messages.isNotEmpty() }
                ?.title.orEmpty(),
            roleChips = {
                ChatbotRoleChipsRoute(
                    chatbotState.sessionId,
                    edgePadding = 26.dp,
                    showCenteredLockedRole = true,
                )
            },
            onVoiceVisibleChange = { voicePageVisible = it },
            drawerOpen = drawerOpen,
        ) { onOpenVoice ->
            // 两模式页面常驻组合：切档只做横向平移，不销毁重建，滚动位置与草稿自然保留。
            // 禁用手势拖动，避免与抽屉边缘手势冲突；切档方向由页序决定，与顶栏滑块同向。
            val pagerState = rememberPagerState(initialPage = chatMode.ordinal) { ChatMode.entries.size }
            LaunchedEffect(chatMode) {
                pagerState.animateScrollToPage(
                    page = chatMode.ordinal,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val pageMode = ChatMode.entries[page]
                    modeStates.SaveableStateProvider(pageMode) {
                        if (pageMode == ChatMode.CHATBOT) {
                            ChatbotRouteContent(
                                viewModel = chatbotViewModel,
                                onOpenDrawer = { drawerOpen = true },
                                onConfigureModels = onConfigureModels,
                                onModeChange = { chatMode = it },
                                onOpenVoice = onOpenVoice,
                                roleChips = { ChatbotRoleChipsRoute(chatbotState.sessionId) },
                                sharedMediaUris = sharedMediaUris,
                                onSharedMediaConsumed = onSharedMediaConsumed,
                                headerVisible = false,
                                active = chatMode == ChatMode.CHATBOT,
                            )
                        } else {
                            val musicActions = if (chatMode == ChatMode.ASSISTANT && currentSessionId.isNotBlank()) {
                                ChatA2uiMusicActions(
                                    sessionId = currentSessionId,
                                    renderer = viewModel.a2uiRendererFunctions,
                                    liked = uiState.a2uiNeteaseLikes,
                                    pendingLikes = uiState.a2uiPendingNeteaseLikes,
                                    setLiked = { id, liked -> viewModel.setA2uiNeteaseLiked(currentSessionId, id, liked) },
                                    canRecommend = !uiState.isInitializing && !uiState.isAgentRunning,
                                )
                            } else null
                            val deviceActions = musicActions?.let {
                                ChatA2uiDeviceActions(
                                    volume = viewModel.a2uiVolume,
                                    flashlight = viewModel.a2uiFlashlight,
                                    changingVolume = uiState.a2uiChangingVolume,
                                    changingFlashlight = uiState.a2uiChangingFlashlight,
                                    permissionNeedsSettings = flashlightPermissionDenied,
                                    setVolume = { level -> viewModel.setA2uiVolume(currentSessionId, level) },
                                    setFlashlight = { enabled -> viewModel.setA2uiFlashlight(currentSessionId, enabled) },
                                    requestFlashlightPermission = {
                                        if (viewModel.a2uiFlashlight.value == A2uiFlashlightState.PermissionRequired) {
                                            if (flashlightPermissionDenied) {
                                                context.startActivity(Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", context.packageName, null),
                                                ))
                                            } else {
                                                flashlightPermission.launch(Manifest.permission.CAMERA)
                                            }
                                        }
                                    },
                                )
                            }
                            CompositionLocalProvider(
                                LocalChatA2uiMusicActions provides musicActions,
                                LocalA2uiDeviceActions provides deviceActions,
                            ) {
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
                                    onOpenDocument = { attachment ->
                                        scope.openChatDocument(context, attachment) {
                                            viewModel.openVendorDocument(attachment)
                                        }
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
                                    onRetryAttachmentUpload = viewModel::uploadDocumentDraft,
                                    onDocumentDraftImported = viewModel::uploadDocumentDraft,
                                    attachFileRequests = viewModel.attachFileRequests,
                                    uploadedFiles = uploadedFiles,
                                    onAttachUploadedFile = viewModel::requestAttachVendorFile,
                                    onUploadedFileDelete = { viewModel.onAction(ChatAction.DeleteUploadedFile(it)) },
                                    onUploadedFilePrune = viewModel::clearDocumentUploads,
                                    onRefreshUploadedFiles = viewModel::refreshUploadedFiles,
                                    isRefreshingUploadedFiles = uploadedFilesRefreshing,
                                    onRetryA2ui = { viewModel.onAction(ChatAction.RetryA2ui(it)) },
                                    onRetryFailedTurn = {
                                        viewModel.onAction(ChatAction.RetryFailedTurn)
                                    },
                                    chatMode = ChatMode.ASSISTANT,
                                    onChatModeChange = { chatMode = it },
                                    headerVisible = false,
                                )
                            }
                        }
                    }
                }
                // 顶栏只渲染一份：分段控件不随页面切换销毁，滑块动画连续播放。
                ChatFloatingTopBar(
                    onOpenDrawer = {
                        drawerOpen = true
                    },
                    onNewConversation = {
                        if (chatMode == ChatMode.CHATBOT) chatbotViewModel.onAction(ChatbotAction.NewConversation)
                        else viewModel.onAction(ChatAction.NewConversation)
                    },
                    chatMode = chatMode,
                    onChatModeChange = { chatMode = it },
                )
            }
        }
    }
}

internal fun openDocumentAttachment(context: Context, attachment: FileAttachment) {
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

/**
 * 打开文档消息附件的统一入口（助手 / Chatbot / 实时语音三个 surface 共用）。
 *
 * 本地载荷（归档副本或 inline 字节）直接走 [openDocumentAttachment]；厂商 file 引用
 * 附件经 [resolve] 先查注册表绑定、缺失时走厂商下载兜底，成功后复用同一打开路径，
 * 失败按原因提示：厂商协议不支持下载保留既有文案，其余失败提示下载失败。音频附件
 * 的播放分支在组件内自行处理，不经本入口。
 */
internal fun CoroutineScope.openChatDocument(
    context: Context,
    attachment: FileAttachment,
    resolve: suspend () -> VendorAttachmentResolution,
) {
    val hasLocalPayload = attachment.inlineData != null ||
        attachment.payloadReference?.let(::File)?.isFile == true
    if (hasLocalPayload) {
        openDocumentAttachment(context, attachment)
        return
    }
    launch {
        when (val result = resolve()) {
            is VendorAttachmentResolution.Resolved -> openDocumentAttachment(context, result.attachment)
            is VendorAttachmentResolution.Failed -> Toast.makeText(
                context,
                context.getString(
                    if (result.reason == VendorFileFailureReason.UNSUPPORTED_VENDOR) {
                        R.string.chat_attachment_open_unsupported
                    } else {
                        R.string.chat_attachment_open_download_failed
                    },
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
