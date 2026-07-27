package github.ponyhuang.asssistantai.feature.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.feature.chat.R
import kotlinx.coroutines.launch

/**
 * Default implementation of the leading content of the chat composer.
 *
 * Renders the button that opens the system photo picker.
 */
@Composable
internal fun DefaultComposerLeadingContent(params: ComposerLeadingContentParams) {
    FilledIconButton(
        enabled = !params.isGenerating,
        onClick = params.onAttachmentsClick,
        modifier = Modifier
            .size(48.dp)
            .testTag("chat_composer_add"),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
    val isTranscribing = params.voiceInputState == VoiceInputUiState.Transcribing

    val trailingButton = when {
        params.isGenerating -> ComposerTrailingButton.Stop
        isTranscribing -> null
        params.messageData.text.isNotBlank() || params.messageData.attachments.isNotEmpty() ->
            ComposerTrailingButton.Send
        else -> null
    }

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(params.voiceErrorMessage) {
        val message = params.voiceErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        params.onVoiceErrorShown()
    }

    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        SnackbarHost(hostState = snackbarHostState)

        if (isTranscribing) {
            TranscribingContent()
        } else {
            AttachmentList(
                attachments = params.messageData.attachments,
                onRemoveAttachment = params.onRemoveAttachment,
            )
            BasicTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = LocalMinimumInteractiveComponentSize.current),
                value = params.messageData.text,
                onValueChange = params.onTextChange,
                enabled = !params.isGenerating,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { params.onSendClick() }),
                textStyle = resolveTextFieldStyle(interactionSource, disabled = params.isGenerating),
                cursorBrush = SolidColor(OutlinedTextFieldDefaults.colors().cursorColor),
                interactionSource = interactionSource,
                maxLines = 6,
                minLines = 1,
                decorationBox = { innerTextField ->
                    TextInput(
                        modifier = Modifier.fillMaxWidth(),
                        text = params.messageData.text,
                        innerTextField = innerTextField,
                    )
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            with(LocalChatAiComponentFactory.current) {
                ComposerLeadingContent(
                    ComposerLeadingContentParams(
                        isGenerating = params.isGenerating || isTranscribing,
                        onAttachmentsClick = params.onAttachmentsClick,
                    ),
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                params.modelSelectorContent()
            }

            if (!isTranscribing) {
                VoiceButton(
                    isGenerating = params.isGenerating,
                    isVoiceInputAvailable = params.isVoiceInputAvailable,
                    snackbarHostState = snackbarHostState,
                    onVoiceInputStart = params.onVoiceInputStart,
                )
            }
            TrailingButton(
                button = trailingButton,
                onSendClick = params.onSendClick,
                onStopClick = params.onStopClick,
            )
            with(LocalChatAiComponentFactory.current) {
                ComposerTrailingContent(
                    ComposerTrailingContentParams(isGenerating = params.isGenerating),
                )
            }
        }
    }
}

@Composable
private fun TranscribingContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = LocalMinimumInteractiveComponentSize.current)
            .padding(horizontal = 16.dp)
            .testTag(VOICE_TRANSCRIBING_TEST_TAG),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.chat_voice_transcribing),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal const val VOICE_TRANSCRIBING_TEST_TAG = "voice_transcribing"

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
    if (attachments.isNotEmpty()) {
        AttachmentList(
            modifier = Modifier.fillMaxWidth(),
            uris = attachments,
            onRemoveAttachment = onRemoveAttachment,
        )
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
                .padding(horizontal = 6.dp, vertical = 10.dp),
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
    isVoiceInputAvailable: Boolean,
    snackbarHostState: SnackbarHostState,
    onVoiceInputStart: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    if (isGenerating) return

    val snackbarMessage = stringResource(R.string.stream_ai_compose_composer_mic_permission_message)
    val actionLabel = stringResource(R.string.stream_ai_compose_composer_mic_permission_action)
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            onVoiceInputStart()
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
        when {
            !isVoiceInputAvailable -> Unit
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> onVoiceInputStart()
            else -> permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    with(LocalChatAiComponentFactory.current) {
        SpeechToTextButtonIdleContent(
            SpeechToTextButtonIdleContentParams(
                onClick = onClick,
                enabled = isVoiceInputAvailable,
            ),
        )
    }
}

private enum class ComposerTrailingButton { Send, Stop }

@Composable
private fun TrailingButton(
    button: ComposerTrailingButton?,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    when (button) {
        ComposerTrailingButton.Stop -> TrailingIconButton(
            icon = R.drawable.stream_ai_compose_ic_stop,
            contentDescription = stringResource(R.string.stream_ai_compose_composer_stop_button),
            testTag = "chat_composer_stop",
            onClick = onStopClick,
        )

        ComposerTrailingButton.Send -> TrailingIconButton(
            icon = R.drawable.stream_ai_compose_ic_send,
            contentDescription = stringResource(R.string.stream_ai_compose_composer_send_button),
            testTag = "chat_composer_send",
            onClick = onSendClick,
        )

        null -> Unit
    }
}

@Composable
private fun TrailingIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag),
    ) {
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
