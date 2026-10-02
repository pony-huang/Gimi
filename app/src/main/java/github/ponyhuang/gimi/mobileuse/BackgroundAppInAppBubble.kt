package github.ponyhuang.gimi.mobileuse

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppBubble
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clipToBounds

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
        var x by remember(session?.id) { mutableFloatStateOf(maxX) }
        var y by remember(session?.id) { mutableFloatStateOf(maxY / 2) }
        var hideGeneration by remember(session?.id) { mutableIntStateOf(0) }
        var hidden by remember(session?.id) { androidx.compose.runtime.mutableStateOf(false) }
        var dragging by remember(session?.id) { androidx.compose.runtime.mutableStateOf(false) }
        LaunchedEffect(hideGeneration, maxX, maxY, dragging) {
            if (dragging) return@LaunchedEffect
            delay(2000)
            x = if (x < maxX / 2) 0f else maxX
            hidden = true
        }
        val half = with(density) { 28.dp.toPx() }
        BackgroundAppBubble(icon = { BackgroundAppIcon() }, onOpen = host::open, modifier = Modifier
            .offset { IntOffset((x.coerceIn(0f, maxX) + if (hidden) { if (x < maxX / 2) -half else half } else 0f).roundToInt(), y.coerceIn(0f, maxY).roundToInt()) }
            .pointerInput(maxX, maxY) {
                detectDragGestures(onDragStart = { dragging = true; hidden = false; hideGeneration++ }, onDragCancel = { dragging = false; hideGeneration++ }, onDragEnd = { dragging = false; x = if (x < maxX / 2) 0f else maxX; hideGeneration++ }) { change, delta ->
                    change.consume(); x = (x + delta.x).coerceIn(0f, maxX); y = (y + delta.y).coerceIn(0f, maxY)
                }
            })
    }
}
