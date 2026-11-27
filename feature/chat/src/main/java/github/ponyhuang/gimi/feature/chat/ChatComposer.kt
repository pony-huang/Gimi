package github.ponyhuang.gimi.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import github.ponyhuang.gimi.core.audio.VoiceAudioRecorder
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.modelcatalog.model.FileIngestMode
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.domain.voice.repository.toVoiceFailure
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.GimiTheme
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Chat composer with attach, voice, and send buttons.
 *
 * This composable provides full control over the message state and includes:
 * - Text input field with placeholder
 * - Add button for selecting images
 * - Voice input button with speech-to-text
 * - Send button (shown when text is not empty)
 * - Stop button (shown during AI generating)
 *
 * The rendered components are resolved through [LocalChatAiComponentFactory], so each part can
 * be overridden without replacing the whole composer. See [ChatAiComponentFactory].
 *
 * @param onSendClick Callback invoked when the send button is clicked with the composed message data.
 * @param onStopClick Callback invoked when the stop button is clicked (during AI generation).
 * @param onVoiceInputStart Callback invoked after microphone capture starts.
 * @param onVoiceInputStop Callback invoked after an active capture stops or is cancelled.
 * @param onVoiceAudioChunk Callback receiving 16 kHz, mono, signed 16-bit PCM chunks on a
 * background thread for optional observation.
 * @param onVoiceInputError Callback invoked when microphone capture cannot start or fails.
 * @param isGenerating Whether the AI is currently generating a response.
 * @param modifier The modifier to be applied to the composer.
 * @param messageData The current draft owned by the chat screen, shared across conversations.
 * @param onMessageDataChange Reports edits to the draft; the owner consumes accepted submissions.
 * @param modelSelectorContent Model selection control rendered beside the attachment button.
 * @param retainExpanded Whether an active child surface requires the composer to stay expanded.
 * @param attachmentsEnabled Whether attachment selection is available for this input.
 * @param headerContent Optional request prompt displayed above both text and recording states.
 * @param onExpandedChange Callback reporting whether the capsule is currently in its enlarged
 * state, so surrounding content can align with it.
 */
