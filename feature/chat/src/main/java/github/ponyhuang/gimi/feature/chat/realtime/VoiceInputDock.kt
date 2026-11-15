package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.AttachmentList
import github.ponyhuang.gimi.feature.chat.ComposerInputContentParams
import github.ponyhuang.gimi.feature.chat.R

/** 同一胶囊从工具栏展开为编辑器；尺寸与控件位置共用一段 280ms 过渡。 */
@Composable
internal fun VoiceInputDock(
    state: RealtimeVoiceUiState,
    onSettings: () -> Unit,
    onChat: () -> Unit,
    onCall: () -> Unit,
    onMute: () -> Unit,
    composerContent: @Composable () -> Unit,
    settingsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val editing = state.composer.editing
    val colors = MaterialTheme.colorScheme
    val enabled = !state.switching
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val width by animateDpAsState(
            if (editing) maxWidth else minOf(maxWidth, 320.dp), tween(280), label = "voice-input-width",
        )
        val controlsHeight by animateDpAsState(if (editing) 48.dp else 0.dp, tween(280), label = "voice-controls-rise")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(controlsHeight).clipToBounds()) {
                if (editing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.voice_input_paused), Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    DockButton(Icons.Default.Tune, stringResource(R.string.voice_more_options), onSettings, enabled && settingsEnabled)
                    DockButton(Icons.Default.CallEnd, stringResource(R.string.voice_end), onCall, enabled, contentColor = colors.error)
                    DockButton(Icons.Default.MicOff, stringResource(R.string.voice_input_paused), {}, false)
                }
            }
            Surface(
                modifier = Modifier.width(width).animateContentSize(tween(280)),
                shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer,
                border = BorderStroke(1.dp, colors.outlineVariant), tonalElevation = 0.dp,
            ) {
                AnimatedContent(
                    targetState = editing,
                    transitionSpec = { fadeIn(tween(160, 80)) togetherWith fadeOut(tween(100)) },
                    label = "voice-input-content",
                ) { expanded ->
                    if (expanded) composerContent()
                    else Row(
                        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DockButton(Icons.Default.Tune, stringResource(R.string.voice_more_options), onSettings, enabled && settingsEnabled && !editing, filled = true)
                        DockButton(Icons.Default.Keyboard, stringResource(R.string.voice_input_open), onChat, enabled && state.active && !editing, filled = true)
                        DockButton(
                            if (state.active) Icons.Default.CallEnd else Icons.Default.Call,
                            stringResource(if (state.active) R.string.voice_end else R.string.voice_start),
                            onCall, enabled && !editing, filled = true,
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (state.active) colors.error else colors.onSurface,
                        )
                        DockButton(
                            if (state.voice.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            stringResource(if (state.voice.muted) R.string.voice_unmute else R.string.voice_mute),
                            onMute, enabled && state.active && !editing, filled = true,
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun DockButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean = false,
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val tint = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
    IconButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (filled) containerColor else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = tint,
            disabledContainerColor = if (filled) containerColor else androidx.compose.ui.graphics.Color.Transparent,
            disabledContentColor = tint,
        ),
    ) { Icon(icon, description, Modifier.size(22.dp), tint = tint) }
}

/** 编辑器仅接收受控参数，选择器和 Android 副作用继续由现有 ChatComposer 承载。 */
@Composable
internal fun VoiceComposerInput(
    params: ComposerInputContentParams,
    sendEnabled: Boolean,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        if (params.messageData.attachments.isNotEmpty()) AttachmentList(
            params.messageData.attachments, params.onRemoveAttachment,
            Modifier.fillMaxWidth().heightIn(max = 112.dp),
            uploadStates = params.attachmentUploadStates, onRetryUpload = params.onRetryAttachmentUpload,
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
            if (params.messageData.text.isEmpty()) Text(stringResource(R.string.voice_input_placeholder), color = colors.onSurfaceVariant)
            BasicTextField(
                value = params.messageData.text, onValueChange = params.onTextChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                enabled = !params.isGenerating, maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (sendEnabled) params.onSendClick() }),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = params.onAttachmentsClick, enabled = !params.isGenerating) {
                Icon(Icons.Default.Add, stringResource(R.string.stream_ai_compose_composer_add_attachments_button), tint = colors.onSurface)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose, enabled = !params.isGenerating) { Text(stringResource(R.string.voice_input_collapse), color = colors.onSurfaceVariant) }
            FilledIconButton(
                onClick = params.onSendClick, enabled = sendEnabled && !params.isGenerating,
                modifier = Modifier.size(40.dp), shape = CircleShape,
            ) {
                if (params.isGenerating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.ArrowUpward, stringResource(R.string.voice_input_send))
            }
        }
    }
}
