package github.ponyhuang.gimi.feature.chat.realtime

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.TtsVoiceCatalog
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceMode
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.ui.components.GimiSheetBase
import github.ponyhuang.gimi.ui.components.GimiSheetOptionRow
import github.ponyhuang.gimi.ui.components.GimiSheetTitleRow
import github.ponyhuang.gimi.ui.components.SheetHeight
import github.ponyhuang.gimi.ui.components.VoicePickerPage
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 语音抽屉的层级：一级「更多选项」入口 → 二级（模式 / 三组语音设置）→ 三级（模型 / 音色选择）。
 *
 * 全部页面共用同一个 `GimiSheetBase`，只靠 `AnimatedContent` 翻页，不再叠加第二个模态抽屉。
 * [depth] 决定左右滑动方向；[parent] 让标题返回箭头与系统返回键走同一条回退路径。
 */
private sealed interface VoiceSheetPage {
    val depth: Int
    val parent: VoiceSheetPage

    object Entry : VoiceSheetPage {
        override val depth = 0
        override val parent get() = Entry
    }

    object Modes : VoiceSheetPage {
        override val depth = 1
        override val parent get() = Entry
    }

    /** 轻聊预设选择页：仅展示与选择，新增/编辑入口在个性化服务页的预设管理页。 */
    object Presets : VoiceSheetPage {
        override val depth = 1
        override val parent get() = Entry
    }

    data class Settings(val kind: VoiceSettingsKind) : VoiceSheetPage {
        override val depth = 1
        override val parent get() = Entry
    }

    data class ModelPicker(val kind: VoiceSettingsKind) : VoiceSheetPage {
        override val depth = 2
        override val parent get() = Settings(kind)
    }

    data class VoicePicker(val kind: VoiceSettingsKind) : VoiceSheetPage {
        override val depth = 2
        override val parent get() = Settings(kind)
    }
}

/**
 * 语音页场景配色：全部由主题语义角色派生，不再保留独立的场景色值。
 *
 * 场景跟随主题画布——浅色是白→浅灰、深色是黑→深灰的中性阶，控件、文字与字幕
 * 一律取 `onSurface` / `onSurfaceVariant` / `outline` 等角色；品牌蓝只留给
 * 主操作按钮与球体粒子，保证这一屏仍是"中性界面 + 单一蓝色动作焦点"。
 */
internal data class VoiceSceneColors(
    val isDark: Boolean,
    /** 背景垂直渐变的色标（屏幕高度比例 + 颜色）。 */
    val sceneStops: List<Pair<Float, Color>>,
    /** 顶栏胶囊的填充与描边。 */
    val capsuleFill: Color,
    val capsuleLine: Color,
    /** 胶囊上的图标与文字。 */
    val ink: Color,
    val inkDim: Color,
    /** 胶囊上不可用控件的图标色：比 inkDim 再淡一档，让"置灰"看得出来。 */
    val inkDisabled: Color,
    /** 胶囊内竖分隔线。 */
    val divider: Color,
    /** 回答字幕主/次色与滚动渐隐底色。 */
    val caption: Color,
    val captionDim: Color,
    val fade: Color,
)

/**
 * 场景由当前主题推导：顶部一层浅灰雾，往下收到纯画布。
 *
 * 渐变只铺在顶部（顶栏与球体所在区域），42% 之后保持纯画布色——字幕区与底部控件都落在这块
 * 平色上，两个好处：字幕的上下渐隐与背景完全对齐，不会在场景上留下可见的矩形边；
 * 用户话语气泡沿用聊天页那套中性灰，在纯画布上依然分得清角色。
 */
internal fun voiceScene(colors: ColorScheme): VoiceSceneColors = VoiceSceneColors(
    isDark = colors.background.luminance() < .5f,
    sceneStops = listOf(
        0f to colors.surfaceContainerLow,
        .16f to colors.surfaceContainer,
        .30f to colors.surfaceContainerHigh,
        .42f to colors.background,
        1f to colors.background,
    ),
    capsuleFill = colors.surfaceContainerHigh,
    capsuleLine = colors.outline,
    ink = colors.onSurface,
    inkDim = colors.onSurfaceVariant,
    // 禁用态比 inkDim 再淡一档：胶囊图标都显式指定 tint，不换色就和可用态长得一样。
    inkDisabled = colors.onSurfaceVariant.copy(alpha = 0.38f),
    divider = colors.outline,
    caption = colors.onSurface,
    captionDim = colors.onSurfaceVariant,
    // 渐隐底色必须与字幕区所处的场景底色一致，否则滚动时会露出色带。
    fade = colors.background,
)

/** 整屏场景渐变：自上而下由画布落到最高表面容器，保持"雾"的层次但不用彩色光晕。 */
internal fun Modifier.voiceSceneBackground(scene: VoiceSceneColors): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(*scene.sceneStops.toTypedArray()))
}

/** 语音页胶囊内竖分隔线：几何与聊天页 ChatHeaderDivider 一致，颜色跟随主题场景。 */
@Composable
private fun VoiceHeaderDivider(scene: VoiceSceneColors) {
    Box(
        modifier = Modifier
            .padding(vertical = 13.dp)
            .size(width = 1.dp, height = 22.dp)
            .background(scene.divider),
    )
}

/** 模式显示名：模式抽屉两级页面共用同一份文案映射。 */
@StringRes
private fun modeTitleRes(mode: RealtimeVoiceMode): Int = when (mode) {
    RealtimeVoiceMode.Simple -> R.string.voice_mode_simple
    RealtimeVoiceMode.Chat -> R.string.voice_mode_chat
    RealtimeVoiceMode.Realtime -> R.string.voice_mode_realtime
    RealtimeVoiceMode.RealtimeAssistant -> R.string.voice_mode_realtime_assistant
}

