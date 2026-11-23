package github.ponyhuang.gimi.feature.chat.realtime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigField
import github.ponyhuang.gimi.domain.voice.repository.VoiceConversationStyle
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.feature.chat.ChatComposer
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.feature.chat.openDocumentAttachment

/**
 * 权限和 Android 副作用只在入口处理；无状态页面不接触模型副作用。
 * 懒启动：进入页面不占用麦克风，「开始通话」按钮触发权限确认后才真正 start；
 * 挂断只结束会话并留在待启动状态，只有回聊天才离开页面（系统后台切换仍由前台服务维持通话）。
 * 配置编辑的确认与通知都在这里承载：通话中应用草稿先弹重建确认，应用结果经 Toast 反馈。
 */
@Composable
fun RealtimeVoiceRoute(
    conversationId: String,
    selection: ModelSelection?,
    conversationTitle: String,
    roleChips: (@Composable () -> Unit)? = null,
    active: Boolean = true,
    isVisible: Boolean = active,
    transitionProgress: Float = 1f,
    onDismiss: () -> Unit,
    drawerOpen: Boolean = false,
) {
    val viewModel: RealtimeVoiceViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var leaving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var wasEditing by remember { mutableStateOf(false) }
    // 路由常驻时每次重新进入语音页需复位 leaving 标记。
    LaunchedEffect(active) {
        if (active) leaving = false
    }
    LaunchedEffect(active, state.composer.editing) {
        if (active && state.composer.editing) {
            wasEditing = true
            keyboard?.show()
        } else if (wasEditing) {
            wasEditing = false
            keyboard?.hide()
            focus.clearFocus()
        }
    }
    var presentedState by remember { mutableStateOf(state) }
    // 退场期间保留最后一帧，防止挂断清理产生的 Closed/空字幕闪成“连接中”。
    // 进入动画第一帧即同步最新状态，消除动画结束瞬间才刷新的状态跳变。
    val currentPresentation = state.copy(conversationTitle = conversationTitle)
    SideEffect { if (active && !leaving) presentedState = currentPresentation }
    // 进入页面立即登记会话绑定参数（对话覆盖按它观察，启动时按它绑定），不等待入场动画结束。
    LaunchedEffect(viewModel, active, conversationId, selection, conversationTitle) {
        if (active) viewModel.prepare(conversationId, selection, conversationTitle, VoiceConversationStyle.CHATBOT)
    }
    val context = LocalContext.current
    val routeActive by rememberUpdatedState(active)
    LaunchedEffect(viewModel) {
        viewModel.inputNotices.collect { (revision, error) ->
            if (error != null) {
                // 页面不可见时不弹 Toast，但通知仍要消费，否则回到前台会重放旧错误。
                if (routeActive) {
                    val message = when (error) {
                        VoiceComposerError.StartCall -> R.string.voice_input_start_first
                        VoiceComposerError.Unavailable -> R.string.voice_input_unavailable
                        VoiceComposerError.Unsupported -> R.string.voice_input_unsupported
                        VoiceComposerError.Attachment -> R.string.voice_input_attachment_error
                        VoiceComposerError.Submit -> R.string.voice_input_submit_error
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                viewModel.consumeInputNotice(revision)
            }
        }
    }
    LaunchedEffect(state.voice.failure) {
        val message = when (state.voice.failure) {
            RealtimeVoiceFailure.Configuration -> R.string.voice_configuration_error
            RealtimeVoiceFailure.Microphone -> R.string.voice_microphone_error
            RealtimeVoiceFailure.AudioProcessing -> R.string.voice_audio_error
            RealtimeVoiceFailure.Recognition -> R.string.voice_recognition_error
            RealtimeVoiceFailure.Generation -> R.string.voice_generation_error
            RealtimeVoiceFailure.Synthesis, RealtimeVoiceFailure.Playback -> R.string.voice_synthesis_error
            RealtimeVoiceFailure.Persistence -> R.string.voice_save_caption
            RealtimeVoiceFailure.ConversationBusy -> R.string.voice_conversation_busy
            null -> null
        }
        if (message != null && active) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(state.voice.reconnectNotice) {
        if (state.voice.reconnectNotice && active) Toast.makeText(context, R.string.voice_reconnect_notice, Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(state.voice.toolNotice) {
        state.voice.toolNotice?.takeIf { active }?.let { name ->
            Toast.makeText(context, context.getString(R.string.voice_tool_running, name), Toast.LENGTH_SHORT).show()
        }
    }
    val permissionMessage = stringResource(R.string.voice_permission)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && active) viewModel.startCall() else if (active) {
            // 拒绝权限时停留在待启动页面，用户可再次点「开始通话」重试。
            Toast.makeText(context, permissionMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val startCall = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCall()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    // 配置校验与保存失败时提示用户。
    val conflictMessage = stringResource(R.string.voice_apply_conflict)
    val missingMessage = stringResource(R.string.voice_apply_missing)
    val voiceModelMissingMessage = stringResource(R.string.voice_quick_voice_no_model)
    val noConversationMessage = stringResource(R.string.voice_config_no_conversation)
    LaunchedEffect(viewModel) {
        viewModel.configNotices.collect { (revision, notice) ->
            if (notice != null) {
                // 通知与页面可见性解耦：不可见时也要消费，避免回前台重放旧提示。
                if (routeActive) {
                    val message = when (notice) {
                        RealtimeVoiceViewModel.ConfigNotice.Problem.Conflict -> conflictMessage
                        RealtimeVoiceViewModel.ConfigNotice.Problem.MissingRequired -> missingMessage
                        RealtimeVoiceViewModel.ConfigNotice.Problem.VoiceModelMissing -> voiceModelMissingMessage
                        is RealtimeVoiceViewModel.ConfigNotice.SaveFailed -> {
                            val names = notice.fields.map { context.getString(it.settingsLabelRes()) }.distinct()
                            context.getString(R.string.voice_config_save_failed, names.joinToString(" / "))
                        }
                        RealtimeVoiceViewModel.ConfigNotice.Problem.NoConversation -> noConversationMessage
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                viewModel.consumeConfigNotice(revision)
            }
        }
    }
    // 无法解析的已存配置被重建：从 0 起比较，冷启动读取时已产生的重建也要提示；
    // 提示后让仓库清零计数，重进页面不会重复弹 Toast。
    val rebuiltMessage = stringResource(R.string.voice_config_rebuilt)
    LaunchedEffect(viewModel) {
        var seen = 0
        viewModel.configRebuiltRevisions.collect { count ->
            if (count > seen && routeActive) {
                Toast.makeText(context, rebuiltMessage, Toast.LENGTH_SHORT).show()
                viewModel.acknowledgeConfigRebuilt()
            }
            seen = count
        }
    }
    val routing = state.voice.audioRouting
    val currentDeviceName = voiceAudioDeviceName(routing.devices.firstOrNull { it.id == routing.currentDeviceId })
    val routingMessage = stringResource(
        if (routing.selectionFailed) R.string.voice_device_failed else R.string.voice_device_disconnected,
        currentDeviceName,
    )
    // revision 持久在控制器状态里：以进入本组合时的 revision 为基线，
    // 只提示之后新发生的路由事件，避免重进页面重放历史 Toast。
    val shownNoticeRevision = remember { routing.noticeRevision }
    LaunchedEffect(routing.noticeRevision) {
        if (routing.noticeRevision != shownNoticeRevision) Toast.makeText(context, routingMessage, Toast.LENGTH_SHORT).show()
    }
    // 系统返回与左胶囊「回聊天」同义：结束通话并返回文字聊天。
    val leavePage = {
        if (!leaving) {
            presentedState = state
            leaving = true
            onDismiss()
            viewModel.end()
        }
    }
    val backToChat = {
        when {
            state.composer.submitting -> Unit
            state.composer.editing -> viewModel.closeInput()
            state.composer.draft.text.isNotBlank() || state.composer.draft.attachments.isNotEmpty() -> confirmDiscard = true
            else -> leavePage()
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.voice_input_discard_title)) },
        text = { Text(stringResource(R.string.voice_input_discard_message)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; leavePage() }) {
            Text(stringResource(R.string.voice_input_discard))
        } },
        dismissButton = { TextButton(onClick = {
            confirmDiscard = false
            // 挂断后残留草稿时通话未启动：保留草稿留在待启动页即可，
            // 此时 editInput 会走「先开始通话」拒绝路径，误报错误 Toast。
            if (state.active) viewModel.editInput()
        }) {
            Text(stringResource(R.string.voice_input_keep))
        } },
    )
    // 主按钮挂断只是关闭会话：留在语音页回到待启动状态，不返回聊天列表。
    val hangUp = {
        if (state.composer.draft.text.isNotBlank() || state.composer.draft.attachments.isNotEmpty()) {
            Toast.makeText(context, R.string.voice_input_draft_saved, Toast.LENGTH_SHORT).show()
        }
        viewModel.end()
    }
    BackHandler(enabled = active && !drawerOpen) { backToChat() }
    // 展示快照（含退场冻结帧）同时决定主按钮状态，避免离场动画期间闪回「开始通话」。
    val presented = if (active && !leaving) currentPresentation else presentedState
    RealtimeVoiceScreen(
        uiState = presented,
        started = presented.active,
        onStartCall = startCall,
        roleChips = roleChips,
        onSelectPipeline = viewModel::selectPipeline,
        onSelectFieldModel = viewModel::selectFieldModel,
        onSelectVoice = viewModel::selectPipelineVoice,
        onOpenVoiceSettings = viewModel::openVoiceSettings,
        onCloseVoiceSettings = viewModel::closeVoiceSettings,
        onResetVoiceSettings = viewModel::resetVoiceSettings,
        onToggleMute = viewModel::toggleMute,
        onRetry = viewModel::retry,
        onEnd = hangUp,
        onBack = backToChat,
        onEditInput = viewModel::editInput,
        onOpenDocument = { attachment -> openDocumentAttachment(context, attachment) },
        transitionProgress = transitionProgress,
        isVisible = isVisible,
        composerContent = {
            val composer = presented.composer
            val uploadPending = composer.fileUploadEnabled && composer.draft.attachments.any {
                it.category == AttachmentCategory.DOCUMENT && !it.isVendorUploaded
            }
            val sendEnabled = presented.active && !presented.switching && !uploadPending &&
                (composer.draft.text.isNotBlank() || composer.draft.attachments.isNotEmpty())
            ChatComposer(
                onSendClick = viewModel::submitInput,
                onStopClick = {}, isGenerating = composer.submitting,
                messageData = composer.draft, onMessageDataChange = viewModel::updateDraft,
                attachmentCapabilities = composer.capabilities,
                addToChatState = github.ponyhuang.gimi.feature.chat.ChatAddToChatState(
                    vendorTemplateId = composer.vendorTemplateId,
                ),
                attachmentUploadStates = composer.uploads,
                isFileUploadEnabled = composer.fileUploadEnabled,
                onRetryAttachmentUpload = viewModel::uploadDocument,
                onDocumentDraftImported = viewModel::uploadDocument,
                uploadedFiles = composer.uploadedFiles,
                onAttachUploadedFile = viewModel::attachUploadedFile,
                retainExpanded = true, embedded = true,
                inputContent = { params -> VoiceComposerInput(params, sendEnabled, viewModel::closeInput) },
            )
        },
    )
}

private fun VoiceConfigField.settingsLabelRes(): Int = when (this) {
    VoiceConfigField.PIPELINE -> R.string.voice_settings_entry
    VoiceConfigField.CASCADED_RECOGNITION_MODEL -> R.string.voice_settings_stt_model
    VoiceConfigField.CASCADED_SYNTHESIS_MODEL, VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL -> R.string.voice_settings_tts_model
    VoiceConfigField.SEMI_CASCADED_UNDERSTANDING_MODEL -> R.string.voice_settings_understanding_model
    VoiceConfigField.REALTIME_MODEL -> R.string.voice_settings_realtime_model
    VoiceConfigField.CASCADED_VOICE, VoiceConfigField.SEMI_CASCADED_VOICE -> R.string.voice_settings_tts_voice
    VoiceConfigField.REALTIME_VOICE -> R.string.voice_settings_realtime_voice
}
