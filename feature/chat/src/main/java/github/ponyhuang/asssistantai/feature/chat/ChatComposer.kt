package github.ponyhuang.asssistantai.feature.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import github.ponyhuang.asssistantai.feature.chat.R
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
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
 * @param onVoiceInputStart Callback invoked when the user starts voice input. The caller owns
 * audio capture and speech-to-text processing.
 * @param onVoiceInputStop Callback invoked when the user stops voice input.
 * @param onVoiceAudioChunk Callback receiving 16 kHz, mono, signed 16-bit PCM chunks on a
 * background thread. The caller owns speech-to-text processing.
 * @param onVoiceInputError Callback invoked when microphone capture cannot start or fails.
 * @param isGenerating Whether the AI is currently generating a response.
 * @param modifier The modifier to be applied to the composer.
 * @param messageData The initial message data to be displayed in the input field.
 */
@Composable
public fun ChatComposer(
    onSendClick: (data: MessageData) -> Unit,
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
) {
    var messageData by rememberSaveable(stateSaver = MessageData.Saver) {
        mutableStateOf(messageData)
    }
    var showAttachmentOptions by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }
    var voiceErrorMessage by remember { mutableStateOf<String?>(null) }
    val voiceAudio = remember { VoicePcmBuffer() }
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val voiceNoAudioMessage = stringResource(R.string.chat_voice_no_audio_captured)
    val voiceTranscriptionFailedMessage = stringResource(R.string.chat_voice_transcription_failed)
    val voiceRecordingFailedMessage = stringResource(R.string.chat_voice_recording_failed)

    val handleSendClick = {
        keyboardController?.hide()
        onSendClick(messageData)
        messageData = MessageData()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = PickMultipleVisualMedia(),
    ) { uris ->
        messageData = messageData.copy(attachments = messageData.attachments + uris)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { captured ->
        val capturedUri = pendingCameraUri?.toUri()
        if (captured && capturedUri != null) {
            messageData = messageData.copy(attachments = messageData.attachments + capturedUri)
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

    Box(
        modifier = modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                with(componentFactory) {
                    ComposerLeadingContent(
                        ComposerLeadingContentParams(
                            isGenerating = isGenerating || isTranscribing,
                            onAttachmentsClick = { showAttachmentOptions = true },
                        ),
                    )

                    ComposerInputContent(
                        ComposerInputContentParams(
                            messageData = messageData,
                            isGenerating = isGenerating,
                            isTranscribing = isTranscribing,
                            isVoiceInputAvailable = isVoiceInputAvailable,
                            voiceErrorMessage = voiceErrorMessage,
                            onVoiceErrorShown = { voiceErrorMessage = null },
                            onTextChange = { messageData = messageData.copy(text = it) },
                            onRemoveAttachment = { uri ->
                                messageData = messageData.copy(attachments = messageData.attachments - uri)
                                deleteCameraAttachment(context, uri)
                            },
                            onSendClick = handleSendClick,
                            onStopClick = onStopClick,
                            onVoiceInputStart = {
                                voiceAudio.reset()
                                onVoiceInputStart()
                            },
                            onVoiceInputStop = {
                                onVoiceInputStop()
                                val pcm = voiceAudio.drain()
                                if (pcm.isEmpty()) {
                                    voiceErrorMessage = voiceNoAudioMessage
                                } else if (!isTranscribing) {
                                    coroutineScope.launch {
                                        isTranscribing = true
                                        try {
                                            val transcript = onTranscribeVoice(pcm)
                                            messageData = messageData.copy(
                                                text = appendTranscript(messageData.text, transcript),
                                            )
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (error: Throwable) {
                                            voiceErrorMessage = error.message ?: voiceTranscriptionFailedMessage
                                            onVoiceInputError(error)
                                        } finally {
                                            isTranscribing = false
                                        }
                                    }
                                }
                            },
                            onVoiceAudioChunk = { chunk ->
                                voiceAudio.append(chunk)
                                onVoiceAudioChunk(chunk)
                            },
                            onVoiceInputError = { error ->
                                voiceAudio.reset()
                                voiceErrorMessage = error.message ?: voiceRecordingFailedMessage
                                onVoiceInputError(error)
                            },
                        ),
                    )

                    ComposerTrailingContent(ComposerTrailingContentParams(isGenerating = isGenerating))
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
        AttachmentSourceSheet(
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
        )
    }
}

internal fun appendTranscript(draft: String, transcript: String): String {
    val recognized = transcript.trim()
    if (recognized.isEmpty()) return draft
    if (draft.isBlank()) return recognized
    return "${draft.trimEnd()} $recognized"
}

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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AttachmentSourceSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhotos: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.stream_ai_compose_composer_take_photo)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onTakePhoto),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.stream_ai_compose_composer_choose_photos)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable(onClick = onChoosePhotos),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }
}

/**
 * Data class representing a message composed by the user.
 *
 * @param text The text content of the message.
 * @param attachments The set of attachment URIs to include with the message.
 */
public data class MessageData(
    val text: String = "",
    val attachments: List<Uri> = emptyList(),
) {
    public companion object {
        /**
         * [Saver] implementation for [MessageData] that converts it to a saveable format.
         */
        internal val Saver: Saver<MessageData, List<Any>> = Saver(
            save = { messageData ->
                listOf(
                    messageData.text,
                ) + messageData.attachments.map(Uri::toString)
            },
            restore = { saved ->
                val text = saved.firstOrNull() as? String ?: ""
                val attachmentStrings = saved.drop(1).mapNotNull { it as? String }
                val attachments = attachmentStrings.map(String::toUri)
                MessageData(text = text, attachments = attachments)
            },
        )
    }
}

@Composable
internal fun ChatComposerEmpty() {
    ChatComposer(
        onSendClick = {},
        onStopClick = {},
        isGenerating = false,
    )
}

@Composable
internal fun ChatComposerFilled() {
    ChatComposer(
        messageData = MessageData(text = "What is Stream Chat?"),
        onSendClick = {},
        onStopClick = {},
        isGenerating = false,
    )
}

@Composable
internal fun ChatComposerLongFilled() {
    ChatComposer(
        messageData = MessageData(text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."),
        onSendClick = {},
        onStopClick = {},
        isGenerating = false,
    )
}

@Composable
internal fun ChatComposerWithAttachments() {
    ChatComposer(
        messageData = MessageData(
            text = "What is Stream Chat?",
            attachments = listOf("1".toUri(), "2".toUri(), "3".toUri()),
        ),
        onSendClick = {},
        onStopClick = {},
        isGenerating = false,
    )
}

@Composable
internal fun ChatComposerGenerating() {
    ChatComposer(
        onSendClick = {},
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
