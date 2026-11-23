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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.AttachmentList
import github.ponyhuang.gimi.feature.chat.ComposerInputContentParams
import github.ponyhuang.gimi.feature.chat.R

/**
 * 语音底部操作栏：按照 Material 3 Expressive Button-Groups 规范统一布局。
 *
 * 待通话态 / 通话中态采用「分翼连结群组」架构：
 * 1. 左翼连结组 [设置, 键盘]：次级辅助工具，左外 28dp 圆角，内侧接缝处采用细分割线。
 * 2. 中央 Hero 按钮：高权重通话胶囊，待启动为 28dp 全圆胶囊 (Call)，通话中弹性形变为 16dp 圆角方枕 (CallEnd)。
 * 3. 右翼连结组 [静音, 退出]：静音采用中性灰底与形态微收缩（去除高亮红强刺激），退出键独占鲜艳实心红。
 *
 * 点击键盘展开文字编辑器时，整体弹性过渡为全宽输入与附件卡片。
 */
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
    val dockSpring = spring<Dp>(
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
                    Text(
                        stringResource(R.string.voice_input_paused),
                        Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    DockButton(painterResource(R.drawable.ic_settings_voice), settingsDescription, onSettings, enabled && settingsEnabled)
                    DockButton(Icons.Default.CallEnd, stringResource(R.string.voice_end), onCall, enabled, contentColor = colors.error)
                    DockButton(Icons.Default.MicOff, stringResource(R.string.voice_input_paused), {}, false)
                }
            }

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
                if (expanded) {
                    Surface(
                        modifier = Modifier.width(width),
                        shape = RoundedCornerShape(28.dp),
                        color = colors.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        composerContent()
                    }
                } else {
                    VoiceSplitConnectedButtonDock(
                        state = state,
                        width = width,
                        enabled = enabled,
                        settingsEnabled = settingsEnabled,
                        settingsDescription = settingsDescription,
                        onSettings = onSettings,
                        onChat = onChat,
                        onCall = onCall,
                        onMute = onMute,
                        onBack = onBack,
                        dockSpring = dockSpring,
                    )
                }
            }
        }
    }
}

/**
 * 方案 A：M3 分翼连结按钮组 (Split Connected Button Groups)
 */
@Composable
private fun VoiceSplitConnectedButtonDock(
    state: RealtimeVoiceUiState,
    width: Dp,
    enabled: Boolean,
    settingsEnabled: Boolean,
    settingsDescription: String,
    onSettings: () -> Unit,
    onChat: () -> Unit,
    onCall: () -> Unit,
    onMute: () -> Unit,
    onBack: () -> Unit,
    dockSpring: androidx.compose.animation.core.SpringSpec<Dp>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f

    val callCorner by animateDpAsState(
        targetValue = if (state.active) 16.dp else 28.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "voice-call-shape",
    )
    val callWidth by animateDpAsState(
        targetValue = if (state.active) 80.dp else 84.dp,
        animationSpec = dockSpring,
        label = "voice-call-width",
    )
    val muted = state.voice.muted
    val muteCorner by animateDpAsState(
        targetValue = if (muted) 16.dp else 24.dp,
        animationSpec = dockSpring,
        label = "voice-mute-shape",
    )

    // 静音按键：中性底色，不再赋予红色，避免过度强调干扰正常通话
    val muteContainerColor = if (muted) {
        colors.surfaceContainerHighest.copy(alpha = if (isDark) 0.6f else 0.8f)
    } else {
        Color.Transparent
    }
    val muteContentColor = if (muted) colors.onSurface else colors.onSurfaceVariant

    // 退出按键：浅色模式下采用高饱和鲜艳实心红配纯白图标，深色模式下采用深红 errorContainer
    val closeContainerColor = if (!isDark) colors.error else colors.errorContainer
    val closeContentColor = if (!isDark) colors.onError else colors.onErrorContainer

    Row(
        modifier = modifier
            .width(width)
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左翼连结群组：辅助工具 [设置, 键盘]
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                bottomStart = 28.dp,
                topEnd = 16.dp,
                bottomEnd = 16.dp,
            ),
            color = colors.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onSettings,
                    enabled = enabled && settingsEnabled && !state.settingsLocked,
                    modifier = Modifier.size(46.dp),
                ) {
                    val tint = if (enabled && settingsEnabled && !state.settingsLocked) {
                        colors.onSurfaceVariant
                    } else {
                        colors.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_voice),
                        contentDescription = settingsDescription,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                VerticalDivider(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp),
                    color = colors.outlineVariant.copy(alpha = 0.5f),
                )
                IconButton(
                    onClick = onChat,
                    enabled = enabled,
                    modifier = Modifier.size(46.dp),
                ) {
                    val tint = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.38f)
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = stringResource(R.string.voice_input_open),
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // 中央主操作：M3 Expressive 通话 Hero 胶囊 (Call ↔ CallEnd)
        Surface(
            onClick = onCall,
            enabled = enabled && !state.settingsSaving,
            modifier = Modifier
                .width(callWidth)
                .height(56.dp),
            shape = RoundedCornerShape(callCorner),
            color = colors.primary,
            contentColor = colors.onPrimary,
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (state.active) Icons.Default.CallEnd else Icons.Default.Call,
                    contentDescription = stringResource(if (state.active) R.string.voice_end else R.string.voice_start),
                    modifier = Modifier.size(if (state.active) 22.dp else 24.dp),
                )
            }
        }

        // 右翼连结群组：通话控制 [静音 Toggle (中性), 退出 (鲜红)]
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                bottomStart = 16.dp,
                topEnd = 28.dp,
                bottomEnd = 28.dp,
            ),
            color = colors.surfaceContainerHigh,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 静音 Toggle (中性灰底/无红)
                val muteEnabled = enabled && state.active
                Surface(
                    onClick = onMute,
                    enabled = muteEnabled,
                    shape = RoundedCornerShape(muteCorner),
                    color = if (muteEnabled) muteContainerColor else Color.Transparent,
                    contentColor = if (muteEnabled) muteContentColor else colors.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (muted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = stringResource(if (muted) R.string.voice_unmute else R.string.voice_mute),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                VerticalDivider(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp),
                    color = colors.outlineVariant.copy(alpha = 0.5f),
                )
                // 退出 / 破坏性操作 (保留鲜红醒目)
                Surface(
                    onClick = onBack,
                    enabled = true,
                    shape = CircleShape,
                    color = closeContainerColor,
                    contentColor = closeContentColor,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.voice_back_to_chat),
                            modifier = Modifier.size(20.dp),
                        )
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
