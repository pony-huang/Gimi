package github.ponyhuang.gimi.data.voicewake

/** 同一音频路由重新开始监听时应执行的资源操作。 */
internal enum class WakeListeningRestartAction {
    ResumeCapture,
    RecreateSession,
}

/**
 * 将录音采集与离线唤醒会话的重建决策集中起来。
 * 已经持有相同路由与检测器时，只需重新打开录音；重建检测器会造成一次无意义的唤醒停启。
 */
internal object WakeListeningRestartPolicy {
    fun decide(
        currentRouteId: String?,
        availableRouteId: String,
        hasActiveDetector: Boolean,
    ): WakeListeningRestartAction = if (
        currentRouteId == availableRouteId && hasActiveDetector
    ) {
        WakeListeningRestartAction.ResumeCapture
    } else {
        WakeListeningRestartAction.RecreateSession
    }
}
