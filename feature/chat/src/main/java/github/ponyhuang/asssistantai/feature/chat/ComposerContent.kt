package github.ponyhuang.asssistantai.feature.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.feature.chat.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Default implementation of the leading content of the chat composer.
 *
 * Renders the button that opens the system photo picker.
 */
@Composable
internal fun DefaultComposerLeadingContent(params: ComposerLeadingContentParams) {
    IconButton(
        enabled = !params.isGenerating,
        onClick = params.onAttachmentsClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.stream_ai_compose_ic_add),
            contentDescription = stringResource(R.string.stream_ai_compose_composer_add_attachments_button),
        )
    }
}

/**
 * Default implementation of the input content of the chat composer.
 *
 * Renders the text field together with the speech-to-text and send/stop controls.
 */
@Composable
internal fun DefaultComposerInputContent(
    modifier: Modifier,
    params: ComposerInputContentParams,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isRecording = remember { mutableStateOf(false) }

    val trailingButton = when {
        params.isGenerating -> ComposerTrailingButton.Stop
        params.isTranscribing -> null
        (params.messageData.text.isNotBlank() || params.messageData.attachments.isNotEmpty()) &&
            !isRecording.value -> ComposerTrailingButton.Send
        else -> null
    }

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(params.voiceErrorMessage) {
        val message = params.voiceErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        params.onVoiceErrorShown()
    }

    Column(modifier = modifier) {
        SnackbarHost(hostState = snackbarHostState)

        BasicTextField(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = LocalMinimumInteractiveComponentSize.current),
            value = params.messageData.text,
            onValueChange = params.onTextChange,
            enabled = !params.isGenerating && !params.isTranscribing && !isRecording.value,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { params.onSendClick() }),
            textStyle = resolveTextFieldStyle(interactionSource, disabled = params.isGenerating),
            cursorBrush = SolidColor(OutlinedTextFieldDefaults.colors().cursorColor),
            interactionSource = interactionSource,
            maxLines = 6,
            minLines = 1,
            decorationBox = { innerTextField ->
                Column {
                    AttachmentList(
                        attachments = params.messageData.attachments,
                        onRemoveAttachment = params.onRemoveAttachment,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        TextInput(
                            modifier = Modifier.weight(1f),
                            text = params.messageData.text,
                            innerTextField = innerTextField,
                        )
                        VoiceButton(
                            isGenerating = params.isGenerating,
                            isTranscribing = params.isTranscribing,
                            isVoiceInputAvailable = params.isVoiceInputAvailable,
                            isRecording = isRecording,
                            snackbarHostState = snackbarHostState,
                            onVoiceInputStart = params.onVoiceInputStart,
                            onVoiceInputStop = params.onVoiceInputStop,
                            onVoiceAudioChunk = params.onVoiceAudioChunk,
                            onVoiceInputError = params.onVoiceInputError,
                        )
                        TrailingButton(
                            button = trailingButton,
                            onSendClick = params.onSendClick,
                            onStopClick = params.onStopClick,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun resolveTextFieldStyle(
    interactionSource: MutableInteractionSource,
    disabled: Boolean,
): TextStyle {
    val colors = OutlinedTextFieldDefaults.colors()
    val textStyle = LocalTextStyle.current
    val textColor = textStyle.color.takeOrElse {
        val focused = interactionSource.collectIsFocusedAsState().value
        when {
            disabled -> colors.disabledTextColor
            focused -> colors.focusedTextColor
            else -> colors.unfocusedTextColor
        }
    }
    return textStyle.merge(TextStyle(color = textColor))
}

@Composable
private fun AttachmentList(
    attachments: List<Uri>,
    onRemoveAttachment: (Uri) -> Unit,
) {
    AnimatedContent(targetState = attachments.isNotEmpty()) { visible ->
        if (visible) {
            AttachmentList(
                modifier = Modifier.fillMaxWidth(),
                uris = attachments,
                onRemoveAttachment = onRemoveAttachment,
            )
        }
    }
}

@Composable
private fun TextInput(
    modifier: Modifier,
    text: String,
    innerTextField: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        ) {
            if (text.isBlank()) {
                Text(
                    text = stringResource(R.string.stream_ai_compose_composer_input_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            innerTextField()
        }
    }
}

@Composable
private fun VoiceButton(
    isGenerating: Boolean,
    isTranscribing: Boolean,
    isVoiceInputAvailable: Boolean,
    isRecording: MutableState<Boolean>,
    snackbarHostState: SnackbarHostState,
    onVoiceInputStart: () -> Unit,
    onVoiceInputStop: () -> Unit,
    onVoiceAudioChunk: (ByteArray) -> Unit,
    onVoiceInputError: (Throwable) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val recorder = remember { VoiceAudioRecorder() }
    var remainingSeconds by remember { mutableIntStateOf(MAX_RECORDING_SECONDS) }
    val currentOnVoiceInputStart by rememberUpdatedState(onVoiceInputStart)
    val currentOnVoiceInputStop by rememberUpdatedState(onVoiceInputStop)
    val currentOnVoiceAudioChunk by rememberUpdatedState(onVoiceAudioChunk)
    val currentOnVoiceInputError by rememberUpdatedState(onVoiceInputError)

    fun stopRecording(submitForRecognition: Boolean = true) {
        if (isRecording.value) {
            recorder.stop()
            isRecording.value = false
            if (submitForRecognition) currentOnVoiceInputStop()
        }
    }

    fun startRecording() {
        if (!isRecording.value && isVoiceInputAvailable && !isTranscribing) {
            remainingSeconds = MAX_RECORDING_SECONDS
            val started = recorder.start(
                onAudioChunk = { currentOnVoiceAudioChunk(it) },
                onError = { error ->
                    coroutineScope.launch {
                        if (isRecording.value) {
                            stopRecording(submitForRecognition = false)
                        }
                        currentOnVoiceInputError(error)
                    }
                },
            )
            if (started) {
                isRecording.value = true
                currentOnVoiceInputStart()
            }
        }
    }

    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            stopRecording(submitForRecognition = false)
        }
    }

    LaunchedEffect(isRecording.value) {
        while (isRecording.value && remainingSeconds > 0) {
            delay(1_000)
            if (isRecording.value) remainingSeconds--
        }
        if (isRecording.value && remainingSeconds == 0) stopRecording()
    }

    DisposableEffect(Unit) {
        onDispose {
            stopRecording(submitForRecognition = false)
            recorder.release()
        }
    }

    AnimatedContent(targetState = isTranscribing) { transcribing ->
        if (transcribing) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            return@AnimatedContent
        }
        val showVoiceButton = !isGenerating
        if (showVoiceButton) {
            val snackbarMessage = stringResource(R.string.stream_ai_compose_composer_mic_permission_message)
            val actionLabel = stringResource(R.string.stream_ai_compose_composer_mic_permission_action)
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { isGranted ->
                if (isGranted) {
                    startRecording()
                } else {
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = snackbarMessage,
                            actionLabel = actionLabel,
                            withDismissAction = true,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            context.openSettings()
                        }
                    }
                }
            }
            val onClick = {
                if (isRecording.value) {
                    stopRecording()
                } else if (!isVoiceInputAvailable) {
                    Unit
                } else if (
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    startRecording()
                } else {
                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }
            with(LocalChatAiComponentFactory.current) {
                if (isRecording.value) {
                    SpeechToTextButtonRecordingContent(
                        SpeechToTextButtonRecordingContentParams(
                            onClick = onClick,
                            rmsdB = 0f,
                            remainingSeconds = remainingSeconds,
                        ),
                    )
                } else {
                    SpeechToTextButtonIdleContent(
                        SpeechToTextButtonIdleContentParams(
                            onClick = onClick,
                            enabled = isVoiceInputAvailable,
                        ),
                    )
                }
            }
        }
    }
}

private const val MAX_RECORDING_SECONDS = 60

private enum class ComposerTrailingButton { Send, Stop }

@Composable
private fun TrailingButton(
    button: ComposerTrailingButton?,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    AnimatedContent(targetState = button) { target ->
        when (target) {
            ComposerTrailingButton.Stop -> TrailingIconButton(
                icon = R.drawable.stream_ai_compose_ic_stop,
                contentDescription = stringResource(R.string.stream_ai_compose_composer_stop_button),
                onClick = onStopClick,
            )

            ComposerTrailingButton.Send -> TrailingIconButton(
                icon = R.drawable.stream_ai_compose_ic_send,
                contentDescription = stringResource(R.string.stream_ai_compose_composer_send_button),
                onClick = onSendClick,
            )

            null -> Unit
        }
    }
}

@Composable
private fun TrailingIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledIconButton(onClick = onClick) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
        )
    }
}

private fun Context.openSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}
