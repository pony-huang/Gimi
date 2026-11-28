package github.ponyhuang.gimi.feature.chat.realtime

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigField
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.components.GimiSheetBase
import github.ponyhuang.gimi.ui.components.GimiSheetOptionRow
import github.ponyhuang.gimi.ui.components.LocalGimiSheetBodyMaxHeight
import github.ponyhuang.gimi.ui.components.GimiSheetTitleRow
import github.ponyhuang.gimi.ui.components.SheetHeight
import github.ponyhuang.gimi.ui.components.VoicePickerPage
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.GimiTheme
import kotlin.math.roundToInt

/**
 * 语音抽屉的层级：一级「更多选项」入口 → 二级语音设置 → 三级模型 / 音色选择；角色管理共用 Chatbot 入口。
 *
 * 全部页面共用同一个 `GimiSheetBase`，只靠 `AnimatedContent` 翻页，不再叠加第二个模态抽屉。
 * [depth] 决定左右滑动方向；[parent] 让标题返回箭头与系统返回键走同一条回退路径。
 * [hasOwnTitle] 标记页面主体自带标题行，外壳只为 false 的页面绘制 header 标题。
 * 不能用 depth 推断：快捷入口的音色选择层级是 1，却复用了自带标题行的 [VoicePickerPage]。
 */
private sealed interface VoiceSheetPage {
    val depth: Int
    val parent: VoiceSheetPage
    val hasOwnTitle: Boolean

    object Entry : VoiceSheetPage {
        override val depth = 0
        override val parent get() = Entry
        override val hasOwnTitle = false
    }

    /** 语音设置页：链路切换 + 当前链路的模型与音色。 */
    object Settings : VoiceSheetPage {
        override val depth = 1
        override val parent get() = Entry
        override val hasOwnTitle = false
    }

    data class ModelPicker(val field: VoiceConfigField) : VoiceSheetPage {
        override val depth = 2
        override val parent get() = Settings
        override val hasOwnTitle = true
    }

    /** 音色选择页；[quick] 为 true 时来自更多选项快捷入口，保存不经草稿。 */
    data class VoicePicker(val pipeline: VoicePipeline, val quick: Boolean) : VoiceSheetPage {
        override val depth = if (quick) 1 else 2
        override val parent get() = if (quick) Entry else Settings
        override val hasOwnTitle = true
    }
}

