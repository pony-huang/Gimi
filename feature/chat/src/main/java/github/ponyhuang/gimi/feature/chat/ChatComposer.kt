package github.ponyhuang.gimi.feature.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import github.ponyhuang.gimi.core.audio.VoiceAudioRecorder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import java.io.ByteArrayOutputStream
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
 * be overridden without replacing the whole composer. See [CompoundChatAiComponentFactory].
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
 * @param messageData The initial message data to be displayed in the input field.
 * @param modelSelectorContent Model selection control rendered beside the attachment button.
 * @param retainExpanded Whether an active child surface requires the composer to stay expanded.
 * @param onExpandedChange Callback reporting whether the capsule is currently in its enlarged
 * state, so surrounding content can align with it.
 */
@Composable
public fun ChatComposer(
    onSendClick: (data: MessageData) -> Boolean,
    onStopClick: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
    messageData: MessageData = MessageData(),
    onVoiceInputStart: () -> Unit = { },
    onVoiceInputStop: () -> Unit = { },
    onVoiceAudioChunk: (ByteArray) -> Unit = { },
    onVoiceInputError: (Throwable) -> Unit = { },
    isVoiceInputAvailable: Boolean = false,
    onTranscribeVoice: suspend (ByteArray) -> String = { error("transcription not configured") },
    retainExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = { },
    modelSelectorContent: @Composable () -> Unit = { },
    addToChatState: ChatAddToChatState = ChatAddToChatState(),
    onLocalToolEnabledChange: (String, Boolean) -> Unit = { _, _ -> },
    onToolAccessModeChange: (ToolAccessMode) -> Unit = { _ -> },
    onMcpServerEnabledChange: (String, Boolean) -> Unit = { _, _ -> },
    onFullAccessChange: (Boolean) -> Unit = { _ -> },
    onOfficialToolOpened: (String) -> Unit = { _ -> },
    onOfficialToolFunctionEnabledChange: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onOfficialToolFunctionsRetry: (String) -> Unit = { _ -> },
    attachmentCapabilities: MultimodalCapabilities = MultimodalCapabilities(),
    sharedMediaUris: List<Uri> = emptyList(),
    onSharedMediaConsumed: () -> Unit = {},
) {
    var messageData by rememberSaveable(stateSaver = MessageData.Saver) {
        mutableStateOf(messageData)
    }
    var showAttachmentOptions by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isComposerExpanded by remember { mutableStateOf(messageData.attachments.isNotEmpty()) }
    var voiceInputState: VoiceInputUiState by remember { mutableStateOf(VoiceInputUiState.Idle) }
    var voiceErrorMessage by remember { mutableStateOf<String?>(null) }
    val voiceAudio = remember { VoicePcmBuffer() }
    val voiceRecorder = remember { VoiceAudioRecorder() }
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val voiceNoAudioMessage = stringResource(R.string.chat_voice_no_audio_captured)
    val voiceTranscriptionFailedMessage = stringResource(R.string.chat_voice_transcription_failed)
    val voiceRecordingFailedMessage = stringResource(R.string.chat_voice_recording_failed)
    val mixedAttachmentMessage = stringResource(R.string.chat_attachment_mixed_types)
    val attachmentReadFailedMessage = stringResource(R.string.chat_attachment_read_failed)
    val attachmentUnsupportedMessage = stringResource(R.string.chat_attachment_unsupported)

    fun acceptSelection(uris: List<Uri>) {
        if (uris.isEmpty()) return
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
                imported += attachment
            }
            mergeAttachmentSelection(messageData.attachments, imported)
        }.getOrElse { failure ->
            deleteManagedDrafts(imported)
            Toast.makeText(
                context,
                failure.message ?: attachmentReadFailedMessage,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        when (result) {
            is AttachmentSelectionResult.MixedTypes -> {
                deleteManagedDrafts(imported)
                Toast.makeText(context, mixedAttachmentMessage, Toast.LENGTH_SHORT).show()
            }
            is AttachmentSelectionResult.Accepted -> {
                deleteManagedDrafts(result.replaced)
                deleteManagedDrafts(imported.filterNot { it in result.attachments })
                messageData = messageData.copy(attachments = result.attachments)
            }
        }
    }

    fun startVoiceInput() {
        if (
            voiceInputState != VoiceInputUiState.Idle ||
            !isVoiceInputAvailable ||
            isGenerating
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

    fun finishVoiceInput() {
        if (voiceInputState !is VoiceInputUiState.Recording) return
        voiceRecorder.stop()
        onVoiceInputStop()
        val pcm = voiceAudio.drain()
        if (pcm.isEmpty()) {
            voiceInputState = VoiceInputUiState.Idle
            voiceErrorMessage = voiceNoAudioMessage
            return
        }
        voiceInputState = VoiceInputUiState.Transcribing
        coroutineScope.launch {
            try {
                cancellationAwareRunCatching { onTranscribeVoice(pcm) }
                    .onSuccess { transcript ->
                        messageData = messageData.copy(
                            text = appendTranscript(messageData.text, transcript),
                        )
                    }
                    .onFailure { error ->
                        voiceErrorMessage = error.message ?: voiceTranscriptionFailedMessage
                        onVoiceInputError(error)
                    }
            } finally {
                voiceInputState = VoiceInputUiState.Idle
            }
        }
    }

    LaunchedEffect(isGenerating) {
        if (isGenerating && voiceInputState is VoiceInputUiState.Recording) {
            cancelVoiceInput()
        }
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
            deletePendingCameraAttachment(pendingCameraPath)
            deleteManagedDrafts(messageData.attachments)
        }
    }

    LaunchedEffect(sharedMediaUris) {
        if (sharedMediaUris.isNotEmpty()) {
            acceptSelection(sharedMediaUris)
            onSharedMediaConsumed()
        }
    }

    val handleSendClick = {
        keyboardController?.hide()
        messageData = consumeDraftForSend(messageData, onSendClick)
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

    val componentFactory = LocalChatAiComponentFactory.current
    val recordingState = voiceInputState as? VoiceInputUiState.Recording
    val isCapsuleExpanded = isComposerExpanded || retainExpanded || recordingState != null

    LaunchedEffect(isCapsuleExpanded) {
        onExpandedChange(isCapsuleExpanded)
    }

    Box(
        modifier = modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        val composerHorizontalInset by animateDpAsState(
            targetValue = if (isCapsuleExpanded) 0.dp else ComposerCollapsedHorizontalInset,
            animationSpec = ComposerInsetAnimationSpec,
            label = "composerHorizontalInset",
        )
        Surface(
            modifier = Modifier
                .padding(horizontal = composerHorizontalInset)
                .fillMaxWidth()
                .testTag("chat_composer_surface"),
            shape = RoundedCornerShape(28.dp),
            // 用中性容器色而非 surface+tonalElevation：后者会叠加 primary 色偏蓝，
            // 浅色下与白色背景拉不开、深色下过亮。
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
        ) {
            if (recordingState != null) {
                with(componentFactory) {
                    VoiceRecordingContent(
                        VoiceRecordingContentParams(
                            levels = recordingState.levels,
                            remainingSeconds = recordingState.remainingSeconds,
                            onCancel = ::cancelVoiceInput,
                            onFinish = ::finishVoiceInput,
                        ),
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    with(componentFactory) {
                        ComposerInputContent(
                            ComposerInputContentParams(
                                messageData = messageData,
                                isGenerating = isGenerating,
                                voiceInputState = voiceInputState,
                                isVoiceInputAvailable = isVoiceInputAvailable,
                                voiceErrorMessage = voiceErrorMessage,
                                onVoiceErrorShown = { voiceErrorMessage = null },
                                onTextChange = { messageData = messageData.copy(text = it) },
                                onRemoveAttachment = { uri ->
                                    messageData = messageData.copy(
                                        attachments = messageData.attachments - uri,
                                    )
                                    deleteManagedDrafts(listOf(uri))
                                },
                                onSendClick = handleSendClick,
                                onStopClick = onStopClick,
                                onVoiceInputStart = ::startVoiceInput,
                                retainExpanded = retainExpanded,
                                onExpandedChange = { isComposerExpanded = it },
                                onAttachmentsClick = { showAttachmentOptions = true },
                                modelSelectorContent = modelSelectorContent,
                            ),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isGenerating,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp, y = (-18).dp)
                .zIndex(1f),
        ) {
            AITypingIndicator()
        }
    }

    if (showAttachmentOptions) {
        ChatAddToChatSheet(
            state = addToChatState,
            onDismiss = { showAttachmentOptions = false },
            onTakePhoto = {
                showAttachmentOptions = false
                launchCamera()
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
            onLocalToolEnabledChange = onLocalToolEnabledChange,
            onToolAccessModeChange = onToolAccessModeChange,
            onMcpServerEnabledChange = onMcpServerEnabledChange,
            onFullAccessChange = onFullAccessChange,
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

internal fun consumeDraftForSend(
    draft: MessageData,
    onSend: (MessageData) -> Boolean,
): MessageData = if (onSend(draft)) MessageData() else draft

/** 胶囊收起时的横向内缩。聚焦放大后归零，推荐列表复用同一数值保持边缘对齐。 */
internal val ComposerCollapsedHorizontalInset = 20.dp

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
        onSendClick = { true },
        onStopClick = {},
        isGenerating = false,
    )
}

@Composable
internal fun ChatComposerFilled() {
    ChatComposer(
        messageData = MessageData(text = "What is Stream Chat?"),
        onSendClick = { true },
        onStopClick = {},
        isGenerating = false,
    )
}

@Composable
internal fun ChatComposerLongFilled() {
    ChatComposer(
        messageData = MessageData(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."),
        onSendClick = { true },
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
        onSendClick = { true },
        onStopClick = {},
        isGenerating = false,
    )
}

@Composable
internal fun ChatComposerGenerating() {
    ChatComposer(
        onSendClick = { true },
        onStopClick = {},
        isGenerating = true,
    )
}

@Preview(showBackground = true)
@Composable
private fun ChatComposerEmptyPreview() {
    MaterialTheme {
        ChatComposerEmpty()
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatComposerFilledPreview() {
    MaterialTheme {
        ChatComposerFilled()
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatComposerLongFilledPreview() {
    MaterialTheme {
        ChatComposerLongFilled()
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatComposerWithAttachmentsPreview() {
    MaterialTheme {
        ChatComposerWithAttachments()
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatComposerGeneratingPreview() {
    MaterialTheme {
        ChatComposerGenerating()
    }
}
