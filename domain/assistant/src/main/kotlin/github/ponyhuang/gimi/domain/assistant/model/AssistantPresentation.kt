package github.ponyhuang.gimi.domain.assistant.model

/** 语音交互界面可能使用的承载方式。 */
enum class AssistantSurfaceTarget {
    NONE,
    CHAT_COMPOSER,
    IN_APP_SHEET,
    SYSTEM_OVERLAY,
    LOCK_SCREEN_ACTIVITY,
    NOTIFICATION_ONLY,
}

/**
 * 助手界面路由所需的运行环境快照。
 *
 * @property presentationVisible 当前一轮是否需要展示。
 * @property appForeground 主应用是否位于前台。
 * @property chatVisible 当前前台页面是否为聊天页。
 * @property deviceLocked 设备是否处于锁屏状态。
 * @property overlayPermissionGranted 是否允许显示系统悬浮窗。
 * @property lockScreenLaunchAllowed 当前是否允许从后台打开锁屏 Activity。
 */
data class AssistantSurfaceEnvironment(
    val presentationVisible: Boolean,
    val appForeground: Boolean,
    val chatVisible: Boolean,
    val deviceLocked: Boolean,
    val overlayPermissionGranted: Boolean,
    val lockScreenLaunchAllowed: Boolean,
)

/** 按锁屏、应用前台、后台的优先级选择唯一助手界面。 */
fun routeAssistantSurface(environment: AssistantSurfaceEnvironment): AssistantSurfaceTarget {
    if (!environment.presentationVisible) return AssistantSurfaceTarget.NONE
    if (environment.deviceLocked) {
        return if (environment.lockScreenLaunchAllowed) {
            AssistantSurfaceTarget.LOCK_SCREEN_ACTIVITY
        } else {
            AssistantSurfaceTarget.NOTIFICATION_ONLY
        }
    }
    if (environment.appForeground) {
        return if (environment.chatVisible) {
            AssistantSurfaceTarget.CHAT_COMPOSER
        } else {
            AssistantSurfaceTarget.IN_APP_SHEET
        }
    }
    return if (environment.overlayPermissionGranted) {
        AssistantSurfaceTarget.SYSTEM_OVERLAY
    } else {
        AssistantSurfaceTarget.NOTIFICATION_ONLY
    }
}

/** 语音链路投射到共享助手界面的事件。 */
sealed interface AssistantPresentationEvent {
    /** 检测到唤醒词并开始采集一条新指令。 */
    data class CaptureStarted(val source: AssistantInvocationSource) : AssistantPresentationEvent

    /** 正在将录音转写为文本。 */
    data object Transcribing : AssistantPresentationEvent

    /** 已获得本轮用户指令。 */
    data class TranscriptReady(val text: String) : AssistantPresentationEvent

    /** 正在播报回答。 */
    data object Speaking : AssistantPresentationEvent

    /** 本轮语音交互完成，等待界面自动收起。 */
    data object Completed : AssistantPresentationEvent

    /** 用户主动停止本轮任务。 */
    data object Stopped : AssistantPresentationEvent

    /** 语音链路失败。 */
    data class Failed(val message: String) : AssistantPresentationEvent
}

/** 将语音链路事件归并到单一助手会话状态。 */
fun AssistantSessionState.applyPresentationEvent(
    event: AssistantPresentationEvent,
): AssistantSessionState = when (event) {
    is AssistantPresentationEvent.CaptureStarted -> copy(
        phase = AssistantSessionPhase.LISTENING,
        source = event.source,
        turn = null,
        pendingConfirmation = null,
        errorMessage = null,
        configIssue = null,
        presentationVisible = true,
    )
    AssistantPresentationEvent.Transcribing -> copy(
        phase = AssistantSessionPhase.TRANSCRIBING,
    )
    is AssistantPresentationEvent.TranscriptReady -> copy(
        phase = AssistantSessionPhase.PREPARING,
        turn = (turn ?: AssistantTurn()).copy(userText = event.text),
    )
    AssistantPresentationEvent.Speaking -> copy(
        phase = AssistantSessionPhase.SPEAKING,
    )
    AssistantPresentationEvent.Completed -> copy(
        phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
        taskActive = false,
        pendingConfirmation = null,
    )
    AssistantPresentationEvent.Stopped -> copy(
        phase = AssistantSessionPhase.STOPPED,
        taskActive = false,
        pendingConfirmation = null,
    )
    is AssistantPresentationEvent.Failed -> copy(
        phase = AssistantSessionPhase.ERROR,
        errorMessage = event.message,
        taskActive = false,
        pendingConfirmation = null,
    )
}
