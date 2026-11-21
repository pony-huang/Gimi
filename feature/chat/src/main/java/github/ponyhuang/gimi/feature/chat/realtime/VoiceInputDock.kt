package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.AttachmentList
import github.ponyhuang.gimi.feature.chat.ComposerInputContentParams
import github.ponyhuang.gimi.feature.chat.R

/** 同一胶囊从工具栏展开为文字与附件编辑器；尺寸与控件位置共用一段弹性过渡。 */
@Composable
internal fun VoiceInputDock(
    state: RealtimeVoiceUiState,
    onSettings: () -> Unit,
    onChat: () -> Unit,
    onCall: () -> Unit,
    onMute: () -> Unit,
    /** 右端关闭键：结束通话并回到文字聊天（取代原顶栏右上角的聊天切换）。 */
    onBack: () -> Unit,
    composerContent: @Composable () -> Unit,
    settingsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val editing = state.composer.editing
    val colors = MaterialTheme.colorScheme
    val enabled = !state.switching
    val settingsDescription = stringResource(
        if (state.settingsLocked) R.string.voice_settings_after_hangup else R.string.voice_more_options,
    )
    val dockSpring = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val width by animateDpAsState(
            targetValue = if (editing) maxWidth else minOf(maxWidth, 400.dp),
            animationSpec = dockSpring,
            label = "voice-input-width",
        )
        val controlsHeight by animateDpAsState(
            targetValue = if (editing) 48.dp else 0.dp,
            animationSpec = dockSpring,
            label = "voice-controls-rise",
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(controlsHeight).clipToBounds()) {
                if (editing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.voice_input_paused), Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    DockButton(painterResource(R.drawable.ic_settings_voice), settingsDescription, onSettings, enabled && settingsEnabled)
                    DockButton(Icons.Default.CallEnd, stringResource(R.string.voice_end), onCall, enabled, contentColor = colors.error)
                    DockButton(Icons.Default.MicOff, stringResource(R.string.voice_input_paused), {}, false)
                }
            }
            // M3 悬浮工具栏：纯色阶容器，无描边无阴影。
            Surface(
                modifier = Modifier.width(width),
                shape = RoundedCornerShape(34.dp), color = colors.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                AnimatedContent(
                    targetState = editing,
                    transitionSpec = {
                        (fadeIn(tween(150, delayMillis = 40)) togetherWith fadeOut(tween(90)))
                            .using(
                                SizeTransform(clip = false) { _, _ ->
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    )
                                },
                            )
                    },
                    label = "voice-input-content",
                ) { expanded ->
                    if (expanded) composerContent()
                    else Row(
                        Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DockButton(painterResource(R.drawable.ic_settings_voice), settingsDescription, onSettings, enabled && settingsEnabled && !editing,
                            filled = true, containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurfaceVariant)
                        DockButton(Icons.Default.Keyboard, stringResource(R.string.voice_input_open), onChat, enabled && !editing,
                            filled = true, containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurfaceVariant)
                        VoiceCallPill(active = state.active, enabled = enabled && !editing && !state.settingsSaving, onClick = onCall)
                        // 静音为 M3 Toggle：激活时形变为圆角方形并切到 errorContainer。
                        val muted = state.voice.muted
                        val muteCorner by animateDpAsState(if (muted) 16.dp else 24.dp, dockSpring, label = "voice-mute-shape")
                        DockButton(
                            if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                            stringResource(if (muted) R.string.voice_unmute else R.string.voice_mute),
                            onMute, enabled && state.active && !editing, filled = true,
                            containerColor = if (muted) colors.errorContainer else colors.surfaceContainerHigh,
                            contentColor = if (muted) colors.onErrorContainer else colors.onSurfaceVariant,
                            shape = RoundedCornerShape(muteCorner),
                        )
                        // 退出为破坏性操作：M3 填充色调错误色容器，与静音激活态同一套语义。
                        DockButton(Icons.Default.Close, stringResource(R.string.voice_back_to_chat), onBack, true,
                            filled = true, containerColor = colors.errorContainer, contentColor = colors.onErrorContainer)
                    }
                }
            }

        }
    }
}

@Composable
private fun DockButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean, filled: Boolean = false, containerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent, contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, shape: androidx.compose.ui.graphics.Shape = CircleShape) =
    DockButton(rememberVectorPainter(icon), description, onClick, enabled, filled, containerColor, contentColor, shape)

@Composable
private fun DockButton(
    icon: Painter,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean = false,
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    val tint = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
    IconButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp), shape = shape,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (filled) containerColor else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = tint,
            disabledContainerColor = if (filled) containerColor.copy(alpha = containerColor.alpha * .5f) else androidx.compose.ui.graphics.Color.Transparent,
            disabledContentColor = tint,
        ),
    ) { Icon(icon, description, Modifier.size(22.dp), tint = tint) }
}

/**
 * 中央主按钮：待启动为全圆胶囊 + 声波图标；通话中形变为圆角方枕（M3 Expressive），
 * 仅显示居中的停止图标表示可挂断。仅图标，无文字。
 */
@Composable
private fun VoiceCallPill(active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val corner by animateDpAsState(
        targetValue = if (active) 18.dp else 27.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "voice-call-pill-shape",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = colors.primary,
        contentColor = colors.onPrimary,
        modifier = Modifier.width(88.dp).height(54.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (active) Icons.Default.Stop else Icons.Default.GraphicEq,
                stringResource(if (active) R.string.voice_end else R.string.voice_start),
                Modifier.size(if (active) 18.dp else 24.dp),
            )
        }
    }
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
