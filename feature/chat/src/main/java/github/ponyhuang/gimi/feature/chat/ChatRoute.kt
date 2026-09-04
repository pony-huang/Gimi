package github.ponyhuang.gimi.feature.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import kotlinx.coroutines.launch
import java.io.File

/**
 * 自包含的 Chat 入口。该 Composable 拥有 [ChatViewModel]、[ChatRecommendationsViewModel]、
 * 抽屉、效果监听、文档/本地文件打开与搜索结果子页面；app 只传入跨 feature 跳转回调。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
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
    onConfigureModels: () -> Unit,
    onShowAllLocalFiles: (sessionId: String, responseId: String) -> Unit,
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
) {
    // Activity 作用域：与“查看全部”等子目的地共享同一份会话状态（见 activityScopedChatViewModel）。
    val viewModel: ChatViewModel = activityScopedChatViewModel()
    val recommendationViewModel: ChatRecommendationsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recommendations by recommendationViewModel.recommendations.collectAsStateWithLifecycle()
    val currentSessionId = uiState.sessionId
    val context = LocalContext.current
    val chatNoticeConfigureChatModel = stringResource(R.string.chat_notice_configure_chat_model)
    val chatNoticeModelSwitchBlocked = stringResource(R.string.chat_notice_model_switch_blocked)
    val chatNoticeParallelLimit = stringResource(R.string.chat_notice_parallel_limit)
    val chatNoticeCurrentConversationBusy =
        stringResource(R.string.chat_notice_current_conversation_busy)
    val chatNoticeActiveDeleteBlocked = stringResource(R.string.chat_notice_active_delete_blocked)
    val chatNoticeMixedAttachmentCategories =
        stringResource(R.string.chat_notice_mixed_attachment_categories)
    val chatNoticeChatModelUnavailable = stringResource(R.string.chat_notice_chat_model_unavailable)
    val chatNoticeAttachmentCategoryUnsupported =
        stringResource(R.string.chat_notice_attachment_category_unsupported)
    val chatNoticeDocumentTotalSizeLimit =
        stringResource(R.string.chat_notice_document_total_size_limit)
    val chatNoticeMemorySearchFailed = stringResource(R.string.chat_notice_memory_search_failed)
    val chatNoticeMemoryWriteFailed = stringResource(R.string.chat_notice_memory_write_failed)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(viewModel) {
        viewModel.setCurrentChatVisible(true)
        onPauseOrDispose { viewModel.setCurrentChatVisible(false) }
    }

    LaunchedEffect(viewModel) {
        viewModel.onAction(ChatAction.RefreshConversations)
        viewModel.onAction(ChatAction.RestoreOrCreateSession)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is ChatEffect.ShowNotice -> when (val notice = effect.notice) {
                    ChatNotice.ConfigureChatModel -> chatNoticeConfigureChatModel
                    ChatNotice.ModelSwitchBlocked -> chatNoticeModelSwitchBlocked
                    ChatNotice.ParallelTaskLimitReached -> chatNoticeParallelLimit
                    ChatNotice.CurrentConversationBusy -> chatNoticeCurrentConversationBusy
                    ChatNotice.ActiveConversationDeleteBlocked -> chatNoticeActiveDeleteBlocked
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
            onReturnToChat()
            scope.launch { drawerState.close() }
        },
        onDeleteClick = { conversation ->
            viewModel.onAction(ChatAction.DeleteConversation(conversation.id))
        },
        onSettingsClick = {
            onReturnToChat()
            onOpenSettings()
            scope.launch { drawerState.close() }
        },
        darkTheme = uiState.darkThemeOverride ?: isSystemInDarkTheme(),
        onDarkThemeChange = { enabled ->
            viewModel.onAction(ChatAction.SetDarkTheme(enabled))
        },
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
                onShowAllLocalFiles(currentSessionId, responseId)
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
            onOpenSettings = onOpenSettings,
            onConfigureModels = onConfigureModels,
            onNewConversation = {
                viewModel.onAction(ChatAction.NewConversation)
            },
            onLocalToolEnabledChange = { toolId, enabled ->
                viewModel.onAction(ChatAction.SetLocalToolEnabled(toolId, enabled))
            },
            onToolAccessModeChange = { mode ->
                viewModel.onAction(ChatAction.SetToolAccessMode(mode))
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
        )
    }
}

private fun openDocumentAttachment(context: Context, attachment: FileAttachment) {
    runCatching {
        val directory = File(context.cacheDir, "message-attachments").apply { mkdirs() }
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
            context.getString(R.string.chat_attachment_open_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

internal fun openLocalFile(context: Context, file: LocalFileReference) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(file.contentUri), file.mimeType)
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