/**
 * 实时语音展示页：整屏主题画布（浅色白→浅灰、深色黑→深灰），墨色粒子球居中承载状态文字。
 * 顶栏两胶囊：左（回聊天）+ 右（新建会话｜通话设备），中性底色与描边适配主题场景；
 * 字幕和动效只消费真实会话快照。底栏主按钮为「开始通话/结束通话」，静音移至侧位；
 * 懒启动下未启动时控制组按会话可用性禁用；新建会话仅待启动态的助手模式可点，
 * 通话中重建会话会打断采集/播放，与模式切换一样置灰。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeVoiceScreen(
    uiState: RealtimeVoiceUiState,
    /** 通话是否已启动：决定主按钮「开始通话/结束通话」与控制组可用性。 */
    started: Boolean,
    onStartCall: () -> Unit,
    onToggleMute: () -> Unit,
    /** 挂断主按钮：只结束会话，留在语音页回到待启动状态。 */
    onEnd: () -> Unit,
    // 左胶囊「回聊天」：结束通话并返回文字聊天；后台续跑只保留给系统后台切换。
    onBack: () -> Unit = {},
    // 右胶囊「新会话」：仅待启动态可用；助手模式新建持久化会话并换绑，轻聊/实时模式无会话可换（置灰）。
    onNewSession: () -> Unit = {},
    // 底栏「更多选项」在通话中被点击：抽屉开不了，只提示先结束通话。
    onMoreOptionsBlocked: () -> Unit = {},
    onRetry: () -> Unit = {},
    // 右上角通话设备下拉菜单：打开、收起与选择。
    onOpenDevicePicker: () -> Unit = {},
    onDismissDevicePicker: () -> Unit = {},
    onSelectAudioDevice: (Int?) -> Unit = {},
    onSelectMode: (RealtimeVoiceMode) -> Unit = {},
    /** 设置抽屉的模型写入；`Realtime` 允许传 null 表示「不使用 Realtime」。 */
    onSelectSettingsModel: (VoiceSettingsKind, ModelSelection?) -> Unit = { _, _ -> },
    /** 设置抽屉的音色写入；`Realtime` 允许传 null 表示回到模型缺省音色。 */
    onSelectSettingsVoice: (VoiceSettingsKind, String?) -> Unit = { _, _ -> },
    /** 预设抽屉的轻聊预设写入；null 表示不使用预设，回退内置语音人设。 */
    onSelectPromptPreset: (String?) -> Unit = {},
    /** 音色抽屉的「查看官方音色列表」外链。 */
    onOpenUrl: (String) -> Unit = {},
) {
    val state = uiState.voice
    val colors = MaterialTheme.colorScheme
    val failed = state.phase == RealtimeVoicePhase.Failed
    val savingFailed = state.failure == RealtimeVoiceFailure.Persistence
    val starting = state.phase == RealtimeVoicePhase.Idle || state.phase == RealtimeVoicePhase.Closed
    val controlsEnabled = !starting && !failed && !uiState.switching
    // 新建会话仅待启动态可用：通话中重建会话会打断采集/播放，与模式切换同因需先结束通话；
    // 且只有助手模式有持久化会话可换，轻聊/实时模式的内存会话随每次起呼新建，直接置灰。
    val newSessionEnabled = !started && !uiState.switching && uiState.mode == RealtimeVoiceMode.Chat
    // 语音开启后一律不能更换模式（需先结束通话）；切换期与保存失败时同样冻结菜单。
    val modesEditable = !started && !uiState.switching && !savingFailed
    var modeSheetOpen by remember { mutableStateOf(false) }
    // 抽屉层级由单一状态表达：点行进入下一页、点返回或选完退回上一页，全程只有一个抽屉。
    var sheetPage by remember { mutableStateOf<VoiceSheetPage>(VoiceSheetPage.Entry) }
    val status = when {
        uiState.switching -> R.string.voice_mode_switching
        savingFailed -> R.string.voice_save_failed
        failed -> R.string.voice_failed
        !started -> R.string.voice_ready
        starting -> R.string.voice_connecting
        state.muted -> R.string.voice_muted
        state.phase == RealtimeVoicePhase.Transcribing -> R.string.voice_transcribing
        state.phase == RealtimeVoicePhase.Thinking -> R.string.voice_thinking
        state.phase == RealtimeVoicePhase.Speaking -> R.string.voice_speaking
        else -> R.string.voice_listening
    }
    val failureMessage = when (state.failure) {
        RealtimeVoiceFailure.Configuration -> R.string.voice_configuration_error
        RealtimeVoiceFailure.Microphone -> R.string.voice_microphone_error
        RealtimeVoiceFailure.AudioProcessing -> R.string.voice_audio_error
        RealtimeVoiceFailure.Recognition -> R.string.voice_recognition_error
        RealtimeVoiceFailure.Generation -> R.string.voice_generation_error
        RealtimeVoiceFailure.Synthesis, RealtimeVoiceFailure.Playback -> R.string.voice_synthesis_error
        RealtimeVoiceFailure.Persistence -> R.string.voice_save_caption
        RealtimeVoiceFailure.ConversationBusy -> R.string.voice_conversation_busy
        null -> R.string.voice_error_hint
    }
    val routing = state.audioRouting
    // 通话设备：优先取系统确认的实际输出；没有活跃通话时 currentDeviceId 可能为空，回落到手动首选。
    val currentDevice = routing.devices.firstOrNull { it.id == routing.currentDeviceId }
    val device = currentDevice ?: routing.devices.firstOrNull { it.id == routing.preferredDeviceId }
    val deviceName = voiceAudioDeviceName(device)
    // 设备切换尚未被平台确认时的无障碍描述：读屏要能听到"正在切到哪个设备"。
    val switchingDescription = stringResource(R.string.voice_device_switching, deviceName)
    val scene = voiceScene(colors)
    Scaffold(
        modifier = Modifier.fillMaxSize().voiceSceneBackground(scene),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .heightIn(min = 132.dp).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左槽：更多选项抽屉入口。通话中不开放抽屉——模式与语音配置在通话中本就不可写，
                // 点击只提示原因，不静默吞掉这次点击。
                VoiceControl(
                    Icons.Default.Tune,
                    stringResource(R.string.voice_more_options),
                    main = false, end = false,
                    enabled = !uiState.switching && !savingFailed,
                    contentDescription = stringResource(R.string.voice_open_more_options),
                    onClick = {
                        if (started) {
                            onMoreOptionsBlocked()
                        } else {
                            sheetPage = VoiceSheetPage.Entry
                            modeSheetOpen = true
                        }
                    },
                )
                // 主按钮与静音交换位置：挂断升级为中央主按钮，未启动时承载「开始通话」。
                VoiceControl(
                    if (started) Icons.Default.CallEnd else Icons.Default.Call,
                    stringResource(if (started) R.string.voice_end else R.string.voice_start),
                    main = true, end = started, enabled = started || !uiState.switching,
                    onClick = if (started) onEnd else onStartCall,
                )
                VoiceControl(if (state.muted) Icons.Default.MicOff else Icons.Default.Mic,
                    stringResource(if (state.muted) R.string.voice_unmute else R.string.voice_mute),
                    main = false, end = false, enabled = controlsEnabled, onClick = onToggleMute)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 28.dp)) {
            Box(Modifier.fillMaxWidth().height(64.dp)) {
                // 左胶囊：只留回聊天（结束通话）；中性底色适配主题场景（48dp 点击区 + 上下内缩 2dp 的 44dp 可视高度）。
                Box(Modifier.align(Alignment.CenterStart).offset(x = (-12).dp)) {
                    Surface(Modifier.matchParentSize().padding(vertical = 2.dp), shape = CircleShape,
                        color = scene.capsuleFill, border = BorderStroke(1.dp, scene.capsuleLine)) {}
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.voice_back_to_chat),
                                modifier = Modifier.size(24.dp), tint = scene.inkDim)
                        }
                    }
                }
                Box(Modifier.align(Alignment.CenterEnd).offset(x = 12.dp)) {
                    // 右胶囊：新建会话｜通话设备，两段以竖分隔线划分独立点击区；与聊天页右胶囊共用图标语言。
                    Surface(Modifier.matchParentSize().padding(vertical = 2.dp), shape = CircleShape,
                        color = scene.capsuleFill, border = BorderStroke(1.dp, scene.capsuleLine)) {}
                    Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 新建会话：仅待启动态的助手模式可点；通话中与轻聊/实时模式用更淡的 inkDisabled 明示。
                        IconButton(onClick = onNewSession, enabled = newSessionEnabled, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.voice_new_session),
                                modifier = Modifier.size(24.dp),
                                tint = if (newSessionEnabled) scene.inkDim else scene.inkDisabled)
                        }
                        VoiceHeaderDivider(scene)
                        // 通话设备：图标跟着系统确认的输出设备走，仅通话中可切换。
                        IconButton(
                            onClick = onOpenDevicePicker,
                            enabled = started && !uiState.switching && !routing.switching,
                            modifier = Modifier.size(48.dp),
                        ) {
                            if (routing.switching) {
                                // 切换尚未被平台确认：进度环替代图标，别让"正在切"这件事消失。
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp).semantics {
                                        contentDescription = switchingDescription
                                    },
                                    strokeWidth = 2.dp,
                                    color = scene.ink,
                                )
                            } else {
                                Icon(
                                    imageVector = voiceAudioDeviceIcon(device),
                                    contentDescription = stringResource(R.string.voice_device_action, deviceName),
                                    modifier = Modifier.size(24.dp),
                                    tint = if (started) scene.ink else scene.inkDisabled,
                                )
                            }
                        }
                    }
                    // 菜单锚在右胶囊上：Material 会把菜单收到锚点右缘，正好贴住屏幕右边。
                    DropdownMenu(
                        expanded = uiState.devicePickerOpen,
                        onDismissRequest = onDismissDevicePicker,
                    ) {
                        VoiceAudioDeviceMenuItems(routing, onSelectAudioDevice)
                    }
                }
            }
            // 球体略低于上半区中心，同时保留下方约 43% 空间给字幕与错误恢复内容；
            // 状态文字按参考视频样式移入球心，由 VoiceOrb 自行渲染。
            Column(Modifier.weight(1.2f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val orbSize = minOf(maxWidth, maxHeight, 340.dp)
                    VoiceOrb(state, uiState.switching, stringResource(status), Modifier.size(orbSize).offset(y = 16.dp), ink = scene.ink)
                }
            }
            Box(Modifier.weight(.9f).fillMaxWidth()) {
                when {
                    failed -> Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(failureMessage), style = MaterialTheme.typography.titleMedium, color = scene.ink)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) {
                            Text(stringResource(if (savingFailed) R.string.voice_retry_save else R.string.voice_retry))
                        }
                        if (savingFailed) Text(stringResource(R.string.voice_save_retry_hint), style = MaterialTheme.typography.bodySmall,
                            color = scene.inkDim)
                    }
                    uiState.switching -> VoiceSwitchingIndicator(scene, Modifier.padding(top = 20.dp))
                    else -> VoiceCaptions(
                        inputText = state.inputText,
                        responseText = state.responseText,
                        spokenText = state.spokenText,
                        speakingText = state.speakingText,
                        revision = state.revision,
                        reconnectNotice = state.reconnectNotice,
                        toolNotice = state.toolNotice,
                        responseColor = scene.caption,
                        responseDimColor = scene.captionDim,
                        fadeColor = scene.fade,
                        noticeContainerColor = colors.surfaceContainerHighest,
                        // 提示胶囊是半透明容器，labelMedium 小字用 inkDim 对比度不足。
                        noticeContentColor = scene.ink,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    val settings = uiState.settings
    if (modeSheetOpen) {
        // 系统返回按层级逐级回退：三级选择页 → 二级设置页 → 一级入口；一级交给抽屉本身收起。
        BackHandler(enabled = sheetPage != VoiceSheetPage.Entry) { sheetPage = sheetPage.parent }
        PreferenceStyleScope(PreferenceStyle.Compact) {
            GimiSheetBase(
                onDismissRequest = { modeSheetOpen = false },
                initialHeight = SheetHeight.Fraction(0.5f),
                maxHeight = SheetHeight.Fraction(0.9f),
                // 三级选择页要放得下可搜索长列表，按页切换主体高度上限。
                height = if (sheetPage.depth >= 2) SheetHeight.Fraction(0.9f) else SheetHeight.Fraction(0.5f),
            ) {
                // 页与页之间沿用聊天界面「添加到聊天」抽屉的同一套过渡：进入下一级
                // 整体向左滑出、返回时向右滑回，标题行随页面一起移动而不是原地跳变。
                AnimatedContent(
                    targetState = sheetPage,
                    transitionSpec = {
                        val towards = if (targetState.depth > initialState.depth) {
                            AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        slideIntoContainer(towards = towards, animationSpec = tween(220)) + fadeIn(tween(160)) togetherWith
                            slideOutOfContainer(towards = towards, animationSpec = tween(220)) + fadeOut(tween(120))
                    },
                    label = "voice-sheet-page",
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    Column(Modifier.fillMaxWidth()) {
                        when (page) {
                            is VoiceSheetPage.ModelPicker -> {
                                val kind = page.kind
                                VoiceModelPickerPage(
                                    title = stringResource(page.titleRes()),
                                    rows = when (kind) {
                                        VoiceSettingsKind.Speech -> settings.speechRows
                                        VoiceSettingsKind.Tts -> settings.ttsRows
                                        VoiceSettingsKind.Realtime -> settings.realtimeRows
                                    },
                                    currentSelection = when (kind) {
                                        VoiceSettingsKind.Speech -> settings.speechSelection
                                        VoiceSettingsKind.Tts -> settings.ttsSelection
                                        VoiceSettingsKind.Realtime -> settings.realtimeSelection
                                    },
                                    allowNone = kind == VoiceSettingsKind.Realtime,
                                    onPick = { selection -> onSelectSettingsModel(kind, selection) },
                                    onPickNone = { onSelectSettingsModel(VoiceSettingsKind.Realtime, null) },
                                    // 选完翻回来源设置页：抽屉不关闭，当前值在副标题原地更新。
                                    onBack = { sheetPage = page.parent },
                                )
                            }
                            is VoiceSheetPage.VoicePicker -> {
                                val kind = page.kind
                                if (kind == VoiceSettingsKind.Tts) {
                                    VoicePickerPage(
                                        title = stringResource(page.titleRes()),
                                        currentVoiceId = settings.ttsVoiceId,
                                        options = settings.ttsVoiceOptions,
                                        allowCustomInput = settings.ttsVoiceCustomInputAllowed,
                                        isLoading = settings.isTtsVoiceLoading,
                                        onPick = { voiceId -> onSelectSettingsVoice(VoiceSettingsKind.Tts, voiceId) },
                                        onBack = { sheetPage = page.parent },
                                        // 命名规则与官方音色列表只对开放自定义输入的厂商（千问）有意义。
                                        namingTemplate = if (settings.ttsVoiceCustomInputAllowed) {
                                            TtsVoiceCatalog.QWEN_BASE_VOICE_TEMPLATE
                                        } else {
                                            null
                                        },
                                        docsUrl = if (settings.ttsVoiceCustomInputAllowed) {
                                            TtsVoiceCatalog.QWEN_VOICE_LIST_URL
                                        } else {
                                            null
                                        },
                                        onOpenUrl = onOpenUrl,
                                    )
                                } else {
                                    VoicePickerPage(
                                        title = stringResource(page.titleRes()),
                                        currentVoiceId = settings.realtimeVoiceId ?: settings.realtimeDefaultVoice,
                                        options = settings.realtimeVoiceOptions,
                                        allowCustomInput = settings.realtimeCustomVoiceAllowed,
                                        isLoading = false,
                                        // 清空输入等于回到该模型的缺省音色，因此空串要落成 null 而不是一个空覆盖值。
                                        onPick = { voiceId -> onSelectSettingsVoice(VoiceSettingsKind.Realtime, voiceId.ifBlank { null }) },
                                        onBack = { sheetPage = page.parent },
                                        defaultVoiceId = settings.realtimeDefaultVoice,
                                    )
                                }
                            }
                            else -> {
                                // 一、二级页共用外壳标题行；三级选择页自带标题行（音色输入态会换标题与返回语义）。
                                GimiSheetTitleRow(
                                    title = stringResource(page.titleRes()),
                                    navigationIcon = if (page == VoiceSheetPage.Entry) null else Icons.AutoMirrored.Filled.ArrowBack,
                                    navigationContentDescription = if (page == VoiceSheetPage.Entry) {
                                        null
                                    } else {
                                        stringResource(R.string.voice_sheet_back)
                                    },
                                    onNavigationClick = if (page == VoiceSheetPage.Entry) {
                                        null
                                    } else {
                                        ({ sheetPage = page.parent })
                                    },
                                )
                                when (page) {
                                    VoiceSheetPage.Entry -> VoiceSheetEntryPage(
                                        settings = settings,
                                        currentModeTitle = stringResource(modeTitleRes(uiState.mode)),
                                        modesEnabled = !uiState.switching && !savingFailed,
                                        onOpenModes = { sheetPage = VoiceSheetPage.Modes },
                                        onOpenPresets = { sheetPage = VoiceSheetPage.Presets },
                                        onOpenSettings = { sheetPage = VoiceSheetPage.Settings(it) },
                                    )
                                    // 语音开启后整页禁点，抽屉保持打开以便查看当前模式与原因提示。
                                    VoiceSheetPage.Modes -> VoiceModeSheetOptions(
                                        current = uiState.mode,
                                        chatAvailable = uiState.chatAvailable,
                                        realtimeAvailable = uiState.realtimeAvailable,
                                        interactionEnabled = modesEditable,
                                        blockedByActiveCall = started,
                                        onSelectMode = onSelectMode,
                                    )
                                    VoiceSheetPage.Presets -> VoicePresetPickerPage(
                                        settings = settings,
                                        onSelectPreset = onSelectPromptPreset,
                                    )
                                    is VoiceSheetPage.Settings -> VoiceSettingsPage(
                                        kind = page.kind,
                                        settings = settings,
                                        onOpenModelPicker = { sheetPage = VoiceSheetPage.ModelPicker(it) },
                                        onOpenVoicePicker = { sheetPage = VoiceSheetPage.VoicePicker(it) },
                                    )
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 抽屉各页标题；选择页标题也由这里统一给出，避免多处重复拼 when。 */
@StringRes
private fun VoiceSheetPage.titleRes(): Int = when (this) {
    VoiceSheetPage.Entry -> R.string.voice_more_options
    VoiceSheetPage.Modes -> R.string.voice_select_mode
    VoiceSheetPage.Presets -> R.string.voice_prompt_settings_section
    is VoiceSheetPage.Settings -> when (kind) {
        VoiceSettingsKind.Speech -> R.string.voice_settings_stt_page
        VoiceSettingsKind.Tts -> R.string.voice_settings_tts_page
        VoiceSettingsKind.Realtime -> R.string.voice_settings_realtime_page
    }
    is VoiceSheetPage.ModelPicker -> when (kind) {
        VoiceSettingsKind.Speech -> R.string.voice_settings_pick_stt
        VoiceSettingsKind.Tts -> R.string.voice_settings_pick_tts
        VoiceSettingsKind.Realtime -> R.string.voice_settings_pick_realtime_model
    }
    is VoiceSheetPage.VoicePicker -> when (kind) {
        VoiceSettingsKind.Realtime -> R.string.voice_settings_pick_realtime_voice
        VoiceSettingsKind.Tts, VoiceSettingsKind.Speech -> R.string.voice_settings_pick_tts_voice
    }
}

/** 四个对话模式的语义图标，与抽屉里其它菜单行共用图标槽位。 */
private fun modeIcon(mode: RealtimeVoiceMode): ImageVector = when (mode) {
    RealtimeVoiceMode.Simple -> Icons.AutoMirrored.Filled.Chat
    RealtimeVoiceMode.Chat -> Icons.Default.SmartToy
    RealtimeVoiceMode.Realtime -> Icons.Default.FlashOn
    RealtimeVoiceMode.RealtimeAssistant -> Icons.Default.AutoAwesome
}

/**
 * 模式抽屉一级页：模式入口 + 轻聊预设 + 三个语音设置条目，各带当前值副标题。
 * 条目与聊天界面抽屉同一套 One UI 语言：灰画布上的白色圆角分组卡片 + 内缩分隔线。
 */
@Composable
private fun VoiceSheetEntryPage(
    settings: VoiceSettingsUiState,
    currentModeTitle: String,
    modesEnabled: Boolean,
    onOpenModes: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenSettings: (VoiceSettingsKind) -> Unit,
) {
    // 设置条目永远可进入查看；写入由二级页与 ViewModel 按通话/Agent 状态拦截。
    val speechModel = settings.speechRows.selectionName(settings.speechSelection)
    val ttsModel = settings.ttsRows.selectionName(settings.ttsSelection)
    val ttsVoice = settings.ttsVoiceOptions.firstOrNull { it.id == settings.ttsVoiceId }?.name
    val realtimeModel = settings.realtimeRows.selectionName(settings.realtimeSelection)
    val promptPreset = settings.selectedPromptPreset()?.name
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PreferenceGroupCard(Modifier.padding(top = 8.dp)) {
            PreferenceListItem(
                icon = Icons.Default.Tune,
                title = stringResource(R.string.voice_mode_entry),
                subtitle = stringResource(R.string.voice_mode_current, currentModeTitle),
                onClick = onOpenModes.takeIf { modesEnabled },
                showDivider = true,
            )
            PreferenceListItem(
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.voice_prompt_presets),
                subtitle = promptPreset ?: stringResource(R.string.voice_prompt_preset_none),
                onClick = onOpenPresets,
                showDivider = true,
            )
            PreferenceListItem(
                icon = Icons.Default.Mic,
                title = stringResource(R.string.voice_settings_stt_title),
                subtitle = speechModel ?: stringResource(R.string.voice_settings_not_configured),
                onClick = { onOpenSettings(VoiceSettingsKind.Speech) },
                showDivider = true,
            )
            PreferenceListItem(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = stringResource(R.string.voice_settings_tts_title),
                subtitle = listOfNotNull(ttsModel, ttsVoice).joinToString(" · ")
                    .ifBlank { stringResource(R.string.voice_settings_not_configured) },
                onClick = { onOpenSettings(VoiceSettingsKind.Tts) },
                showDivider = true,
            )
            PreferenceListItem(
                icon = Icons.Default.FlashOn,
                title = stringResource(R.string.voice_settings_realtime_title),
                subtitle = realtimeModel ?: stringResource(R.string.voice_settings_not_configured),
                onClick = { onOpenSettings(VoiceSettingsKind.Realtime) },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * 语音设置二级页：每个可配置项一行（当前值 + ›），点行在同一抽屉内翻到对应选择页。
 * 通话进行中或前台 Agent 忙时只读；改动统一在下次通话生效，页尾给出提示。
 */
@Composable
private fun VoiceSettingsPage(
    kind: VoiceSettingsKind,
    settings: VoiceSettingsUiState,
    onOpenModelPicker: (VoiceSettingsKind) -> Unit,
    onOpenVoicePicker: (VoiceSettingsKind) -> Unit,
) {
    val writable = settings.writable
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PreferenceGroupCard(Modifier.padding(top = 8.dp)) {
            when (kind) {
                VoiceSettingsKind.Speech -> PreferenceListItem(
                    icon = Icons.Default.SmartToy,
                    title = stringResource(R.string.voice_settings_stt_model),
                    subtitle = settings.speechRows.selectionName(settings.speechSelection)
                        ?: stringResource(R.string.voice_settings_not_configured),
                    onClick = if (writable) ({ onOpenModelPicker(VoiceSettingsKind.Speech) }) else null,
                )
                VoiceSettingsKind.Tts -> {
                    PreferenceListItem(
                        icon = Icons.Default.SmartToy,
                        title = stringResource(R.string.voice_settings_tts_model),
                        subtitle = settings.ttsRows.selectionName(settings.ttsSelection)
                            ?: stringResource(R.string.voice_settings_not_configured),
                        onClick = if (writable) ({ onOpenModelPicker(VoiceSettingsKind.Tts) }) else null,
                        showDivider = true,
                    )
                    // 音色与 TTS 模型绑定：没有模型就没有可选音色，行随之置灰（与设置页同规则）。
                    val ttsModelSelected = settings.ttsRows.any { it.selection() == settings.ttsSelection }
                    PreferenceListItem(
                        icon = Icons.Default.GraphicEq,
                        title = stringResource(R.string.voice_settings_tts_voice),
                        subtitle = settings.ttsVoiceOptions.firstOrNull { it.id == settings.ttsVoiceId }?.name
                            ?: settings.ttsVoiceId.ifBlank { stringResource(R.string.voice_settings_default_voice) },
                        onClick = if (writable && ttsModelSelected) {
                            ({ onOpenVoicePicker(VoiceSettingsKind.Tts) })
                        } else {
                            null
                        },
                    )
                }
                VoiceSettingsKind.Realtime -> {
                    PreferenceListItem(
                        icon = Icons.Default.SmartToy,
                        title = stringResource(R.string.voice_settings_realtime_model),
                        subtitle = settings.realtimeRows.selectionName(settings.realtimeSelection)
                            ?: stringResource(R.string.voice_settings_not_configured),
                        onClick = if (writable) ({ onOpenModelPicker(VoiceSettingsKind.Realtime) }) else null,
                        showDivider = true,
                    )
                    // 音色与模型强绑定：未配置模型时置灰；模型已选但厂商既无缺省音色也不接受自定义时同样无可选。
                    val realtimeVoiceEnabled = settings.realtimeSelection != null &&
                        (settings.realtimeDefaultVoice.isNotBlank() || settings.realtimeCustomVoiceAllowed)
                    val effectiveRealtimeVoice = settings.realtimeVoiceId ?: settings.realtimeDefaultVoice
                    PreferenceListItem(
                        icon = Icons.Default.GraphicEq,
                        title = stringResource(R.string.voice_settings_realtime_voice),
                        subtitle = settings.realtimeVoiceOptions.firstOrNull { it.id == effectiveRealtimeVoice }?.name
                            ?: effectiveRealtimeVoice.ifBlank { stringResource(R.string.voice_settings_default_voice) },
                        onClick = if (writable && realtimeVoiceEnabled) {
                            ({ onOpenVoicePicker(VoiceSettingsKind.Realtime) })
                        } else {
                            null
                        },
                    )
                }
            }
        }
        // 提示与分组卡片内文字对齐（卡片外边距 16dp + 行内缩进 16dp），与「添加到聊天」抽屉的提示同位。
        Text(
            stringResource(R.string.voice_settings_effect_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )
    }
}

/**
 * 模式抽屉二级页：四个模式条目，各带一行详细说明；不可用的置灰并给出原因。
 * 标题只用短名，管线信息（级联或端到端 Realtime）由说明文案承载，避免长标题挤占行宽。
 * 点选即切换，抽屉不关闭，选中标记原地更新。
 * 语音开启后四个模式全部禁点并统一给出「先结束通话」提示——此时不再按配置可用性解释置灰原因，
 * 避免把「已配置 Realtime 模型」误说成「请先配置」。
 */
@Composable
private fun VoiceModeSheetOptions(
    current: RealtimeVoiceMode,
    chatAvailable: Boolean,
    realtimeAvailable: Boolean,
    interactionEnabled: Boolean,
    blockedByActiveCall: Boolean,
    onSelectMode: (RealtimeVoiceMode) -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PreferenceGroupCard(Modifier.padding(top = 8.dp)) {
            RealtimeVoiceMode.entries.forEachIndexed { index, mode ->
                val available = when (mode) {
                    RealtimeVoiceMode.Simple -> true
                    RealtimeVoiceMode.Chat -> chatAvailable
                    // 未配置 Realtime 默认模型时直接置灰；配置入口统一在齿轮 → 设置页。
                    RealtimeVoiceMode.Realtime, RealtimeVoiceMode.RealtimeAssistant -> realtimeAvailable
                }
                val detail = when (mode) {
                    RealtimeVoiceMode.Simple -> stringResource(R.string.voice_simple_mode_detail)
                    RealtimeVoiceMode.Chat -> stringResource(R.string.voice_chat_mode_detail)
                    RealtimeVoiceMode.Realtime -> stringResource(R.string.voice_realtime_mode_detail)
                    RealtimeVoiceMode.RealtimeAssistant -> stringResource(R.string.voice_realtime_assistant_mode_detail)
                }
                // 置灰条目把不可用原因接在说明文字之后，与权限模式抽屉的行样式一致。
                // 通话中统一由页尾提示解释，不再逐行复述配置类原因。
                val description = when {
                    blockedByActiveCall -> detail
                    available -> detail
                    else -> detail + "\n" + stringResource(
                        when (mode) {
                            RealtimeVoiceMode.Chat -> R.string.voice_chat_unavailable
                            else -> R.string.voice_realtime_unavailable
                        },
                    )
                }
                GimiSheetOptionRow(
                    selected = current == mode,
                    enabled = available && interactionEnabled,
                    label = stringResource(modeTitleRes(mode)),
                    description = description,
                    onClick = { onSelectMode(mode) },
                    icon = modeIcon(mode),
                )
                if (index < RealtimeVoiceMode.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                }
            }
        }
        // 与语音设置页的「下次通话生效」提示同位；解释四行同时置灰的原因。
        if (blockedByActiveCall) {
            Text(
                stringResource(R.string.voice_mode_switch_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 轻量圆弧加载：放慢旋转避免模式切换时显得急促，短文案不抢占字幕区域。 */
@Composable
private fun VoiceSwitchingIndicator(scene: VoiceSceneColors, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rotation by rememberInfiniteTransition(label = "voiceSwitching").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "voiceSwitchingRotation",
    )
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            drawArc(colors.primary.copy(alpha = .16f), 0f, 360f, useCenter = false, style = stroke)
            drawArc(colors.primary, rotation, 88f, useCenter = false, style = stroke)
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.voice_switch_hint), style = MaterialTheme.typography.bodyMedium,
            color = scene.inkDim)
    }
}

@Composable
private fun VoiceControl(
    icon: ImageVector,
    label: String,
    main: Boolean,
    end: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    /** 图标的无障碍描述；默认复用可见标签。 */
    contentDescription: String = label,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) .94f else 1f,
        animationSpec = spring(dampingRatio = .8f, stiffness = 600f),
        label = "Voice control press",
    )
    // 主钮颜色在品牌蓝与错误色之间平滑过渡，避免「开始通话 → 结束通话」切换时的硬切。
    val containerColor by animateColorAsState(
        targetValue = if (end) colors.error else colors.primary,
        animationSpec = tween(320),
        label = "Voice control container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (end) colors.onError else colors.onPrimary,
        animationSpec = tween(320),
        label = "Voice control content",
    )
    Column(Modifier.width(86.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 主钮是这一屏唯一的品牌蓝动作焦点；挂断属于破坏性操作，改用错误色并保留电话图标语义。
        // 次要钮保持中性描边，不与主钮争夺注意力。
        if (main) {
            // 待启动态给主钮一圈呼吸光晕：蓝色只落在这一个动作上，同时把这一屏唯一的主操作点亮。
            val pulse = !end && enabled
            val pulseTransition = rememberInfiniteTransition(label = "voiceMainPulse")
            val pulsePhase by pulseTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
                label = "voiceMainPulsePhase",
            )
            Box(contentAlignment = Alignment.Center) {
                if (pulse) {
                    Canvas(Modifier.size(76.dp)) {
                        // 光晕半径外扩、透明度同步归零，循环一轮；按钮本身不动。
                        val base = size.minDimension / 2f
                        drawCircle(
                            color = colors.primary.copy(alpha = .26f * (1f - pulsePhase)),
                            radius = base + base * .45f * pulsePhase,
                        )
                    }
                }
                FilledIconButton(
                    onClick = onClick,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    modifier = Modifier.size(76.dp).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                ) {
                    Crossfade(targetState = icon, animationSpec = tween(180), label = "Voice control icon") { currentIcon ->
                        Icon(currentIcon, contentDescription, Modifier.size(30.dp))
                    }
                }
            }
        } else {
            OutlinedIconButton(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                modifier = Modifier.size(54.dp).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurfaceVariant,
                ),
                border = BorderStroke(1.5.dp, colors.outline),
            ) {
                Crossfade(targetState = icon, animationSpec = tween(180), label = "Voice control icon") { currentIcon ->
                    Icon(currentIcon, contentDescription, Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Crossfade(targetState = label, animationSpec = tween(180), label = "Voice control label") { currentLabel ->
            Text(currentLabel, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── 抽屉页面预览 ─────────────────────────────────────────────────

/** 抽屉页面预览共用的候选模型行假数据。 */
private fun previewVoiceModelRow(
    serviceId: String,
    serviceName: String,
    groupId: String,
    groupName: String,
    modelId: String,
    modelName: String,
): VoiceModelRow = VoiceModelRow(
    service = LLMModelSetting(
        id = serviceId,
        name = serviceName,
        isEnabled = true,
        apiKey = "preview-key",
        apiBaseUrl = "https://preview.example.com",
        apiProtocol = ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = "",
        groups = emptyList(),
    ),
    group = ModelGroup(id = groupId, name = groupName, models = emptyList()),
    model = Model(id = modelId, name = modelName),
)

/** 抽屉页面预览共用的一套已配置默认模型与音色。 */
private fun previewVoiceSettings(): VoiceSettingsUiState = VoiceSettingsUiState(
    speechRows = listOf(previewVoiceModelRow("minimax", "MiniMax", "audio", "Audio", "speech-02", "Speech-02")),
    speechSelection = ModelSelection("minimax", "audio", "speech-02"),
    ttsRows = listOf(previewVoiceModelRow("mimo", "MiMo", "audio", "Audio", "mimo-tts", "MiMo TTS")),
    ttsSelection = ModelSelection("mimo", "audio", "mimo-tts"),
    ttsVoiceId = "tongtong",
    ttsVoiceOptions = listOf(TtsVoice(id = "tongtong", name = "童童", language = "zh", gender = "Female")),
    realtimeRows = listOf(
        previewVoiceModelRow("qwen", "千问", "realtime", "Realtime", "qwen3-omni-flash-realtime", "qwen3-omni-flash-realtime"),
    ),
    realtimeSelection = ModelSelection("qwen", "realtime", "qwen3-omni-flash-realtime"),
    realtimeDefaultVoice = "Cherry",
    realtimeVoiceOptions = listOf(TtsVoice(id = "Cherry", name = "Cherry", language = "zh", gender = "Female")),
    writable = true,
)

/** 抽屉页面预览共用外壳：灰画布 + 紧凑行样式 + 标题行，模拟抽屉内的观感。 */
@Composable
private fun VoiceSheetPagePreview(
    title: String,
    showBack: Boolean,
    content: @Composable () -> Unit,
) {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            PreferenceStyleScope(PreferenceStyle.Compact) {
                Column(Modifier.fillMaxWidth()) {
                    GimiSheetTitleRow(
                        title = title,
                        navigationIcon = if (showBack) Icons.AutoMirrored.Filled.ArrowBack else null,
                        navigationContentDescription = if (showBack) "返回" else null,
                        onNavigationClick = if (showBack) ({ }) else null,
                    )
                    content()
                }
            }
        }
    }
}

@Preview(name = "抽屉 · 更多选项 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · 更多选项 · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSheetEntryPagePreview() {
    VoiceSheetPagePreview(title = "更多选项", showBack = false) {
        VoiceSheetEntryPage(
            settings = previewVoiceSettings(),
            currentModeTitle = "轻聊",
            modesEnabled = true,
            onOpenModes = {},
            onOpenPresets = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "抽屉 · 选择对话模式 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · 选择对话模式 · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSheetModesPagePreview() {
    VoiceSheetPagePreview(title = "选择对话模式", showBack = true) {
        VoiceModeSheetOptions(
            current = RealtimeVoiceMode.Chat,
            chatAvailable = true,
            realtimeAvailable = false,
            interactionEnabled = true,
            blockedByActiveCall = false,
            onSelectMode = {},
        )
    }
}

/** 已配置 Realtime 默认模型：待启动态下实时与实时助手都应可选。 */
@Preview(name = "抽屉 · 选择对话模式 · 已配置 Realtime · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · 选择对话模式 · 已配置 Realtime · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSheetModesPageRealtimeConfiguredPreview() {
    VoiceSheetPagePreview(title = "选择对话模式", showBack = true) {
        VoiceModeSheetOptions(
            current = RealtimeVoiceMode.Simple,
            chatAvailable = true,
            realtimeAvailable = true,
            interactionEnabled = true,
            blockedByActiveCall = false,
            onSelectMode = {},
        )
    }
}

/** 通话中：四个模式全部禁点，只给一条「先结束通话」提示。 */
@Preview(name = "抽屉 · 选择对话模式 · 通话中 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · 选择对话模式 · 通话中 · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSheetModesPageBlockedPreview() {
    VoiceSheetPagePreview(title = "选择对话模式", showBack = true) {
        VoiceModeSheetOptions(
            current = RealtimeVoiceMode.Realtime,
            chatAvailable = true,
            realtimeAvailable = true,
            interactionEnabled = false,
            blockedByActiveCall = true,
            onSelectMode = {},
        )
    }
}

@Preview(name = "抽屉 · 语音合成设置 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · 语音合成设置 · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSheetTtsPagePreview() {
    VoiceSheetPagePreview(title = "语音合成设置", showBack = true) {
        VoiceSettingsPage(
            kind = VoiceSettingsKind.Tts,
            settings = previewVoiceSettings(),
            onOpenModelPicker = {},
            onOpenVoicePicker = {},
        )
    }
}

@Preview(name = "抽屉 · Realtime 语音设置 · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · Realtime 语音设置 · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSheetRealtimePagePreview() {
    VoiceSheetPagePreview(title = "Realtime 语音设置", showBack = true) {
        VoiceSettingsPage(
            kind = VoiceSettingsKind.Realtime,
            settings = previewVoiceSettings(),
            onOpenModelPicker = {},
            onOpenVoicePicker = {},
        )
    }
}
