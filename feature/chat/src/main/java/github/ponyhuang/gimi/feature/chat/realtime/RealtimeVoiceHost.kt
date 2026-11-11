package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection

/** 两层页面共用时长和曲线，避免退场时聊天页与语音页错位或弹跳。 */
private const val VoiceNavigationDurationMillis = 320

/**
 * 电话入口控制独立语音页；进入动画完成后才允许权限请求和收音。
 * 语音页由用户入口打开、由回聊天关闭；挂断只结束会话，
 * 页面留在待启动状态，等待再次点「开始通话」。
 *
 * @param openVoiceRequest          外部打开语音页的请求计数（如抽屉"语音通话"入口）；
 *                                  值增长时会打开语音页，随后通过 [onOpenVoiceRequestConsumed] 清零。
 * @param onOpenVoiceRequestConsumed 外部打开请求已被消费的回调，宿主据此清零计数。
 */
@Composable
fun RealtimeVoiceHost(
    conversationId: String,
    selection: ModelSelection?,
    conversationTitle: String,
    openVoiceRequest: Int = 0,
    onOpenVoiceRequestConsumed: () -> Unit = {},
    /** 语音页目标可见性回调；宿主据此关闭抽屉手势等被全屏覆盖层影响的交互。 */
    onVoiceVisibleChange: (Boolean) -> Unit = {},
    textContent: @Composable (onOpenVoice: () -> Unit) -> Unit,
) {
    val viewModel: RealtimeVoiceViewModel = hiltViewModel()
    val callState by viewModel.callState.collectAsStateWithLifecycle()
    // 进程仍在通话时（Activity 重建）直接落在语音页；否则等用户从聊天页点电话入口。
    val visibility = remember { MutableTransitionState(callState.active) }
    val transition = rememberTransition(visibility, label = "Voice navigation")
    val progress by transition.animateFloat(
        transitionSpec = { tween(VoiceNavigationDurationMillis, easing = FastOutSlowInEasing) },
        label = "Chat slide",
    ) { if (it) 1f else 0f }
    // 把目标可见性上报给宿主（含进程还在通话、Activity 重建的初始状态），
    // 供宿主在语音页全屏期间关闭抽屉手势等交互。
    LaunchedEffect(visibility.targetState) { onVoiceVisibleChange(visibility.targetState) }
    // 状态栏通知请求返回通话：请求驻留在控制器状态里，后台期间也不会丢失。
    LaunchedEffect(callState.returnToCallRequested) {
        if (callState.returnToCallRequested) {
            visibility.targetState = true
            viewModel.consumeReturnToCall()
        }
    }
    // 外部入口（抽屉"语音通话"）请求打开语音页；消费后清零计数，避免重组重复触发。
    // 语音页已不再提供设置入口，无需清算返回设置的目标状态。
    LaunchedEffect(openVoiceRequest) {
        if (openVoiceRequest > 0) {
            visibility.targetState = true
            onOpenVoiceRequestConsumed()
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // 保持文字页面组合，往返语音页不丢失草稿和滚动位置。
        Box(Modifier.fillMaxSize().graphicsLayer { translationX = -size.width * progress }) {
            textContent({ if (visibility.isIdle) visibility.targetState = true })
        }
        transition.AnimatedVisibility(
            visible = { it },
            enter = slideInHorizontally(
                animationSpec = tween(VoiceNavigationDurationMillis, easing = FastOutSlowInEasing),
                initialOffsetX = { it },
            ),
            exit = slideOutHorizontally(
                animationSpec = tween(VoiceNavigationDurationMillis, easing = FastOutSlowInEasing),
                targetOffsetX = { it },
            ),
        ) {
            RealtimeVoiceRoute(
                conversationId = conversationId, selection = selection, conversationTitle = conversationTitle,
                active = visibility.isIdle && visibility.currentState && visibility.targetState,
                onDismiss = { visibility.targetState = false },
            )
        }
    }
}
