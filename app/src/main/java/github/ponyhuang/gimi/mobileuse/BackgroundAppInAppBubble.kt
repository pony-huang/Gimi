package github.ponyhuang.gimi.mobileuse

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppBubble
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppBubbleBounds
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppBubbleMotionState
import kotlin.math.roundToInt

/** 无悬浮权限或服务启动受限时，Gimi 内仍有可拖动的气泡入口。 */
@Composable
internal fun BackgroundAppInAppBubble(repository: MobileUseRepository, host: BackgroundAppWindowHost) {
    val session by repository.displaySession.collectAsStateWithLifecycle()
    val overlay by host.overlayRunning.collectAsStateWithLifecycle()
    val presentation by host.presentation.collectAsStateWithLifecycle()
    if (session == null || overlay || presentation == BackgroundAppPresentation.FULLSCREEN) return
    BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding().clipToBounds()) {
        val density = LocalDensity.current
        val maxX = with(density) { (maxWidth - 56.dp).toPx().coerceAtLeast(0f) }
        val maxY = with(density) { (maxHeight - 56.dp).toPx().coerceAtLeast(0f) }
        val scope = rememberCoroutineScope()
        val motion = remember(session?.id) { BackgroundAppBubbleMotionState(scope, Offset(maxX, maxY / 2)) }
        val half = with(density) { 28.dp.toPx() }
        val bounds = BackgroundAppBubbleBounds(0f, maxX, 0f, maxY, half)
        LaunchedEffect(motion, bounds) { motion.updateBounds(bounds) }
        DisposableEffect(motion) { onDispose { motion.stop() } }
        BackgroundAppBubble(icon = { BackgroundAppIcon() }, onOpen = host::open, modifier = Modifier
            .offset { IntOffset(motion.position.x.roundToInt(), motion.position.y.roundToInt()) }
            .pointerInput(motion, maxX, maxY) {
                detectDragGestures(
                    onDragStart = { motion.beginDrag() },
                    onDragCancel = motion::endDrag,
                    onDragEnd = motion::endDrag,
                ) { change, delta ->
                    change.consume()
                    motion.dragBy(delta)
                }
            })
    }
}
