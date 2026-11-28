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
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import github.ponyhuang.gimi.feature.chat.AttachmentList
import github.ponyhuang.gimi.feature.chat.ComposerInputContentParams
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.LocalGimiDarkTheme

/**
 * 语音文本编辑卡内嵌顶栏与底栏所需的通话控制及链路摘要上下文。
 *
 * 由 [VoiceInputDock] 在展开态注入，使嵌入在 `ChatComposer` 内部的 [VoiceComposerInput]
 * 能直接渲染「输入中，收音已暂停」胶囊、快捷挂断键以及底部链路模型摘要，无需额外外部悬浮工具行。
 *
 * @property onEndCall 顶部快捷挂断回调。
 * @property endCallEnabled 快捷挂断是否可点（通话已启动且非切换中）。
 * @property pipelineSummary 底部辅助显示的当前链路与主模型摘要（如「级联 · deepseek-flash」）。
 */
internal data class VoiceComposerDockContext(
    val onEndCall: () -> Unit = {},
    val endCallEnabled: Boolean = true,
    val pipelineSummary: String = "",
)

internal val LocalVoiceComposerDockContext = compositionLocalOf { VoiceComposerDockContext() }

/**
 * 语音底部操作栏：按照 Material 3 Expressive Button-Groups 规范统一布局。
 *
 * 待通话态 / 通话中态采用「分翼连结群组」架构：
 * 1. 左翼连结组 [设置, 键盘]：次级辅助工具，左外 28dp 圆角，内侧接缝处采用细分割线。
 * 2. 中央 Hero 按钮：高权重通话胶囊，待启动为 28dp 全圆胶囊 (Call)，通话中弹性形变为 16dp 圆角方枕 (CallEnd)。
 * 3. 右翼连结组 [静音, 退出]：静音采用中性灰底与形态微收缩（去除高亮红强刺激），退出键独占鲜艳实心红。
 *
 * 点击键盘展开文字编辑器（方案 A）时，收音暂停状态、快捷挂断与链路模型摘要全部内嵌进输入卡，
 * 释放垂直空间避免软键盘弹出时挤压中央粒子球。
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
    val pipelineTitle = stringResource(
        when (state.pipeline) {
            VoicePipeline.CASCADED -> R.string.voice_pipeline_cascaded
            VoicePipeline.SEMI_CASCADED -> R.string.voice_pipeline_semi_cascaded
            VoicePipeline.NATIVE_REALTIME -> R.string.voice_pipeline_native_realtime
        },
    )
    val primaryModelName = state.modelName.ifBlank { state.chatModelName }
    val pipelineSummary = if (primaryModelName.isNotBlank()) {
        "$pipelineTitle · $primaryModelName"
    } else {
        pipelineTitle
    }
    val dockContext = remember(onCall, enabled, state.active, pipelineSummary) {
        VoiceComposerDockContext(
            onEndCall = onCall,
            endCallEnabled = enabled && state.active,
            pipelineSummary = pipelineSummary,
        )
    }
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
                CompositionLocalProvider(LocalVoiceComposerDockContext provides dockContext) {
                    Surface(
                        modifier = Modifier.width(width),
                        shape = RoundedCornerShape(26.dp),
                        color = colors.surfaceContainer,
                        border = BorderStroke(0.8.dp, colors.outlineVariant),
                        tonalElevation = 0.dp,
                    ) {
                        composerContent()
                    }
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
    val isDark = LocalGimiDarkTheme.current

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

/**
 * 语音通话内嵌文字编辑器（方案 A）：
 * 顶部状态栏内嵌「输入中，收音已暂停」胶囊、快捷挂断键与收起按钮；
 * 中部承载附件列表与多行输入框；
 * 底部工具栏左侧放置添加附件与当前链路/模型摘要，右侧放置主色圆形发送按钮。
 */
@Composable
internal fun VoiceComposerInput(
    params: ComposerInputContentParams,
    sendEnabled: Boolean,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = LocalGimiDarkTheme.current
    val dockContext = LocalVoiceComposerDockContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 顶部状态栏：左置收音暂停胶囊，右置快捷挂断与收起键盘
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(colors.outline, CircleShape),
                    )
                    Text(
                        text = stringResource(R.string.voice_input_paused),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val hangupBg = colors.errorContainer
                val hangupTint = if (isDark) colors.onErrorContainer else colors.error
                Surface(
                    onClick = dockContext.onEndCall,
                    enabled = dockContext.endCallEnabled && !params.isGenerating,
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = if (dockContext.endCallEnabled) hangupBg else hangupBg.copy(alpha = 0.45f),
                    contentColor = if (dockContext.endCallEnabled) hangupTint else hangupTint.copy(alpha = 0.45f),
                    tonalElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = stringResource(R.string.voice_end),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Surface(
                    onClick = onClose,
                    enabled = !params.isGenerating,
                    shape = CircleShape,
                    color = Color.Transparent,
                    contentColor = colors.onSurfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.voice_input_collapse),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (params.messageData.attachments.isNotEmpty()) {
            AttachmentList(
                attachments = params.messageData.attachments,
                onRemoveAttachment = params.onRemoveAttachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 96.dp),
                uploadStates = params.attachmentUploadStates,
                onRetryUpload = params.onRetryAttachmentUpload,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .heightIn(min = 36.dp),
        ) {
            if (params.messageData.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.voice_input_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = params.messageData.text,
                onValueChange = params.onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                enabled = !params.isGenerating,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (sendEnabled) params.onSendClick() }),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = params.onAttachmentsClick,
                enabled = !params.isGenerating,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.stream_ai_compose_composer_add_attachments_button),
                    tint = colors.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (dockContext.pipelineSummary.isNotBlank()) {
                Text(
                    text = dockContext.pipelineSummary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            FilledIconButton(
                onClick = params.onSendClick,
                enabled = sendEnabled && !params.isGenerating,
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
            ) {
                if (params.isGenerating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.voice_input_send),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
