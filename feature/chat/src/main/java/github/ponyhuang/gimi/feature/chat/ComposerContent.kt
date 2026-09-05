package github.ponyhuang.gimi.feature.chat

import android.net.Uri
import android.content.Context
import android.content.Intent
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.R
import kotlinx.coroutines.launch

/**
 * Default implementation of the leading content of the chat composer.
 *
 * Renders the button that opens the system photo picker.
 */
@Composable
internal fun DefaultComposerLeadingContent(params: ComposerLeadingContentParams) {
    val colorScheme = MaterialTheme.colorScheme
    // 会话工具配置未加载完时禁用附件入口，保证弹窗打开即持有可编辑配置。
    val isEnabled = !params.isGenerating && params.configurationReady
    IconButton(
        enabled = isEnabled,
        onClick = params.onAttachmentsClick,
        modifier = Modifier
            .size(48.dp)
            .testTag("chat_composer_add"),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = composerActionContainerColor(),
            contentColor = composerActionIconColor(colorScheme, enabled = true),
            disabledContainerColor = composerActionContainerColor(),
            disabledContentColor = composerActionIconColor(colorScheme, enabled = false),
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.stream_ai_compose_ic_add),
            contentDescription = stringResource(R.string.stream_ai_compose_composer_add_attachments_button),
            tint = composerActionIconColor(colorScheme, enabled = isEnabled),
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
    val focusRequester = remember { FocusRequester() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var wasImeVisible by remember { mutableStateOf(false) }
    var previousImeBottom by remember { mutableIntStateOf(0) }
    var wasRetainExpanded by remember { mutableStateOf(params.retainExpanded) }
    var restoreFocusPending by remember { mutableStateOf(false) }
    val isExpanded = isFocused ||
        params.retainExpanded ||
        restoreFocusPending ||
        params.messageData.attachments.isNotEmpty()
    val transitionSpec = tween<androidx.compose.ui.unit.Dp>(
        durationMillis = 260,
        easing = FastOutSlowInEasing,
    )
    val textStartPadding by animateDpAsState(
        targetValue = if (isExpanded) 6.dp else 62.dp,
        animationSpec = transitionSpec,
        label = "composerTextStartPadding",
    )
    val textEndPadding by animateDpAsState(
        targetValue = if (isExpanded) 6.dp else when (trailingButton) {
            ComposerTrailingButton.Send -> 118.dp
            ComposerTrailingButton.Stop, null -> 62.dp
        },
        animationSpec = transitionSpec,
        label = "composerTextEndPadding",
    )
    val textBottomPadding by animateDpAsState(
        targetValue = if (isExpanded) 56.dp else 0.dp,
        animationSpec = transitionSpec,
        label = "composerTextBottomPadding",
    )
    val animatedVerticalPadding by animateDpAsState(
        targetValue = composerVerticalPadding(isExpanded),
        animationSpec = transitionSpec,
        label = "composerVerticalPadding",
    )

    LaunchedEffect(params.voiceErrorMessage) {
        val message = params.voiceErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        params.onVoiceErrorShown()
    }

    LaunchedEffect(isFocused, imeBottom) {
        // IME inset 第一次下降就代表键盘已开始退场，此时立即清焦，
        // 让输入栏收起动画与键盘动画并行；首次打开期间只记录高度，不误收起。
        if (
            shouldClearComposerFocus(
                wasImeVisible = wasImeVisible,
                isFocused = isFocused,
                previousImeBottom = previousImeBottom,
                currentImeBottom = imeBottom,
            )
        ) {
            focusManager.clearFocus()
            wasImeVisible = false
        } else if (isFocused && imeBottom > 0) {
            wasImeVisible = true
        } else if (!isFocused) {
            wasImeVisible = false
        }
        previousImeBottom = imeBottom
    }

    LaunchedEffect(params.retainExpanded) {
        if (
            shouldRestoreComposerFocus(
                wasRetainExpanded = wasRetainExpanded,
                retainExpanded = params.retainExpanded,
            )
        ) {
            // Dialog 会先触发旧 IME 的退场；保留展开态并等待退场结束，
            // 避免刚恢复的焦点又被下降中的 IME inset 清掉。
            restoreFocusPending = true
        } else if (params.retainExpanded) {
            restoreFocusPending = false
        }
        wasRetainExpanded = params.retainExpanded
    }

    LaunchedEffect(restoreFocusPending, imeBottom) {
        if (shouldPerformPendingComposerFocusRestore(restoreFocusPending, imeBottom)) {
            focusRequester.requestFocus()
            keyboardController?.show()
            restoreFocusPending = false
        }
    }

    LaunchedEffect(isExpanded) {
        params.onExpandedChange(isExpanded)
    }

    Column(
        modifier = modifier.padding(
            horizontal = 10.dp,
            vertical = animatedVerticalPadding,
        ),
    ) {
        SnackbarHost(hostState = snackbarHostState)

        if (isTranscribing) {
            TranscribingContent()
        } else {
            AttachmentList(
                attachments = params.messageData.attachments,
                onRemoveAttachment = params.onRemoveAttachment,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth()
                        .padding(
                            start = textStartPadding,
                            end = textEndPadding,
                            bottom = textBottomPadding,
                        )
                        .defaultMinSize(minHeight = LocalMinimumInteractiveComponentSize.current)
                        .onPreviewKeyEvent { event ->
                            // 物理键盘回车（无 Shift/Ctrl/Alt）触发发送；Shift+Enter 留给换行。
                            // 与"发送"按钮显隐规则一致：仅在有内容可发时触发。
                            val isBareEnter = event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                                !event.isShiftPressed && !event.isCtrlPressed && !event.isAltPressed
                            val canSend = !params.isGenerating &&
                                (params.messageData.text.isNotBlank() || params.messageData.attachments.isNotEmpty())
                            if (isBareEnter && canSend) {
                                params.onSendClick()
                                true
                            } else {
                                false
                            }
                        }
                        .testTag("chat_composer_text_field"),
                    value = params.messageData.text,
                    onValueChange = params.onTextChange,
                    enabled = !params.isGenerating,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { params.onSendClick() }),
                    textStyle = resolveTextFieldStyle(
                        interactionSource,
                        disabled = params.isGenerating,
                    ),
                    cursorBrush = SolidColor(OutlinedTextFieldDefaults.colors().cursorColor),
                    interactionSource = interactionSource,
                    maxLines = if (isExpanded) 6 else 1,
                    minLines = 1,
                    decorationBox = { innerTextField ->
                        TextInput(
                            modifier = Modifier.fillMaxWidth(),
                            text = params.messageData.text,
                            innerTextField = innerTextField,
                        )
                    },
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    with(LocalChatAiComponentFactory.current) {
                        ComposerLeadingContent(
                            ComposerLeadingContentParams(
                                isGenerating = params.isGenerating,
                                configurationReady = params.configurationReady,
                                onAttachmentsClick = params.onAttachmentsClick,
                            ),
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (isExpanded) params.modelSelectorContent()
                    }

                    VoiceButton(
                        isGenerating = params.isGenerating,
                        isVoiceInputAvailable = params.isVoiceInputAvailable,
                        snackbarHostState = snackbarHostState,
                        onVoiceInputStart = params.onVoiceInputStart,
                    )
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
    }
}

internal fun shouldClearComposerFocus(
    wasImeVisible: Boolean,
    isFocused: Boolean,
    previousImeBottom: Int,
    currentImeBottom: Int,
): Boolean = wasImeVisible && isFocused && currentImeBottom < previousImeBottom

internal fun shouldRestoreComposerFocus(
    wasRetainExpanded: Boolean,
    retainExpanded: Boolean,
): Boolean = wasRetainExpanded && !retainExpanded

internal fun shouldPerformPendingComposerFocusRestore(
    isPending: Boolean,
    imeBottom: Int,
): Boolean = isPending && imeBottom == 0

internal fun composerVerticalPadding(isExpanded: Boolean) = if (isExpanded) 8.dp else 4.dp

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
    attachments: List<DraftAttachment>,
    onRemoveAttachment: (DraftAttachment) -> Unit,
) {
    if (attachments.isNotEmpty()) {
        AttachmentList(
            modifier = Modifier.fillMaxWidth(),
            attachments = attachments,
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
