package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection

/**
 * 聊天页与实时语音页的常驻过渡宿主：
 * 1. 两层均保持组合常驻，消除点击电话图标首帧冷组合与粒子场初始化造成的掉帧；
 * 2. 采用无弹跳高响应弹簧驱动 `progress`，支持中途随时打断与反向切换；
 * 3. 非过渡期间零额外离屏图层开销，隐藏层不参与放置、绘制与手势分发。
 */
@Composable
fun RealtimeVoiceHost(
    conversationId: String,
    selection: ModelSelection?,
    conversationTitle: String,
    roleChips: (@Composable () -> Unit)? = null,
    /** 语音页目标可见性回调；宿主据此关闭抽屉手势等被全屏覆盖层影响的交互。 */
    onVoiceVisibleChange: (Boolean) -> Unit = {},
    drawerOpen: Boolean = false,
    textContent: @Composable (onOpenVoice: () -> Unit) -> Unit,
) {
    val viewModel: RealtimeVoiceViewModel = hiltViewModel()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    // 提前在宿主组合阶段预热 2400 粒子静态球面几何，保证首次打开语音页零计算阻塞。
    LaunchedEffect(Unit) { prewarmVoiceParticleGeometry() }

    // 进程仍在通话时（Activity 重建）直接落在语音页；否则等用户从聊天页点电话入口。
    var voiceTargetVisible by rememberSaveable { mutableStateOf(callState.active) }
    val progress by animateFloatAsState(
        targetValue = if (voiceTargetVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 420f,
            visibilityThreshold = 0.001f,
        ),
        label = "VoiceNavigationProgress",
    )
    val isTransitioning = progress > 0.001f && progress < 0.999f
    val showTextLayer = !voiceTargetVisible || progress < 0.999f
    val showVoiceLayer = voiceTargetVisible || progress > 0.001f

    // 把目标可见性上报给宿主（含进程还在通话、Activity 重建的初始状态），
    // 供宿主在语音页全屏期间关闭抽屉手势等交互。
    LaunchedEffect(voiceTargetVisible) { onVoiceVisibleChange(voiceTargetVisible) }
    // 状态栏通知请求返回通话：请求驻留在控制器状态里，后台期间也不会丢失。
    LaunchedEffect(callState.returnToCallRequested) {
        if (callState.returnToCallRequested) {
            voiceTargetVisible = true
            viewModel.consumeReturnToCall()
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        // 文字聊天层：常驻保留草稿与滚动位置；仅在过渡期间启用景深缩放与淡出图层，平时零离屏开销。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .inactiveLayerPlacement(visible = showTextLayer)
                .then(
                    if (isTransitioning) {
                        Modifier.graphicsLayer {
                            val fade = (1f - progress * 1.15f).coerceIn(0f, 1f)
                            alpha = fade
                            val scale = 1f - 0.045f * progress
                            scaleX = scale
                            scaleY = scale
                            translationY = -14.dp.toPx() * progress
                        }
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (voiceTargetVisible) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            textContent({ voiceTargetVisible = true })
        }

        // 实时语音层：保持组合常驻以避免点击瞬间冷组合卡顿；完全隐藏时不放置也不运行粒子帧循环。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .inactiveLayerPlacement(visible = showVoiceLayer)
                .then(
                    if (!voiceTargetVisible && showVoiceLayer) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            RealtimeVoiceRoute(
                conversationId = conversationId,
                selection = selection,
                conversationTitle = conversationTitle,
                roleChips = roleChips,
                active = voiceTargetVisible,
                isVisible = showVoiceLayer,
                transitionProgress = progress,
                onDismiss = { voiceTargetVisible = false },
                drawerOpen = drawerOpen,
            )
        }
    }
}

/**
 * 隐藏常驻层时跳过 placeable 放置与绘制，既保留子树组合槽位与预热状态，又消除不可见层的绘制与触控命中。
 */
private fun Modifier.inactiveLayerPlacement(visible: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (visible) {
            placeable.placeRelative(0, 0)
        }
    }
}