/** 标题与主体共用同一份翻页规格，左右滑动才会同步：进入下一级左滑、返回右滑。 */
private val voiceSheetPageTransition:
    AnimatedContentTransitionScope<VoiceSheetPage>.() -> ContentTransform = {
        val towards = if (targetState.depth > initialState.depth) {
            AnimatedContentTransitionScope.SlideDirection.Left
        } else {
            AnimatedContentTransitionScope.SlideDirection.Right
        }
        slideIntoContainer(towards = towards, animationSpec = tween(220)) + fadeIn(tween(160)) togetherWith
            slideOutOfContainer(towards = towards, animationSpec = tween(220)) + fadeOut(tween(120))
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

/** 语音处理链路显示名。 */
@StringRes
private fun pipelineTitleRes(pipeline: VoicePipeline): Int = when (pipeline) {
    VoicePipeline.CASCADED -> R.string.voice_pipeline_cascaded
    VoicePipeline.SEMI_CASCADED -> R.string.voice_pipeline_semi_cascaded
    VoicePipeline.NATIVE_REALTIME -> R.string.voice_pipeline_native_realtime
}

/** 链路语义图标，与抽屉里其它菜单行共用图标槽位。 */
private fun pipelineIcon(pipeline: VoicePipeline): ImageVector = when (pipeline) {
    VoicePipeline.CASCADED -> Icons.AutoMirrored.Filled.Chat
    VoicePipeline.SEMI_CASCADED -> Icons.Default.GraphicEq
    VoicePipeline.NATIVE_REALTIME -> Icons.Default.Tune
}

/** M3 Emphasized decelerate 缓动，用于聊天 ↔ 语音页的空间过渡。 */
private val EmphasizedDecelerate = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * 实时语音展示页：整屏主题画布（浅色白→浅灰、深色黑→深灰），墨色粒子球居中承载状态文字。
 * 顶栏与聊天页几何一致：左（抽屉）+ 中间模式分段 + 右（新建会话）；
 * 底部 M3 悬浮工具栏：键盘输入、语音设置、中央通话主按钮、静音、关闭回聊天。
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
    // 右胶囊「回聊天」：结束通话并返回文字聊天；后台续跑只保留给系统后台切换。
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenVoiceSettings: () -> Unit = {},
    onCloseVoiceSettings: () -> Unit = {},
    onResetVoiceSettings: () -> Unit = {},
    roleChips: (@Composable () -> Unit)? = null,
    onSelectPipeline: (VoicePipeline) -> Unit = {},
    onSelectFieldModel: (VoiceConfigField, ModelSelection?) -> Unit = { _, _ -> },
    onSelectVoice: (VoicePipeline, String?) -> Unit = { _, _ -> },
    onEditInput: () -> Unit = {},
    onOpenDocument: (FileAttachment) -> Unit = {},
    /** 页面切换过渡进度（0f = 文字聊天页，1f = 实时语音页），驱动背景、粒子球与底部输入坞分层动效。 */
    transitionProgress: Float = 1f,
    /** 页面是否可见或处于过渡中；隐藏时暂停粒子球无限帧动画。 */
    isVisible: Boolean = true,
    composerContent: @Composable () -> Unit = {},
) {
    val state = uiState.voice
    val colors = MaterialTheme.colorScheme
    val failed = started && state.phase == RealtimeVoicePhase.Failed
    val savingFailed = state.failure == RealtimeVoiceFailure.Persistence
    val starting = state.phase == RealtimeVoicePhase.Idle || state.phase == RealtimeVoicePhase.Closed
    var modeSheetOpen by remember { mutableStateOf(false) }
    // 抽屉层级由单一状态表达：点行进入下一页、点返回或选完退回上一页，全程只有一个抽屉。
    var sheetPage by remember { mutableStateOf<VoiceSheetPage>(VoiceSheetPage.Entry) }
    // 搜索态提升到宿主：返回键要先收搜索再逐级返回，两个选择页共用同一份开关。
    var sheetSearching by remember { mutableStateOf(false) }
    val closeVoiceSettings = {
        modeSheetOpen = false
        sheetSearching = false
        onCloseVoiceSettings()
    }
    DisposableEffect(Unit) {
        onDispose { if (modeSheetOpen) onCloseVoiceSettings() }
    }
    LaunchedEffect(uiState.settingsLocked, isVisible) {
        if (modeSheetOpen && (uiState.settingsLocked || !isVisible)) closeVoiceSettings()
    }
    val navigateSheetBack = {
        if (sheetPage == VoiceSheetPage.Settings) {
            // 返回一级时放下未补齐的链路；快捷音色入口继续读取已保存配置。
            onCloseVoiceSettings()
        }
        sheetPage = sheetPage.parent
    }
    // 翻页即收起搜索态：搜索是选择页的临时模式，不跨页残留。
    LaunchedEffect(sheetPage) { sheetSearching = false }
    val status = when {
        uiState.ending -> R.string.voice_ending
        uiState.settingsSaving -> R.string.voice_settings_saving
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
    val scene = voiceScene(colors)
    val draft = uiState.draft
    // M3 Emphasized decelerate 曲线：进入时前段快、尾段柔和落位；顶栏几何与聊天页一致，保持静止。
    val clampedProgress = EmphasizedDecelerate.transform(transitionProgress.coerceIn(0f, 1f))
    val maxOrbCap by animateDpAsState(
        targetValue = if (uiState.composer.editing) 180.dp else 340.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "voice-orb-cap",
    )
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = clampedProgress }
            .voiceSceneBackground(scene),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val dockProgress = ((clampedProgress - 0.08f) / 0.92f).coerceIn(0f, 1f)
                        alpha = dockProgress
                        val scale = 0.92f + 0.08f * dockProgress
                        scaleX = scale
                        scaleY = scale
                        translationY = (1f - dockProgress) * 28.dp.toPx()
                    }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .imePadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = if (uiState.composer.editing) 12.dp else 30.dp),
            ) {
                VoiceInputDock(
                    state = uiState,
                    onSettings = {
                        sheetPage = VoiceSheetPage.Entry
                        modeSheetOpen = true
                    },
                    onChat = onEditInput,
                    onCall = if (started) onEnd else onStartCall,
                    onMute = onToggleMute,
                    onBack = onBack,
                    composerContent = composerContent,
                    settingsEnabled = !uiState.settingsLocked && !started && !uiState.switching,
                )
                VoiceConfigMicroPills(
                    uiState = uiState,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(horizontal = 16.dp)) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.conversationTitle.isNotBlank()) {
                    Surface(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        shape = CircleShape,
                        color = scene.capsuleFill,
                    ) {
                        Text(
                            text = uiState.conversationTitle,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = scene.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // 球体略低于上半区中心，同时保留下方约 43% 空间给字幕与错误恢复内容；
            // 状态文字按参考视频样式移入球心，由 VoiceOrb 自行渲染。
            Column(
                Modifier
                    .weight(1.2f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val orbScale = 0.76f + 0.24f * clampedProgress
                        scaleX = orbScale
                        scaleY = orbScale
                        translationY = (1f - clampedProgress) * 22.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val orbSize = minOf(maxWidth, maxHeight, maxOrbCap)
                    VoiceOrb(
                        state = state,
                        switching = uiState.switching,
                        statusText = stringResource(status),
                        modifier = Modifier.size(orbSize).offset(y = 16.dp),
                        ink = scene.ink,
                        isVisible = isVisible,
                    )
                }
            }
            Box(Modifier.weight(.9f).fillMaxWidth()) {
                when {
                    failed -> Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(onClick = onRetry) {
                            Text(stringResource(if (savingFailed) R.string.voice_retry_save else R.string.voice_retry))
                        }
                    }
                    uiState.switching -> VoiceSwitchingIndicator(scene, Modifier.padding(top = 20.dp))
                    else -> VoiceCaptions(
                        inputText = state.inputText,
                        inputAttachments = state.inputAttachments,
                        onOpenDocument = onOpenDocument,
                        responseText = state.responseText,
                        spokenText = state.spokenText,
                        speakingText = state.speakingText,
                        revision = state.revision,
                        responseColor = scene.caption,
                        responseDimColor = scene.captionDim,
                        fadeColor = scene.fade,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (roleChips != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .graphicsLayer {
                            val dockProgress = ((clampedProgress - 0.08f) / 0.92f).coerceIn(0f, 1f)
                            alpha = dockProgress
                            translationY = (1f - dockProgress) * 28.dp.toPx()
                        },
                ) {
                    roleChips()
                }
            }
        }

    }
    val settings = uiState.settings
    if (modeSheetOpen && !uiState.settingsLocked && !started) {
        // 选择页逐级返回；收起整个面板后直接回到语音页。
        BackHandler(enabled = sheetPage != VoiceSheetPage.Entry, onBack = navigateSheetBack)
        PreferenceStyleScope(PreferenceStyle.Compact) {
            GimiSheetBase(
                onDismissRequest = closeVoiceSettings,
                initialHeight = SheetHeight.Fraction(0.5f),
                maxHeight = SheetHeight.Fraction(0.9f),
                // 一级入口贴内容长；二级设置页与三级选择页放开到上限：语音设置的链路、
                // 模型/音色行与应用按钮加起来超过半屏，压在半屏会把底部裁在可视区之外。
                height = if (sheetPage.depth == 0) {
                    SheetHeight.Fraction(0.5f)
                } else {
                    SheetHeight.Fraction(0.9f)
                },
                header = {
                    // 外壳标题固定在主体上方、不随内容滚动；选择页自带标题行，外壳不再绘制，
                    // 否则快捷入口的音色选择（层级 1）会叠出两个标题与两个返回箭头。
                    AnimatedContent(
                        targetState = sheetPage,
                        transitionSpec = voiceSheetPageTransition,
                        label = "voice-sheet-title",
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        if (!page.hasOwnTitle) {
                            GimiSheetTitleRow(
                                title = stringResource(page.titleRes()),
                                navigationIcon = if (page == VoiceSheetPage.Entry) {
                                    null
                                } else {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                },
                                navigationContentDescription = if (page == VoiceSheetPage.Entry) {
                                    null
                                } else {
                                    stringResource(R.string.voice_sheet_back)
                                },
                                onNavigationClick = if (page == VoiceSheetPage.Entry) {
                                    null
                                } else {
                                    navigateSheetBack
                                },
                            )
                        }
                    }
                },
            ) {
                // 系统返回先收搜索，再走逐级返回；注册在抽屉内容里，优先于抽屉自身的返回关闭。
                BackHandler(enabled = sheetSearching) { sheetSearching = false }
                // 标题与主体共用同一份翻页规格，左右滑动才会同步。
                AnimatedContent(
                    targetState = sheetPage,
                    transitionSpec = voiceSheetPageTransition,
                    label = "voice-sheet-page",
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    Column(Modifier.fillMaxWidth()) {
                        when (page) {
                            is VoiceSheetPage.ModelPicker -> {
                                val field = page.field
                                VoiceModelPickerPage(
                                    title = stringResource(field.pickerTitleRes()),
                                    rows = when (field) {
                                        VoiceConfigField.CASCADED_RECOGNITION_MODEL -> settings.recognitionRows
                                        VoiceConfigField.CASCADED_SYNTHESIS_MODEL, VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL -> settings.synthesisRows
                                        VoiceConfigField.SEMI_CASCADED_UNDERSTANDING_MODEL -> settings.understandingRows
                                        else -> settings.realtimeRows
                                    },
                                    currentSelection = draftFieldValue(draft, field) as ModelSelection?,
                                    isSearching = sheetSearching,
                                    onSearchingChange = { sheetSearching = it },
                                    onPick = { selection ->
                                        onSelectFieldModel(field, selection)
                                        sheetPage = page.parent
                                    },
                                    onBack = { sheetPage = page.parent },
                                    onClose = closeVoiceSettings,
                                )
                            }
                            is VoiceSheetPage.VoicePicker -> {
                                val pipeline = page.pipeline
                                if (pipeline == VoicePipeline.NATIVE_REALTIME) {
                                    VoicePickerPage(
                                        title = stringResource(R.string.voice_settings_pick_realtime_voice),
                                        currentVoiceId = draft.realtimeVoice.value ?: settings.realtimeDefaultVoice,
                                        options = settings.realtimeVoiceOptions,
                                        isLoading = false,
                                        isSearching = sheetSearching,
                                        onSearchingChange = { sheetSearching = it },
                                        // 清空输入等于回到该模型的缺省音色，因此空串要落成 null 而不是一个空覆盖值。
                                        onPick = { voiceId ->
                                            val picked = voiceId.ifBlank { null }
                                            onSelectVoice(pipeline, picked)
                                            sheetPage = page.parent
                                        },
                                        onBack = { sheetPage = page.parent },
                                        onClose = closeVoiceSettings,
                                    )
                                } else {
                                    VoicePickerPage(
                                        title = stringResource(R.string.voice_settings_pick_tts_voice),
                                        currentVoiceId = when (pipeline) {
                                            VoicePipeline.SEMI_CASCADED -> draft.semiVoice.value.orEmpty()
                                            else -> draft.cascadedVoice.value.orEmpty()
                                        },
                                        options = settings.synthesisVoiceOptions,
                                        isLoading = settings.isSynthesisVoiceLoading,
                                        isSearching = sheetSearching,
                                        onSearchingChange = { sheetSearching = it },
                                        onPick = { voiceId ->
                                            val picked = voiceId.ifBlank { null }
                                            onSelectVoice(pipeline, picked)
                                            sheetPage = page.parent
                                        },
                                        onBack = { sheetPage = page.parent },
                                        onClose = closeVoiceSettings,
                                    )
                                }
                            }
                            else -> {
                                // 一、二级页的标题已固定在 header；主体统一提供有界滚动容器。
                                // 滚动容器拿不到明确上限时 viewport 会和内容一样高：既滚不动，
                                // 超出的设置项又被抽屉主体裁掉。上限取 GimiSheetBase 实测后
                                // 扣掉外壳的高度；短内容仍贴自身高度长，不会被拉到满屏。
                                val bodyMaxHeight = LocalGimiSheetBodyMaxHeight.current
                                Column(
                                    Modifier.fillMaxWidth()
                                        .then(
                                            if (bodyMaxHeight != Dp.Unspecified && bodyMaxHeight > 0.dp) {
                                                Modifier.heightIn(max = bodyMaxHeight)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    when (page) {
                                        VoiceSheetPage.Entry -> VoiceSheetEntryPage(
                                            uiState = uiState,
                                            onOpenQuickVoice = {
                                                if (!uiState.settingsSaving) {
                                                    onOpenVoiceSettings()
                                                    sheetPage = VoiceSheetPage.VoicePicker(uiState.pipeline, quick = true)
                                                }
                                            },
                                            onOpenSettings = {
                                                if (!uiState.settingsSaving) {
                                                    onOpenVoiceSettings()
                                                    sheetPage = VoiceSheetPage.Settings
                                                }
                                            },
                                        )
                                        VoiceSheetPage.Settings -> VoiceSettingsPage(
                                            uiState = uiState,
                                            onOpenModelPicker = { field -> sheetPage = VoiceSheetPage.ModelPicker(field) },
                                            onOpenVoicePicker = { pipeline -> sheetPage = VoiceSheetPage.VoicePicker(pipeline, quick = false) },
                                            onSelectPipeline = onSelectPipeline,
                                            onResetVoiceSettings = onResetVoiceSettings,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 音色选择保存目标由调用点决定：快捷入口立即提交，设置页写入草稿。 */

/** 草稿字段的模型值；用于模型选择页的当前选中判断。 */
private fun draftFieldValue(draft: VoiceDraftUiState, field: VoiceConfigField): Any? = when (field) {
    VoiceConfigField.CASCADED_RECOGNITION_MODEL -> draft.cascadedRecognitionModel.value
    VoiceConfigField.CASCADED_SYNTHESIS_MODEL -> draft.cascadedSynthesisModel.value
    VoiceConfigField.SEMI_CASCADED_UNDERSTANDING_MODEL -> draft.semiUnderstandingModel.value
    VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL -> draft.semiSynthesisModel.value
    VoiceConfigField.REALTIME_MODEL -> draft.realtimeModel.value
    else -> null
}

/** 外壳 header 的标题；选择页标题由页面自身给出，这里仅保留穷尽分支。 */
@StringRes
private fun VoiceSheetPage.titleRes(): Int = when (this) {
    VoiceSheetPage.Entry -> R.string.voice_more_options
    VoiceSheetPage.Settings -> R.string.voice_settings_page
    is VoiceSheetPage.ModelPicker -> field.pickerTitleRes()
    is VoiceSheetPage.VoicePicker -> when (pipeline) {
        VoicePipeline.NATIVE_REALTIME -> R.string.voice_settings_pick_realtime_voice
        else -> R.string.voice_settings_pick_tts_voice
    }
}

/** 模型选择页标题按配置字段给出。 */
@StringRes
private fun VoiceConfigField.pickerTitleRes(): Int = when (this) {
    VoiceConfigField.CASCADED_RECOGNITION_MODEL -> R.string.voice_settings_pick_recognition
    VoiceConfigField.CASCADED_SYNTHESIS_MODEL -> R.string.voice_settings_pick_synthesis
    VoiceConfigField.SEMI_CASCADED_UNDERSTANDING_MODEL -> R.string.voice_settings_pick_understanding
    VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL -> R.string.voice_settings_pick_synthesis
    VoiceConfigField.REALTIME_MODEL -> R.string.voice_settings_pick_realtime_model
    else -> R.string.voice_settings_page
}

/**
 * 抽屉一级页：音色快捷入口、语音设置与 Chatbot 角色预设，各带当前值副标题；
 * 草稿有未应用修改时提供应用／放弃。条目与聊天界面抽屉同一套 One UI 语言：
 * 灰画布上的白色圆角分组卡片 + 内缩分隔线。
 */
@Composable
private fun VoiceSheetEntryPage(
    uiState: RealtimeVoiceUiState,
    onOpenQuickVoice: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings = uiState.settings
    val draft = uiState.draft
    val pipeline = draft.pipeline.value
    val voiceField = when (pipeline) {
        VoicePipeline.CASCADED -> draft.cascadedVoice
        VoicePipeline.SEMI_CASCADED -> draft.semiVoice
        VoicePipeline.NATIVE_REALTIME -> draft.realtimeVoice
    }
    val voiceOptions = if (pipeline == VoicePipeline.NATIVE_REALTIME) {
        settings.realtimeVoiceOptions
    } else {
        settings.synthesisVoiceOptions
    }
    val voiceName = voiceField.value?.let { id ->
        voiceOptions.firstOrNull { it.id == id }?.name ?: id
    } ?: stringResource(R.string.voice_settings_default_voice)
    val modelName = if (pipeline == VoicePipeline.NATIVE_REALTIME) {
        settings.realtimeRows.selectionRow(draft.realtimeModel.value)?.model?.name.orEmpty()
    } else {
        uiState.chatModelName
    }.ifBlank { stringResource(R.string.voice_settings_not_configured) }
    val settingsSubtitle = stringResource(pipelineTitleRes(pipeline)) + " · " + modelName
    // 滚动由抽屉骨架统一提供（有界 viewport），页面本身只排内容。
    Column(Modifier.fillMaxWidth()) {
        PreferenceGroupCard(Modifier.padding(top = 8.dp)) {
            PreferenceListItem(
                icon = Icons.Default.GraphicEq,
                title = stringResource(R.string.voice_voice_entry),
                subtitle = if (voiceField.substituted) {
                    stringResource(R.string.voice_voice_substituted, voiceName)
                } else {
                    voiceName
                },
                onClick = onOpenQuickVoice,
                showDivider = true,
            )
            PreferenceListItem(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.voice_settings_entry),
                subtitle = settingsSubtitle,
                onClick = onOpenSettings,
            )
        }
        Text(
            stringResource(
                if (draft.hasAnyOverride) R.string.voice_scope_conversation_hint
                else R.string.voice_scope_global_hint
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * 语音设置页：链路三段选择 + 当前链路的模型与音色行，展示继承/覆盖状态，
 * 有效配置会自动保存；缺少必需模型时在对应行给出补齐入口。
 */
@Composable
private fun VoiceSettingsPage(
    uiState: RealtimeVoiceUiState,
    onOpenModelPicker: (VoiceConfigField) -> Unit,
    onOpenVoicePicker: (VoicePipeline) -> Unit,
    onSelectPipeline: (VoicePipeline) -> Unit,
    onResetVoiceSettings: () -> Unit,
) {
    val settings = uiState.settings
    val draft = uiState.draft
    val pipeline = draft.pipeline.value
    // 滚动由抽屉骨架统一提供（有界 viewport），页面本身只排内容。
    Column(Modifier.fillMaxWidth()) {
        PreferenceGroupCard(Modifier.padding(top = 8.dp)) {
            VoicePipelineSegmentedRow(
                current = pipeline,
                semiCascadedSupported = uiState.semiCascadedSupported,
                onSelectPipeline = onSelectPipeline,
            )
        }
        // 描述、配置行与半级联注释随链路整块交叉淡入淡出；行数差异（3 行 ↔ 2 行）
        // 由 SizeTransform 的高度动画吸收，避免切档时下方内容硬切跳变。
        AnimatedContent(
            targetState = pipeline,
            transitionSpec = {
                fadeIn(tween(durationMillis = 220)) togetherWith fadeOut(tween(durationMillis = 140)) using
                    SizeTransform { _, _ -> tween(durationMillis = 220, easing = FastOutSlowInEasing) }
            },
            label = "voicePipelineSettings",
        ) { target ->
            Column(Modifier.fillMaxWidth()) {
                val unavailableReason = when {
                    target == VoicePipeline.SEMI_CASCADED && !uiState.semiCascadedSupported ->
                        stringResource(R.string.voice_pipeline_semi_requires_omni)
                    target == pipeline && draft.missing.isNotEmpty() -> stringResource(R.string.voice_settings_missing_models)
                    else -> null
                }
                Text(
                    unavailableReason ?: stringResource(
                        when (target) {
                            VoicePipeline.CASCADED -> R.string.voice_pipeline_cascaded_detail
                            VoicePipeline.SEMI_CASCADED -> R.string.voice_pipeline_semi_detail
                            VoicePipeline.NATIVE_REALTIME -> R.string.voice_pipeline_native_detail
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unavailableReason == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
                PreferenceGroupCard {
                    if (target != VoicePipeline.NATIVE_REALTIME) {
                        PreferenceListItem(
                            title = stringResource(
                                if (target == VoicePipeline.CASCADED) R.string.voice_settings_chat_model
                                else R.string.voice_settings_understanding_model,
                            ),
                            subtitle = uiState.chatModelName.ifBlank {
                                stringResource(R.string.voice_settings_not_configured)
                            },
                            leadingIcon = { iconModifier ->
                                LLMModelServiceIcon(
                                    serviceId = uiState.chatModelTemplateId,
                                    modifier = iconModifier,
                                    contentPadding = 2.dp,
                                )
                            },
                            showDivider = true,
                        )
                    }
                    when (target) {
                        VoicePipeline.CASCADED -> {
                            VoiceConfigModelRow(
                                titleRes = R.string.voice_settings_stt_model,
                                field = draft.cascadedRecognitionModel,
                                missing = VoiceConfigField.CASCADED_RECOGNITION_MODEL in draft.missing,
                                rows = settings.recognitionRows,
                                onClick = { onOpenModelPicker(VoiceConfigField.CASCADED_RECOGNITION_MODEL) },
                            )
                            VoiceConfigModelRow(
                                titleRes = R.string.voice_settings_tts_model,
                                field = draft.cascadedSynthesisModel,
                                missing = VoiceConfigField.CASCADED_SYNTHESIS_MODEL in draft.missing,
                                rows = settings.synthesisRows,
                                onClick = { onOpenModelPicker(VoiceConfigField.CASCADED_SYNTHESIS_MODEL) },
                                showDivider = true,
                            )
                            VoiceConfigVoiceRow(
                                titleRes = R.string.voice_settings_tts_voice,
                                field = draft.cascadedVoice,
                                options = settings.synthesisVoiceOptions,
                                onClick = { onOpenVoicePicker(VoicePipeline.CASCADED) },
                            )
                        }
                        VoicePipeline.SEMI_CASCADED -> {
                            VoiceConfigModelRow(
                                titleRes = R.string.voice_settings_tts_model,
                                field = draft.semiSynthesisModel,
                                missing = VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL in draft.missing,
                                rows = settings.synthesisRows,
                                onClick = { onOpenModelPicker(VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL) },
                            )
                            VoiceConfigVoiceRow(
                                titleRes = R.string.voice_settings_tts_voice,
                                field = draft.semiVoice,
                                options = settings.synthesisVoiceOptions,
                                onClick = { onOpenVoicePicker(VoicePipeline.SEMI_CASCADED) },
                            )
                        }
                        VoicePipeline.NATIVE_REALTIME -> {
                            VoiceConfigModelRow(
                                titleRes = R.string.voice_settings_realtime_model,
                                field = draft.realtimeModel,
                                missing = VoiceConfigField.REALTIME_MODEL in draft.missing,
                                rows = settings.realtimeRows,
                                onClick = { onOpenModelPicker(VoiceConfigField.REALTIME_MODEL) },
                                showDivider = true,
                            )
                            VoiceConfigVoiceRow(
                                titleRes = R.string.voice_settings_realtime_voice,
                                field = draft.realtimeVoice,
                                options = settings.realtimeVoiceOptions,
                                defaultVoiceId = settings.realtimeDefaultVoice,
                                onClick = { onOpenVoicePicker(VoicePipeline.NATIVE_REALTIME) },
                            )
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.voice_settings_scope_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )
        TextButton(
            onClick = onResetVoiceSettings,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.voice_reset_defaults))
        }
        Spacer(Modifier.height(12.dp))
    }
}


/** 链路分段控件的高度和圆角。 */
private val VoicePipelineSwitchHeight = 44.dp
private val VoicePipelineTrackCornerRadius = 24.dp
private val VoicePipelineThumbCornerRadius = 20.dp

/**
 * 链路三段选择：级联／半级联／实时。
 *
 * 允许选择缺少可配置模型的链路，再在该链路内补齐。半级联依赖当前聊天模型支持全模态输入。
 */
@Composable
private fun VoicePipelineSegmentedRow(
    current: VoicePipeline,
    semiCascadedSupported: Boolean,
    onSelectPipeline: (VoicePipeline) -> Unit,
) {
    val selectedIndex = VoicePipeline.entries.indexOf(current).coerceIn(0, VoicePipeline.entries.lastIndex)
    // 滑块位置以档位分数表达，measure 后按段宽换算实际 x；offset lambda 内读状态只触发重放置。
    val slide by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "voicePipelineThumbSlide",
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(VoicePipelineSwitchHeight)
            .clip(RoundedCornerShape(VoicePipelineTrackCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        val segment = maxWidth / VoicePipeline.entries.size
        Box(
            Modifier
                .offset { IntOffset((slide * segment.toPx()).roundToInt(), 0) }
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(VoicePipelineThumbCornerRadius))
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(Modifier.fillMaxSize()) {
            VoicePipeline.entries.forEach { pipeline ->
                val selected = pipeline == current
                val available = pipeline != VoicePipeline.SEMI_CASCADED || semiCascadedSupported
                val contentColor by animateColorAsState(
                    targetValue = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        available -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "voicePipelineLabel",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(VoicePipelineThumbCornerRadius))
                        // 语义化分段：TalkBack 播报选中态与 Tab 角色，与 ChatModeSwitch 同约定。
                        .selectable(
                            selected = selected,
                            enabled = available,
                            role = Role.Tab,
                            onClick = { onSelectPipeline(pipeline) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(pipelineTitleRes(pipeline)),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** 配置行共用的副标题：值名 + 继承/覆盖/未应用标记 + 缺项提示。 */
@Composable
private fun voiceFieldSubtitle(
    name: String?,
    overridden: Boolean,
    dirty: Boolean,
): String {
    val value = name ?: stringResource(R.string.voice_settings_not_configured)
    val markers = buildList {
        when {
            dirty -> add(stringResource(R.string.voice_settings_unsaved))
            overridden -> add(stringResource(R.string.voice_field_overridden))
            else -> add(stringResource(R.string.voice_field_inherited))
        }
    }
    return listOf(value, *markers.toTypedArray()).joinToString(" · ")
}

/**
 * 链路模型配置行：当前模型名 + 继承状态；缺项时在副标题标注。
 * 图标取当前选中模型所属厂商的品牌图标，未配置或所选模型已不在候选目录时
 * 由 [LLMModelServiceIcon] 回落内置占位图。
 */
@Composable
private fun VoiceConfigModelRow(
    @StringRes titleRes: Int,
    field: VoiceDraftFieldUi<ModelSelection?>,
    rows: List<VoiceModelRow>,
    missing: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = false,
) {
    val selectedRow = if (missing) null else rows.selectionRow(field.value)
    val name = selectedRow?.model?.let { it.name.ifBlank { it.id } }
    PreferenceListItem(
        leadingIcon = { iconModifier ->
            LLMModelServiceIcon(
                serviceId = selectedRow?.instance?.templateId.orEmpty(),
                modifier = iconModifier,
                contentPadding = 2.dp,
            )
        },
        title = stringResource(titleRes),
        subtitle = voiceFieldSubtitle(name, field.overridden, field.dirty),
        onClick = onClick,
        showDivider = showDivider,
    )
}

/** 链路音色配置行：当前音色名 + 继承状态；音色被替代时说明原因。 */
@Composable
private fun VoiceConfigVoiceRow(
    @StringRes titleRes: Int,
    field: VoiceDraftFieldUi<String?>,
    options: List<TtsVoice>,
    defaultVoiceId: String = "",
    onClick: () -> Unit,
) {
    val effectiveVoice = field.value ?: defaultVoiceId
    val name = effectiveVoice.takeIf { it.isNotBlank() }?.let { id ->
        options.firstOrNull { it.id == id }?.name ?: id
    } ?: stringResource(R.string.voice_settings_default_voice)
    PreferenceListItem(
        icon = Icons.Default.GraphicEq,
        title = stringResource(titleRes),
        subtitle = if (field.substituted) {
            stringResource(R.string.voice_voice_substituted, voiceFieldSubtitle(name, field.overridden, field.dirty))
        } else {
            voiceFieldSubtitle(name, field.overridden, field.dirty)
        },
        onClick = onClick,
    )
}

/** 轻量圆弧加载：放慢旋转避免配置切换时显得急促，短文案不抢占字幕区域。 */
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

/**
 * 底部语音配置微胶囊组（方案 B · 固定两行布局）：按当前语音模式动态展示「链路 + 各角色模型 + 音色」。
 *
 * - 级联（3 模型 + 音色）：
 *   第 1 行：`[级联]` + `[对话模型]` + `[音色]`
 *   第 2 行：`[识别模型 (STT)]` + `[合成模型 (TTS)]`
 * - 半级联（2 模型 + 音色）：
 *   第 1 行：`[半级联]` + `[理解模型 (Omni)]` + `[音色]`
 *   第 2 行：`[合成模型 (TTS)]`
 * - 实时（1 模型 + 音色）：
 *   第 1 行：`[实时模型]`
 *   第 2 行：`[实时]` + `[音色]`
 *
 * 每行采用单行 `Row` 居中排布，长文案胶囊按权重自适应收缩并单行省略（Ellipsis），严格占用两行、不折出第三行。
 */
@Composable
internal fun VoiceConfigMicroPills(
    uiState: RealtimeVoiceUiState,
    modifier: Modifier = Modifier,
) {
    val notConfigured = stringResource(R.string.voice_settings_not_configured)
    val defaultVoice = stringResource(R.string.voice_settings_default_voice)
    val primaryModelName = uiState.modelName.ifBlank { uiState.chatModelName }.ifBlank { notConfigured }
    val primaryTemplateId = uiState.modelTemplateId.ifBlank { uiState.chatModelTemplateId }
        .takeIf { uiState.modelName.isNotBlank() || uiState.chatModelName.isNotBlank() }
    val sttName = uiState.sttModelName.ifBlank { notConfigured }
    val sttTemplateId = uiState.sttModelTemplateId.takeIf { uiState.sttModelName.isNotBlank() }
    val ttsName = uiState.ttsModelName.ifBlank { notConfigured }
    val ttsTemplateId = uiState.ttsModelTemplateId.takeIf { uiState.ttsModelName.isNotBlank() }
    val voiceDisplay = uiState.voiceName.ifBlank { defaultVoice }
    val pipelineText = stringResource(pipelineTitleRes(uiState.pipeline))

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        when (uiState.pipeline) {
            VoicePipeline.CASCADED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoicePipelineBadgePill(text = pipelineText)
                    VoiceRoleMicroPill(
                        roleLabel = stringResource(R.string.voice_pill_role_chat),
                        valueText = primaryModelName,
                        serviceTemplateId = primaryTemplateId,
                        modifier = Modifier.weight(1f, fill = false),
                        maxPillWidth = 176.dp,
                    )
                    VoiceRoleMicroPill(
                        roleLabel = stringResource(R.string.voice_pill_role_voice),
                        valueText = voiceDisplay,
                        serviceTemplateId = null,
                        maxPillWidth = 116.dp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoiceRoleMicroPill(
                        roleLabel = stringResource(R.string.voice_pill_role_recognition),
                        valueText = sttName,
                        serviceTemplateId = sttTemplateId,
                        modifier = Modifier.weight(1f, fill = false),
                        maxPillWidth = 172.dp,
                    )
                    VoiceRoleMicroPill(
                        roleLabel = stringResource(R.string.voice_pill_role_synthesis),
                        valueText = ttsName,
                        serviceTemplateId = ttsTemplateId,
                        modifier = Modifier.weight(1f, fill = false),
                        maxPillWidth = 172.dp,
                    )
                }
            }
            VoicePipeline.SEMI_CASCADED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoicePipelineBadgePill(text = pipelineText)
                    VoiceRoleMicroPill(
                        roleLabel = stringResource(R.string.voice_pill_role_understanding),
                        valueText = primaryModelName,
                        serviceTemplateId = primaryTemplateId,
                        modifier = Modifier.weight(1f, fill = false),
                        maxPillWidth = 176.dp,
                    )
                    VoiceRoleMicroPill(
                        roleLabel = stringResource(R.string.voice_pill_role_voice),
                        valueText = voiceDisplay,
                        serviceTemplateId = null,
                        maxPillWidth = 116.dp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoiceRoleMicroPill(
                        roleLabel = stringResource(R.string.voice_pill_role_synthesis),
                        valueText = ttsName,
                        serviceTemplateId = ttsTemplateId,
                        maxPillWidth = 240.dp,
                    )
                }
            }
            VoicePipeline.NATIVE_REALTIME -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoiceRoleMicroPill(
                        roleLabel = stringResource(R.string.voice_pill_role_realtime),
                        valueText = primaryModelName,
                        serviceTemplateId = primaryTemplateId.takeIf { uiState.modelName.isNotBlank() },
                        maxPillWidth = 260.dp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoicePipelineBadgePill(text = pipelineText)
                    VoiceRoleMicroPill(
                        roleLabel = stringResource(R.string.voice_pill_role_voice),
                        valueText = voiceDisplay,
                        serviceTemplateId = null,
                        maxPillWidth = 160.dp,
                    )
                }
            }
        }
    }
}

/** 链路模式微胶囊：实心次级高对比底色，突出当前所处链路。 */
@Composable
private fun VoicePipelineBadgePill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 96.dp),
        shape = CircleShape,
        color = colors.surfaceContainerHighest,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 角色化微胶囊：可选品牌图标 + 角色前缀（次级墨灰） + 模型/音色名称（主色，超长单行省略）。
 */
@Composable
private fun VoiceRoleMicroPill(
    roleLabel: String,
    valueText: String,
    serviceTemplateId: String?,
    modifier: Modifier = Modifier,
    maxPillWidth: Dp = 172.dp,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = maxPillWidth),
        shape = CircleShape,
        color = colors.surfaceContainer,
        border = BorderStroke(0.8.dp, colors.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (serviceTemplateId != null) {
                LLMModelServiceIcon(
                    serviceId = serviceTemplateId,
                    modifier = Modifier.size(13.dp),
                    contentPadding = 0.dp,
                )
            }
            Text(
                text = roleLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = valueText,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun previewVoiceModelRow(
    instanceId: String,
    instanceName: String,
    connectionId: String,
    modelId: String,
    modelName: String,
): VoiceModelRow {
    val instance = ModelServiceInstance(
        id = instanceId,
        name = instanceName,
        // 预览用真实模板 ID，品牌图标才不会整屏回落占位图。
        templateId = instanceId.removePrefix("instance-"),
        isEnabled = true,
        isOfficialToolsEnabled = true,
        sharedApiKey = "preview-key",
        connections = listOf(
            ServiceConnection(
                id = connectionId,
                capability = ServiceCapability.Recognition,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "https://preview.example.com",
            ),
        ),
    )
    return VoiceModelRow(
        instance = instance,
        connection = instance.connections.first(),
        model = Model(id = modelId, name = modelName),
    )
}

/** 抽屉页面预览共用的一套候选模型与音色。 */
private fun previewVoiceSettings(): VoiceSettingsUiState = VoiceSettingsUiState(
    recognitionRows = listOf(previewVoiceModelRow("instance-minimax", "MiniMax", "conn-speech", "speech-02", "Speech-02")),
    synthesisRows = listOf(previewVoiceModelRow("instance-mimo", "MiMo", "conn-synthesis", "mimo-tts", "MiMo TTS")),
    understandingRows = listOf(previewVoiceModelRow("instance-qwen", "千问", "conn-asr", "qwen3-omni", "Qwen3 Omni")),
    realtimeRows = listOf(
        previewVoiceModelRow("instance-qwen", "千问", "conn-realtime", "qwen3-omni-flash-realtime", "qwen3-omni-flash-realtime"),
    ),
    synthesisVoiceOptions = listOf(TtsVoice(id = "tongtong", name = "童童", language = "zh", gender = "Female")),
    realtimeVoiceOptions = listOf(TtsVoice(id = "Cherry", name = "Cherry", language = "zh", gender = "Female")),
    realtimeDefaultVoice = "Cherry",
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
            uiState = RealtimeVoiceUiState(settings = previewVoiceSettings()),
            onOpenQuickVoice = {},
            onOpenSettings = {},
        )
    }
}

@Preview(name = "抽屉 · 语音设置（级联） · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · 语音设置（级联） · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSettingsCascadedPreview() {
    VoiceSheetPagePreview(title = "语音设置", showBack = true) {
        VoiceSettingsPage(
            uiState = RealtimeVoiceUiState(
                settings = previewVoiceSettings(),
                cascadedAvailable = true,
            ),
            onOpenModelPicker = {},
            onOpenVoicePicker = {},
            onSelectPipeline = {},
            onResetVoiceSettings = {},
        )
    }
}

@Preview(name = "抽屉 · 语音设置（实时） · 浅色", widthDp = 390, locale = "zh")
@Preview(name = "抽屉 · 语音设置（实时） · 深色", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSettingsRealtimePreview() {
    VoiceSheetPagePreview(title = "语音设置", showBack = true) {
        VoiceSettingsPage(
            uiState = RealtimeVoiceUiState(
                settings = previewVoiceSettings(),
                cascadedAvailable = true,
                draft = VoiceDraftUiState(
                    pipeline = VoiceDraftFieldUi(VoicePipeline.NATIVE_REALTIME, overridden = true, dirty = true),
                    missing = setOf(VoiceConfigField.REALTIME_MODEL),
                ),
            ),
            onOpenModelPicker = {},
            onOpenVoicePicker = {},
            onSelectPipeline = {},
            onResetVoiceSettings = {},
        )
    }
}

@Preview(name = "语音页 · 待启动 · 浅色", widthDp = 390, heightDp = 844, locale = "zh")
@Preview(name = "语音页 · 待启动 · 深色", widthDp = 390, heightDp = 844, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RealtimeVoiceScreenReadyPreview() {
    GimiTheme {
        RealtimeVoiceScreen(
            uiState = RealtimeVoiceUiState(
                settings = previewVoiceSettings(),
                cascadedAvailable = true,
            ),
            started = false,
            onStartCall = {},
            onToggleMute = {},
            onEnd = {},
        )
    }
}

@Preview(name = "语音页 · 通话中文字与文件输入 · 浅色", widthDp = 390, heightDp = 844, locale = "zh")
@Preview(name = "语音页 · 通话中文字与文件输入 · 深色", widthDp = 390, heightDp = 844, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RealtimeVoiceScreenEditingPreview() {
    GimiTheme {
        RealtimeVoiceScreen(
            uiState = RealtimeVoiceUiState(
                active = true,
                settingsLocked = true,
                settings = previewVoiceSettings(),
                pipeline = VoicePipeline.NATIVE_REALTIME,
                cascadedAvailable = true,
                realtimeAvailable = true,
                composer = VoiceComposerUiState(editing = true),
            ),
            started = true,
            onStartCall = {},
            onToggleMute = {},
            onEnd = {},
            composerContent = {
                VoiceComposerInput(
                    params = github.ponyhuang.gimi.feature.chat.ComposerInputContentParams(
                        messageData = github.ponyhuang.gimi.feature.chat.MessageData(text = "帮我分析这份文档"),
                        isGenerating = false,
                        voiceInputState = github.ponyhuang.gimi.feature.chat.VoiceInputUiState.Idle,
                        isVoiceInputAvailable = true,
                        voiceErrorMessage = null,
                        onVoiceErrorShown = {},
                        onTextChange = {},
                        onRemoveAttachment = {},
                        onSendClick = {},
                        onStopClick = {},
                        onVoiceInputStart = {},
                    ),
                    sendEnabled = true,
                    onClose = {},
                )
            },
        )
    }
}
