package github.ponyhuang.gimi.feature.chat

import androidx.compose.ui.platform.LocalUriHandler
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import github.ponyhuang.gimi.domain.conversation.model.ChatMode
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.TextPart

/** Chatbot 的 Android 副作用与事件装配入口，具体布局复用现有无状态聊天组件。 */
@Composable
internal fun ChatbotRouteContent(
    viewModel: ChatbotViewModel,
    onOpenDrawer: () -> Unit,
    onConfigureModels: () -> Unit,
    onModeChange: (ChatMode) -> Unit,
    onOpenVoice: () -> Unit,
    roleChips: (@Composable () -> Unit)? = null,
    sharedMediaUris: List<android.net.Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
    /** 顶栏由宿主统一渲染时置 false，透传给 ChatScaffold。 */
    headerVisible: Boolean = true,
    /** 分页常驻组合时仅当前页消费一次性通知，避免隐藏页在另一模式下弹 Toast；离页期间不消费，与原先离页即销毁的语义一致。 */
    active: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uploadedFilesRefreshing by viewModel.uploadedFilesRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val sourceOpenFailed = stringResource(R.string.chat_web_citations_open_failed)
    val configure = stringResource(R.string.chat_notice_configure_chat_model)
    val sendBusy = stringResource(R.string.chatbot_send_busy)
    val protected = stringResource(R.string.chat_drawer_current_session_protected)
    val failure = stringResource(R.string.chatbot_failure)
    val alreadyNewConversation = stringResource(R.string.chat_notice_already_new_conversation)
    val refused = stringResource(R.string.chatbot_refused)
    val stopped = stringResource(R.string.chatbot_interrupted)
    val voiceInterrupted = stringResource(R.string.chatbot_voice_interrupted)
    val uploadedFilesRefreshFailed = stringResource(R.string.chat_uploaded_files_refresh_failed)
    val uploadedFileDeleteFailed = stringResource(R.string.chat_uploaded_file_delete_failed)
    val uploadedFilesRefreshed = stringResource(R.string.chat_uploaded_files_refreshed)
    val uploadedFileDeleted = stringResource(R.string.chat_uploaded_file_deleted)
    val noticeMessage = when (state.notice) {
        ChatbotNotice.CONFIGURE_MODEL -> configure
        ChatbotNotice.SEND_BUSY -> sendBusy
        ChatbotNotice.PROTECTED_SESSION -> protected
        ChatbotNotice.FAILED -> failure
        ChatbotNotice.ALREADY_NEW_CONVERSATION -> alreadyNewConversation
        ChatbotNotice.UPLOADED_FILES_REFRESH_FAILED -> uploadedFilesRefreshFailed
        ChatbotNotice.UPLOADED_FILE_DELETE_FAILED -> uploadedFileDeleteFailed
        ChatbotNotice.UPLOADED_FILES_REFRESHED -> uploadedFilesRefreshed
        ChatbotNotice.UPLOADED_FILE_DELETED -> uploadedFileDeleted
        null -> null
    }
    LaunchedEffect(state.notice, noticeMessage, active) {
        if (!active) return@LaunchedEffect
        val message = noticeMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onAction(ChatbotAction.ConsumeNotice)
    }
    val messages = state.chat.messages.map { message ->
        if (message.role != MessageRole.Assistant) return@map message
        val outcome = state.messageOutcomes[message.id] ?: return@map message
        message.copy(
            textParts = if (outcome == ChatbotTurnOutcome.REFUSED) listOf(TextPart(message.id + ":refused", refused))
                else message.textParts,
            error = when (outcome) {
                ChatbotTurnOutcome.FAILED -> failure
                ChatbotTurnOutcome.STOPPED, ChatbotTurnOutcome.INTERRUPTED ->
                    stopped.takeIf { message.textParts.none { !it.thought && it.text.isNotBlank() } }
                ChatbotTurnOutcome.VOICE_INTERRUPTED -> voiceInterrupted
                else -> null
            },
        )
    }
    val chat = state.chat.copy(messages = messages,
        listItems = messages.toChatListItems(TimelineActivityState(isAgentRunning = state.chat.isAgentRunning)))
    ChatScaffold(
        state = chat,
        partChannelProvider = { null },
        onSend = viewModel::send,
        onStop = { viewModel.onAction(ChatbotAction.Stop) },
        onTranscribeVoice = viewModel::transcribeVoice,
        onCancelVoiceTranscription = viewModel::cancelTranscription,
        onToggleSpeechPlayback = viewModel::togglePlayback,
        onOpenVoice = onOpenVoice,
        onOpenDocument = { attachment ->
            scope.openChatDocument(context, attachment) {
                viewModel.openVendorDocument(attachment)
            }
        },
        onOpenWebSource = { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, sourceOpenFailed, Toast.LENGTH_SHORT).show()
            }
        },
        onToggleTimeline = viewModel::toggleTimeline,
        onToolConfirmation = {}, onToolConfirmationAlwaysAllow = {}, onFullAccessChange = {},
        onSelectModel = { viewModel.onAction(ChatbotAction.SelectModel(it)) },
        onModelSwitchBlocked = { Toast.makeText(context, configure, Toast.LENGTH_SHORT).show() },
        onOpenDrawer = onOpenDrawer,
        onConfigureModels = onConfigureModels,
        onNewConversation = { viewModel.onAction(ChatbotAction.NewConversation) },
        onReasoningEffortChange = { viewModel.onAction(ChatbotAction.SetEffort(it)) },
        onThinkingEnabledChange = { viewModel.onAction(ChatbotAction.SetThinking(it)) },
        onMcpServerEnabledChange = { _, _ -> },
        onOfficialToolOpened = {},
        onOfficialToolFunctionEnabledChange = { toolId, functionId, enabled ->
            viewModel.onAction(ChatbotAction.SetTool(toolId, functionId, enabled))
        },
        onOfficialToolFunctionsRetry = {},
        onRetryFailedTurn = { viewModel.onAction(ChatbotAction.Retry) },
        onRetryAttachmentUpload = viewModel::uploadDocumentDraft,
        onDocumentDraftImported = viewModel::uploadDocumentDraft,
        uploadedFiles = state.uploadedFiles,
        onAttachUploadedFile = viewModel::attachFile,
        attachFileRequests = viewModel.attachFileRequests,
        onUploadedFileDelete = viewModel::deleteUploadedFile,
        onRefreshUploadedFiles = viewModel::refreshUploadedFiles,
        isRefreshingUploadedFiles = uploadedFilesRefreshing,
        chatMode = ChatMode.CHATBOT,
        onChatModeChange = onModeChange,
        headerVisible = headerVisible,
        roleChips = roleChips,
        sharedMediaUris = sharedMediaUris.takeUnless { chat.isInitializing }.orEmpty(),
        onSharedMediaConsumed = onSharedMediaConsumed,
        initialDraft = state.draft,
        onDraftChanged = { viewModel.saveDraft(chat.sessionId, it) },
    )
}