@Composable
public fun ChatComposer(
    onSendClick: (MessageData, (ChatSubmissionResult) -> Unit) -> Unit,
    onStopClick: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
    messageData: MessageData = MessageData(),
    onMessageDataChange: (MessageData) -> Unit = {},
    onVoiceInputStart: () -> Unit = { },
    onVoiceInputStop: () -> Unit = { },
    onVoiceAudioChunk: (ByteArray) -> Unit = { },
    onVoiceInputError: (Throwable) -> Unit = { },
    isVoiceInputAvailable: Boolean = false,
    onTranscribeVoice: VoiceTranscriptionRequest = { _, _, _, complete ->
        complete(Result.failure(IllegalStateException("transcription not configured")))
    },
    voiceTranscriptionMode: VoiceInputMode? = null,
    onCancelVoiceTranscription: () -> Unit = {},
    voiceInputContextKey: String = "",
    retainExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = { },
    modelSelectorContent: @Composable () -> Unit = { },
    onOpenVoice: (() -> Unit)? = null,
    addToChatState: ChatAddToChatState = ChatAddToChatState(),
    onMcpServerEnabledChange: (String, Boolean) -> Unit = { _, _ -> },
    onFullAccessChange: (Boolean) -> Unit = { _ -> },
    onOfficialToolOpened: (String) -> Unit = { _ -> },
    onOfficialToolFunctionEnabledChange: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onOfficialToolFunctionsRetry: (String) -> Unit = { _ -> },
    attachmentCapabilities: MultimodalCapabilities = MultimodalCapabilities(),
    /** 文档草稿的厂商上传状态（键为草稿 reference）；chip 三态与发送门控的数据源。 */
    uploadedFiles: List<UploadedFileEntry> = emptyList(),
    onAttachUploadedFile: (UploadedFileEntry) -> Unit = {},
    onUploadedFileDelete: (UploadedFileEntry) -> Unit = {},
    attachmentUploadStates: Map<String, DraftUploadState> = emptyMap(),
    /** 当前选择是否启用厂商文件上传；决定文档草稿是否需要预热上传与发送门控。 */
    isFileUploadEnabled: Boolean = false,
    /** 上传失败的 chip 原地重试。 */
    onRetryAttachmentUpload: (DraftAttachment) -> Unit = {},
    /** 新导入的文档草稿触发厂商上传（仅启用上传链路时实际生效）。 */
    onDocumentDraftImported: (DraftAttachment) -> Unit = {},
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
    attachmentsEnabled: Boolean = true,
    /** 控制会话权限入口；Chatbot 使用固定的联网工具白名单。 */
    showPermissionOption: Boolean = true,
    headerContent: @Composable () -> Unit = {},
    /** 实时通话嵌入同一胶囊时由外层处理 IME、安全区与容器。 */
    embedded: Boolean = false,
    inputContent: (@Composable (ComposerInputContentParams) -> Unit)? = null,
) {
    val currentMessageData by rememberUpdatedState(messageData)
    val onDraftChange by rememberUpdatedState(onMessageDataChange)
    val onSubmit by rememberUpdatedState(onSendClick)
    val onTranscriptionRequest by rememberUpdatedState(onTranscribeVoice)
    val onCancelTranscription by rememberUpdatedState(onCancelVoiceTranscription)
    val currentIsGenerating by rememberUpdatedState(isGenerating)
    val currentTranscriptionMode by rememberUpdatedState(voiceTranscriptionMode)
    val currentOnRetryUpload by rememberUpdatedState(onRetryAttachmentUpload)
    val currentOnDocumentDraftImported by rememberUpdatedState(onDocumentDraftImported)
    var isSubmitting by remember { mutableStateOf(false) }

    // 上传链路启用时，未拿到 file 引用的文档草稿阻塞发送：消灭"传完/没传完"混合态，
    // 保证消息形态唯一（发送时 file_id 必然就绪）。门控实现在 submitMessage 内联。
    var showAttachmentOptions by rememberSaveable { mutableStateOf(false) }
    var showPermissionMode by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceInputState: VoiceInputUiState by remember { mutableStateOf(VoiceInputUiState.Idle) }
    var voiceErrorMessage by remember { mutableStateOf<String?>(null) }
    val voiceAudio = remember { VoicePcmBuffer() }
    val voiceRecorder = remember { VoiceAudioRecorder() }
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val voiceNoAudioMessage = stringResource(R.string.chat_voice_no_audio_captured)
    val voiceRecordingFailedMessage = stringResource(R.string.chat_voice_recording_failed)
    val voiceNoTextMessage = stringResource(R.string.chat_voice_no_text_recognized)
    val mixedAttachmentMessage = stringResource(R.string.chat_attachment_mixed_types)
    val attachmentReadFailedMessage = stringResource(R.string.chat_attachment_read_failed)
    val attachmentUnsupportedMessage = stringResource(R.string.chat_attachment_unsupported)

    fun acceptSelection(uris: List<Uri>) {
        if (uris.isEmpty() || isSubmitting || currentTranscriptionMode != null) return
        val imported = mutableListOf<DraftAttachment>()
        val result = runCatching {
            uris.forEach { uri ->
                val attachment = importDraftAttachment(context, uri)
                val supported = when (attachment.category) {
                    AttachmentCategory.IMAGE ->
                        attachmentCapabilities.vision?.supportedMimeTypes
                    AttachmentCategory.AUDIO ->
                        attachmentCapabilities.audioInput?.supportedMimeTypes
                    AttachmentCategory.DOCUMENT ->
                        attachmentCapabilities.documentInput?.supportedMimeTypes
                }.orEmpty()
                require(attachment.mimeType in supported) { attachmentUnsupportedMessage }
                if (attachment.category == AttachmentCategory.DOCUMENT) {
                    // 上传链路的文档在选择入口就拒绝超限文件，避免导入归档后才失败。
                    val documentInput = attachmentCapabilities.documentInput
                    if (documentInput?.ingestMode == FileIngestMode.FILE_REFERENCE &&
                        attachment.sizeBytes > documentInput.maxUploadBytes
                    ) {
                        val limitMb = documentInput.maxUploadBytes / (1024 * 1024)
                        require(false) { context.getString(R.string.chat_attachment_too_large, limitMb) }
                    }
                }
                imported += attachment
            }
            mergeAttachmentSelection(messageData.attachments, imported)
        }.getOrElse { failure ->
            deleteManagedDrafts(context, imported)
            Toast.makeText(
                context,
                failure.message ?: attachmentReadFailedMessage,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        when (result) {
            is AttachmentSelectionResult.MixedTypes -> {
                deleteManagedDrafts(context, imported)
                Toast.makeText(context, mixedAttachmentMessage, Toast.LENGTH_SHORT).show()
            }
            is AttachmentSelectionResult.Accepted -> {
                deleteManagedDrafts(context, result.replaced)
                deleteManagedDrafts(context, imported.filterNot { it in result.attachments })
                onDraftChange(currentMessageData.copy(attachments = result.attachments))
                // chip 阶段预热上传：选完即传，发送时 file_id 必然就绪。
                imported.filter {
                    it.category == AttachmentCategory.DOCUMENT && !it.isVendorUploaded
                }.forEach { currentOnDocumentDraftImported(it) }
            }
        }
    }

    fun startVoiceInput() {
        if (
            voiceInputState != VoiceInputUiState.Idle ||
            currentTranscriptionMode != null ||
            !isVoiceInputAvailable ||
            isGenerating || isSubmitting
        ) {
            return
        }
        voiceAudio.reset()
        voiceInputState = VoiceInputUiState.Recording()
        val started = voiceRecorder.start(
            onAudioChunk = { chunk ->
                voiceAudio.append(chunk)
                onVoiceAudioChunk(chunk)
            },
            onAudioLevel = { level ->
                coroutineScope.launch {
                    val recording = voiceInputState as? VoiceInputUiState.Recording
                        ?: return@launch
                    voiceInputState = recording.copy(
                        levels = (recording.levels + level).takeLast(MAX_WAVEFORM_SAMPLES),
                    )
                }
            },
            onError = { error ->
                coroutineScope.launch {
                    if (voiceInputState !is VoiceInputUiState.Recording) return@launch
                    voiceRecorder.stop()
                    voiceAudio.reset()
                    voiceInputState = VoiceInputUiState.Idle
                    onVoiceInputStop()
                    voiceErrorMessage = error.message ?: voiceRecordingFailedMessage
                    onVoiceInputError(error)
                }
            },
        )
        if (started) {
            onVoiceInputStart()
        } else {
            voiceInputState = VoiceInputUiState.Idle
        }
    }

    fun cancelVoiceInput() {
        if (voiceInputState !is VoiceInputUiState.Recording) return
        voiceRecorder.stop()
        voiceAudio.reset()
        voiceInputState = VoiceInputUiState.Idle
        onVoiceInputStop()
    }

    fun submitMessage(submitted: MessageData) {
        if (isSubmitting || currentIsGenerating || (submitted.text.isBlank() && submitted.attachments.isEmpty())) return
        if (isFileUploadEnabled && submitted.attachments.any {
                it.category == AttachmentCategory.DOCUMENT && !it.isVendorUploaded
            }
        ) {
            return
        }
        if (!embedded) keyboardController?.hide()
        isSubmitting = true
        onSubmit(submitted) { isSubmitting = false }
    }

    fun finishVoiceInput(mode: VoiceInputMode = VoiceInputMode.TRANSCRIBE) {
        if (voiceInputState !is VoiceInputUiState.Recording) return
        voiceRecorder.stop()
        onVoiceInputStop()
        val pcm = voiceAudio.drain()
        if (pcm.isEmpty()) {
            voiceInputState = VoiceInputUiState.Idle
            voiceErrorMessage = voiceNoAudioMessage
            return
        }
        // 同步退出录音态，重复点击不能再次排入转写或发送；等待状态由 ViewModel 提供。
        voiceInputState = VoiceInputUiState.Idle
        onTranscriptionRequest(pcm, mode, currentMessageData) { result ->
            result.onSuccess { recognized ->
                onDraftChange(recognized.message)
                if (recognized.sendImmediately) submitMessage(recognized.message)
            }.onFailure { error ->
                voiceErrorMessage = if (error is EmptyVoiceTranscriptException) {
                    voiceNoTextMessage
                } else {
                    // 按失败分类给出本地化文案：上游的原始错误（可能含厂商返回体）
                    // 不适合直接展示给用户。
                    context.getString(
                        voiceFailureMessageRes(
                            error.toVoiceFailure().kind,
                            R.string.chat_voice_transcription_failed,
                        ),
                    )
                }
                onVoiceInputError(error)
            }
        }
    }

    LaunchedEffect(isGenerating) {
        if (isGenerating && voiceInputState is VoiceInputUiState.Recording) {
            cancelVoiceInput()
        }
        if (isGenerating) onCancelTranscription()
    }

    LaunchedEffect(voiceInputContextKey) {
        cancelVoiceInput()
    }

    LaunchedEffect(voiceInputState is VoiceInputUiState.Recording) {
        while (voiceInputState is VoiceInputUiState.Recording) {
            delay(1_000)
            val recording = voiceInputState as? VoiceInputUiState.Recording
                ?: break
            val remainingSeconds = (recording.remainingSeconds - 1).coerceAtLeast(0)
            voiceInputState = recording.copy(remainingSeconds = remainingSeconds)
            if (remainingSeconds == 0) {
                finishVoiceInput()
                break
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.release()
            voiceAudio.reset()
            onCancelTranscription()
            deletePendingCameraAttachment(pendingCameraPath)
            // 普通附件归页面草稿所有，切换会话或显示挂起操作时不能随输入组件卸载删除。
        }
    }

    LaunchedEffect(sharedMediaUris, isSubmitting, voiceTranscriptionMode) {
        if (sharedMediaUris.isNotEmpty() && !isSubmitting && voiceTranscriptionMode == null) {
            // 先消费分享来源，再导入快照；草稿更新或发送回执不能触发同一次分享重新导入。
            onSharedMediaConsumed()
            acceptSelection(sharedMediaUris)
        }
    }

    val handleSendClick: () -> Unit = {
        submitMessage(currentMessageData)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia(),
    ) { uris ->
        acceptSelection(uris)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        acceptSelection(uris)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { captured ->
        val capturedUri = pendingCameraUri?.toUri()
        if (captured && capturedUri != null) {
            acceptSelection(listOf(capturedUri))
            deletePendingCameraAttachment(pendingCameraPath)
        } else {
            deletePendingCameraAttachment(pendingCameraPath)
        }
        pendingCameraUri = null
        pendingCameraPath = null
    }

    val cameraErrorMessage = stringResource(R.string.stream_ai_compose_composer_camera_error)

    fun launchCamera() {
        val pendingAttachment = runCatching { createPendingCameraAttachment(context) }
            .getOrElse {
                Toast.makeText(context, cameraErrorMessage, Toast.LENGTH_SHORT).show()
                return
            }
        pendingCameraUri = pendingAttachment.uri.toString()
        pendingCameraPath = pendingAttachment.path
        runCatching { takePictureLauncher.launch(pendingAttachment.uri) }
            .onFailure {
                deletePendingCameraAttachment(pendingAttachment.path)
                pendingCameraUri = null
                pendingCameraPath = null
                Toast.makeText(context, cameraErrorMessage, Toast.LENGTH_SHORT).show()
            }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(context, cameraErrorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val componentFactory = LocalChatAiComponentFactory.current
    val recordingState = voiceInputState as? VoiceInputUiState.Recording

    LaunchedEffect(Unit) {
        onExpandedChange(true)
    }

    Column(
        modifier = modifier.then(if (embedded) Modifier else Modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp))
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_composer_surface"),
            shape = RoundedCornerShape(28.dp),
            color = if (embedded) Color.Transparent else chatCapsuleColor(),
            border = if (embedded) null else chatCapsuleBorder(),
            tonalElevation = 0.dp,
            // 仅保留轻微悬浮感，避免底边形成厚重阴影。
            shadowElevation = if (embedded) 0.dp else 0.5.dp,
        ) {
            Column {
                headerContent()
                if (recordingState != null) {
                    with(componentFactory) {
                        VoiceRecordingContent(
                            VoiceRecordingContentParams(
                                levels = recordingState.levels,
                                remainingSeconds = recordingState.remainingSeconds,
                                onCancel = ::cancelVoiceInput,
                                onFinish = { finishVoiceInput(VoiceInputMode.TRANSCRIBE) },
                                onSend = { finishVoiceInput(VoiceInputMode.SEND) },
                            ),
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val renderInput: @Composable (ComposerInputContentParams) -> Unit = inputContent
                            ?: { params -> with(componentFactory) { ComposerInputContent(params) } }
                        renderInput(
                                ComposerInputContentParams(
                                    messageData = messageData,
                                    isGenerating = isGenerating || isSubmitting,
                                    configurationReady = addToChatState.configuration != null,
                                    voiceInputState = voiceTranscriptionMode?.let { VoiceInputUiState.Transcribing(it) }
                                        ?: voiceInputState,
                                    isVoiceInputAvailable = isVoiceInputAvailable,
                                    voiceErrorMessage = voiceErrorMessage,
                                    onVoiceErrorShown = { voiceErrorMessage = null },
                                    onTextChange = { onDraftChange(currentMessageData.copy(text = it)) },
                                    onRemoveAttachment = remove@{ uri ->
                                        if (isSubmitting) return@remove
                                        onDraftChange(
                                            currentMessageData.copy(
                                                attachments = currentMessageData.attachments - uri,
                                            ),
                                        )
                                        deleteManagedDrafts(context, listOf(uri))
                                    },
                                    onSendClick = handleSendClick,
                                    onStopClick = onStopClick,
                                    onVoiceInputStart = ::startVoiceInput,
                                    retainExpanded = retainExpanded,
                                    onExpandedChange = onExpandedChange,
                                    onAttachmentsClick = { if (!isSubmitting) showAttachmentOptions = true },
                                    modelSelectorContent = modelSelectorContent,
                                    onOpenVoice = onOpenVoice,
                                    onPermissionModeClick = { if (!isSubmitting) showPermissionMode = true },
                                    // 权限图标直接打开模式抽屉；
                                    // 临时内容请求没有会话配置，不显示 chip。
                                    showToolChip = showPermissionOption && addToChatState.configuration != null,
                                    fullAccess = addToChatState.fullAccess,
                                    attachmentsEnabled = attachmentsEnabled,
                                    attachmentUploadStates = attachmentUploadStates,
                                    onRetryAttachmentUpload = { currentOnRetryUpload(it) },
                                ),
                        )
                    }
                }
            }
        }
    }

    if (showPermissionMode) {
        ChatPermissionModeSheet(
            fullAccess = addToChatState.fullAccess,
            onFullAccessChange = onFullAccessChange,
            onDismiss = { showPermissionMode = false },
        )
    }

    if (showAttachmentOptions && attachmentsEnabled) {
        ChatAddToChatSheet(
            state = addToChatState,
            uploadedFiles = uploadedFiles,
            uploadedFilesEnabled = isFileUploadEnabled,
            onUploadedFileDelete = onUploadedFileDelete,
            onAttachUploadedFile = { entry ->
                onAttachUploadedFile(entry)
                showAttachmentOptions = false
            },
            onDismiss = { showAttachmentOptions = false },
            onTakePhoto = {
                showAttachmentOptions = false
                // 应用声明 CAMERA 后，系统拍照 Intent 也要求先获得运行时授权。
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    launchCamera()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onChoosePhotos = {
                showAttachmentOptions = false
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(
                        mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                        maxItems = 3,
                    ),
                )
            },
            onChooseFiles = {
                showAttachmentOptions = false
                val mimeTypes = buildList {
                    attachmentCapabilities.audioInput?.supportedMimeTypes?.let(::addAll)
                    attachmentCapabilities.documentInput?.supportedMimeTypes?.let(::addAll)
                }.distinct().toTypedArray()
                if (mimeTypes.isNotEmpty()) filePickerLauncher.launch(mimeTypes)
            },
            imagesEnabled = attachmentCapabilities.supportsImages,
            filesEnabled = attachmentCapabilities.supportsAudio ||
                attachmentCapabilities.supportsDocuments,
            onMcpServerEnabledChange = onMcpServerEnabledChange,
            onOfficialToolOpened = onOfficialToolOpened,
            onOfficialToolFunctionEnabledChange = onOfficialToolFunctionEnabledChange,
            onOfficialToolFunctionsRetry = onOfficialToolFunctionsRetry,
        )
    }
}

internal fun appendTranscript(draft: String, transcript: String): String {
    val recognized = transcript.trim()
    if (recognized.isEmpty()) return draft
    if (draft.isBlank()) return recognized
    return "${draft.trimEnd()} $recognized"
}

/** 胶囊横向内缩。大胶囊样式下无横向内缩（0.dp），保留常量保证调用兼容。 */
internal val ComposerCollapsedHorizontalInset = 0.dp

/** 胶囊收放的动画规格；跟随胶囊移动的外部内容共用它，避免两段动画错拍。 */
internal val ComposerInsetAnimationSpec: AnimationSpec<Dp> = tween(
    durationMillis = 260,
    easing = FastOutSlowInEasing,
)

private const val MAX_WAVEFORM_SAMPLES = 96
internal class VoicePcmBuffer {
    private val output = ByteArrayOutputStream()

    @Synchronized
    fun append(chunk: ByteArray) {
        output.write(chunk)
    }

    @Synchronized
    fun reset() {
        output.reset()
    }

    @Synchronized
    fun drain(): ByteArray = output.toByteArray().also { output.reset() }
}

/**
 * Data class representing a message composed by the user.
 *
 * @param text The text content of the message.
 * @param attachments The set of attachment URIs to include with the message.
 */
public data class MessageData(
    val text: String = "",
    val attachments: List<DraftAttachment> = emptyList(),
) {
    public companion object {
        /**
         * [Saver] implementation for [MessageData] that converts it to a saveable format.
         */
        internal val Saver: Saver<MessageData, List<Any>> = Saver(
            save = { messageData ->
                listOf(
                    messageData.text,
                ) + messageData.attachments.flatMap { attachment ->
                    listOf(
                        attachment.reference,
                        attachment.displayName,
                        attachment.mimeType,
                        attachment.sizeBytes.toString(),
                        attachment.category.name,
                    )
                }
            },
            restore = { saved ->
                val text = saved.firstOrNull() as? String ?: ""
                val attachments = saved.drop(1)
                    .mapNotNull { it as? String }
                    .chunked(5)
                    .mapNotNull { values ->
                        if (values.size != 5) return@mapNotNull null
                        val category = runCatching {
                            AttachmentCategory.valueOf(values[4])
                        }.getOrNull() ?: return@mapNotNull null
                        // 草稿文件可能已被发送归档或存储清理；恢复时不再展示已失效的附件。
                        if (!File(values[0]).exists()) return@mapNotNull null
                        DraftAttachment(
                            reference = values[0],
                            displayName = values[1],
                            mimeType = values[2],
                            sizeBytes = values[3].toLongOrNull() ?: return@mapNotNull null,
                            category = category,
                        )
                    }
                MessageData(text = text, attachments = attachments)
            },
        )
    }
}

@Composable
internal fun ChatComposerEmpty() {
    ChatComposer(
        onSendClick = { _, reply -> reply(ChatSubmissionResult.ACCEPTED) },
        onStopClick = {},
        isGenerating = false,
        isVoiceInputAvailable = true,
        onOpenVoice = {},
        modelSelectorContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "deepseek-flash 高",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        },
    )
}

@Composable
internal fun ChatComposerFilled() {
    ChatComposer(
        messageData = MessageData(text = "What is Stream Chat?"),
        onSendClick = { _, reply -> reply(ChatSubmissionResult.ACCEPTED) },
        onStopClick = {},
        isGenerating = false,
        isVoiceInputAvailable = true,
        onOpenVoice = {},
        modelSelectorContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "deepseek-flash 高",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        },
    )
}

@Composable
internal fun ChatComposerLongFilled() {
    ChatComposer(
        messageData = MessageData(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."),
        onSendClick = { _, reply -> reply(ChatSubmissionResult.ACCEPTED) },
        onStopClick = {},
        isGenerating = false,
    )
}

@Composable
internal fun ChatComposerWithAttachments() {
    ChatComposer(
        messageData = MessageData(
            text = "What is Stream Chat?",
            attachments = listOf(
                DraftAttachment("1", "one.jpg", "image/jpeg", 1, AttachmentCategory.IMAGE),
            ),
        ),
        onSendClick = { _, reply -> reply(ChatSubmissionResult.ACCEPTED) },
        onStopClick = {},
        isGenerating = false,
    )
}

@Composable
internal fun ChatComposerGenerating() {
    ChatComposer(
        onSendClick = { _, reply -> reply(ChatSubmissionResult.ACCEPTED) },
        onStopClick = {},
        isGenerating = true,
    )
}

@Preview(name = "聊天胶囊 · 完整空闲态 · 浅色", showBackground = true, widthDp = 390)
@Preview(name = "聊天胶囊 · 完整空闲态 · 深色", showBackground = true, widthDp = 390, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatComposerEmptyPreview() {
    GimiTheme {
        ChatComposerEmpty()
    }
}

@Preview(name = "聊天胶囊 · 已输入文本 · 浅色", showBackground = true, widthDp = 390)
@Preview(name = "聊天胶囊 · 已输入文本 · 深色", showBackground = true, widthDp = 390, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatComposerFilledPreview() {
    GimiTheme {
        ChatComposerFilled()
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ChatComposerLongFilledPreview() {
    GimiTheme {
        ChatComposerLongFilled()
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ChatComposerWithAttachmentsPreview() {
    GimiTheme {
        ChatComposerWithAttachments()
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ChatComposerGeneratingPreview() {
    GimiTheme {
        ChatComposerGenerating()
    }
}
